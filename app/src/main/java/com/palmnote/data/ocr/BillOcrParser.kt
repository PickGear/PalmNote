package com.palmnote.data.ocr

import android.graphics.RectF
import com.palmnote.data.export.BillCsvImporter
import com.palmnote.data.export.ImportFailure
import com.palmnote.data.export.ImportFailureReason
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.Money
import com.palmnote.domain.util.CategoryClassifier
import java.util.regex.Pattern

/**
 * 单个字段的置信度分级，用于界面上的小圆点：
 * - [HIGH] 绿点：直接、可靠地读到（如带标签的实付行 / ¥ 金额 / 完整日期 / 商户标签）。
 * - [INFERRED] 琥珀点：由规则推断而来，并非直接读到（如盲取最大金额、启发式猜商户、补当前年）。
 * - [MISSING] 红点：必填却为空，需用户补齐。
 */
enum class FieldConfidence { HIGH, INFERRED, MISSING }

data class OcrBillResult(
    val amount: Long? = null, // 金额（分）
    val merchant: String = "",
    val date: Long? = null,
    val note: String = "",
    val category: String = "其他",
    /** 按笔识别的收/支类型；null = 无法判断，由调用方回退到用户手选的默认类型 */
    val type: BillType? = null,
    /** 金额字段置信度（见 [FieldConfidence]） */
    val amountConfidence: FieldConfidence = FieldConfidence.MISSING,
    /** 商户字段置信度 */
    val merchantConfidence: FieldConfidence = FieldConfidence.MISSING,
    /** 日期字段置信度 */
    val dateConfidence: FieldConfidence = FieldConfidence.MISSING,
    /** 分类字段置信度；分类始终由规则推断，故只会是 INFERRED / MISSING */
    val categoryConfidence: FieldConfidence = FieldConfidence.MISSING,
    /**
     * 该笔账单所有识别行文本框的并集（外接矩形）。坐标位于【传入 [OcrEngine.recognizeDetailed]
     * 的那张 bitmap】的像素坐标系——注意调用方 ViewModel 传入的是 inSampleSize 降采样 且
     * 经 EXIF 旋转后的位图，因此若要用原始文件 URI 做 UI 渲染，必须先按同一套降采样比与旋转共同换算，
     * **不可直接套用本矩形**。任一行缺少 box（如字符串适配路径 / 引擎无坐标能力）时为 null，
     * 调用方自行决定降级方式（批13 文件页的选择是不渲染任何占位块）。
     */
    val cropBox: RectF? = null
)

class BillOcrParser {

    /**
     * 纯文本入口（保留原签名）。内部把文本拆成无置信度的 [OcrLine] 后走 [parseLines]，
     * 因此行为与历史版本完全一致；不会因低置信度而降级（来源置信度为 null）。
     */
    fun parse(text: String): OcrBillResult {
        val lines = text.toOcrLines()
        if (lines.isEmpty()) return OcrBillResult()
        return parseLines(lines)
    }

    /**
     * 多笔拆分识别。接受的记录集合与旧版一致；额外把识别不到金额而被丢弃的块
     * （原始文本 + 原因）收集到 [fails]，供结果页统计与失败导出。
     */
    fun parseMultiple(text: String, fails: MutableList<ImportFailure>? = null): List<OcrBillResult> =
        parseMultipleLines(text.toOcrLines(), fails)

    /** 带逐行置信度的多笔识别入口；[fails] 语义同 [parseMultiple]。 */
    fun parseMultiple(lines: List<OcrLine>, fails: MutableList<ImportFailure>? = null): List<OcrBillResult> =
        parseMultipleLines(lines, fails)

    /** 文本 → 无置信度行列表（[parse] / [parseMultiple] 的字符串适配路径） */
    private fun String.toOcrLines(): List<OcrLine> =
        lines().map { it.trim() }.filter { it.isNotBlank() }.map { OcrLine(it, confidence = null) }

    // ============================ 单笔解析 ============================

    private fun parseLines(lines: List<OcrLine>): OcrBillResult {
        val amount = findAmount(lines)
        val merchant = findMerchant(lines)
        val merchantName = merchant?.name ?: ""
        val date = findDate(lines)
        val note = findNote(lines.map { it.text }, merchantName)
        val category = guessCategory(lines, merchantName, note)
        return OcrBillResult(
            amount = amount?.cents,
            merchant = merchantName,
            date = date?.millis,
            note = note,
            category = category,
            type = detectType(lines),
            amountConfidence = applyThreshold(amount?.confidence, amount?.sourceConfidence),
            merchantConfidence = applyThreshold(merchant?.confidence, merchant?.sourceConfidence),
            dateConfidence = applyThreshold(date?.confidence, date?.sourceConfidence),
            categoryConfidence = categoryConfidenceOf(category),
            cropBox = unionBoxOf(lines)
        )
    }

    // ============================ 多笔解析 ============================

    private fun parseMultipleLines(rawLines: List<OcrLine>, fails: MutableList<ImportFailure>?): List<OcrBillResult> {
        if (rawLines.isEmpty()) return emptyList()
        // 页面级汇总行（微信"支出¥156.45 收入¥2.40"、支付宝记账本"9月总支出…"）不是交易
        val lines = rawLines.filterNot { isSummaryLine(it.text) }

        val blocks = splitIntoBlocks(lines)
        if (blocks.size <= 1) return listOf(parseLines(rawLines))
        return blocks.mapNotNull { (block, gDate) -> blockToResult(block, gDate, fails) }
    }

    private fun blockToResult(block: List<OcrLine>, groupDate: Long?, fails: MutableList<ImportFailure>?): OcrBillResult? {
        val amount = findAmount(block)
        val cents = amount?.cents
        if (cents == null || cents <= 0) {
            fails?.add(ImportFailure(block.joinToString(" ") { it.text }, ImportFailureReason.MISSING_AMOUNT))
            return null
        }
        val merchant = findMerchant(block)
        val merchantName = merchant?.name ?: ""
        val date = findDate(block)
        val note = findNote(block.map { it.text }, merchantName)
        val category = guessCategory(block, merchantName, note)
        return OcrBillResult(
            amount = cents,
            merchant = merchantName,
            date = date?.millis ?: groupDate,
            note = note,
            category = category,
            type = detectType(block),
            amountConfidence = applyThreshold(amount.confidence, amount.sourceConfidence),
            merchantConfidence = applyThreshold(merchant?.confidence, merchant?.sourceConfidence),
            dateConfidence = dateConfidenceOf(date, groupDate),
            categoryConfidence = categoryConfidenceOf(category),
            cropBox = unionBoxOf(block)
        )
    }

    /**
     * 计算一组识别行文本框的并集（外接矩形），供结果页裁剪小票缩略图。
     *
     * 坐标与本文件的 [OcrLine.box] 完全一致（传入 [OcrEngine.recognizeDetailed] 的 bitmap 像素坐标），
     * 故调用方须以【同一张传入 recognizeDetailed 的 bitmap】按此矩形裁剪；对原始文件 URI 渲染前
     * 必须按降采样比与 EXIF 旋转换算（不可直接套用）。**任一行缺少 box 时返回 null**
     * （宁可整体降级，也不用残缺坐标裁出错误区域），绝不抛异常；调用方自行决定降级方式。
     */
    private fun unionBoxOf(lines: List<OcrLine>): RectF? {
        val boxes = lines.mapNotNull { it.box }
        if (boxes.isEmpty() || boxes.size != lines.size) return null
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (box in boxes) {
            for (p in box) {
                if (p.x < left) left = p.x
                if (p.y < top) top = p.y
                if (p.x > right) right = p.x
                if (p.y > bottom) bottom = p.y
            }
        }
        if (left > right || top > bottom) return null
        return RectF(left, top, right, bottom)
    }

    /**
     * 按页面结构把识别行切成交易块，块关联所属日期分组的日期（支付宝记账本按日分组，
     * 组内条目行常不带日期）。三种块边界：
     * 1. 电商订单卡片以"实付款"行收尾（抖音/拼多多/淘宝订单页实测）
     * 2. 微信/支付宝账单列表页金额无¥前缀独立成行，金额行即一条交易的结尾
     * 3. isNewTransaction 的日期/分隔线边界
     */
    private fun splitIntoBlocks(lines: List<OcrLine>): List<Pair<MutableList<OcrLine>, Long?>> {
        val blocks = mutableListOf<Pair<MutableList<OcrLine>, Long?>>()
        var current = mutableListOf<OcrLine>()
        var groupDate: Long? = null
        fun flush() {
            if (current.isNotEmpty()) blocks.add(current to groupDate)
            current = mutableListOf()
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val text = line.text
            val headerDate = dayHeaderDate(text)
            when {
                // 电商订单卡片以"实付款"行收尾
                text.contains("实付") && current.any { AMOUNT_PATTERN.matcher(it.text).find() } -> {
                    current.add(line)
                    i += consumeAmountFollowup(lines, i, current)
                    flush()
                }
                // 账单列表页裸金额行即一条交易的结尾
                BARE_AMOUNT.matcher(text).matches() -> { current.add(line); flush() }
                // 日期分组头（"9月9日 星期三 支0.00 收0.14"）：记录组日期供条目回退
                headerDate != null -> { groupDate = headerDate; current.add(OcrLine(stripDaySummary(text), line.confidence, line.box)) }
                else -> {
                    if (isNewTransaction(text, current.map { it.text })) flush()
                    current.add(line)
                }
            }
            i++
        }
        flush()
        return blocks
    }

    /** 该行是否完全不含金额（¥ 前缀金额与裸两位小数两种写法都算） */
    private fun hasNoAmount(line: String): Boolean =
        amountsIn(line).isEmpty() && looseAmountsIn(line).isEmpty()

    /**
     * "实付款"行的金额常因排版/OCR 落到下一行（"实付款" ‖ "共减¥629.85 合计¥3569.15"）：
     * 本行没有金额时把下一行的金额一并收进本笔，返回额外消费的行数；否则一笔订单会被切成两笔。
     */
    private fun consumeAmountFollowup(lines: List<OcrLine>, i: Int, current: MutableList<OcrLine>): Int {
        if (!hasNoAmount(lines[i].text)) return 0
        val next = lines.getOrNull(i + 1) ?: return 0
        if (hasNoAmount(next.text)) return 0
        current.add(next)
        return 1
    }

    /**
     * 按笔识别收/支类型（收支混排截图不能共用同一类型——issue#1 修复的延伸）：
     * 金额符号前缀最可靠，其次关键词；都没有时返回 null 交由用户默认值。
     */
    private fun detectType(lines: List<OcrLine>): BillType? =
        detectSignType(lines) ?: detectKeywordType(lines)

    /** 符号前缀：+¥/-¥ 最可靠，其次账单列表页的行首符号裸金额（+0.23/-32.22） */
    private fun detectSignType(lines: List<OcrLine>): BillType? {
        val text = lines.joinToString(" ") { it.text }
        return when {
            Regex("[+＋]\\s*[¥￥]").containsMatchIn(text) -> BillType.INCOME
            Regex("[-−－]\\s*[¥￥]").containsMatchIn(text) -> BillType.EXPENSE
            lines.any { BARE_SIGNED_INCOME.matcher(it.text).find() } -> BillType.INCOME
            lines.any { BARE_SIGNED_EXPENSE.matcher(it.text).find() } -> BillType.EXPENSE
            else -> null
        }
    }

    /** 关键词兜底：电商订单页常含"申请退款/红包抵扣"等按钮文字，不能用宽泛的"退款/红包"判收入 */
    private fun detectKeywordType(lines: List<OcrLine>): BillType? {
        val text = lines.joinToString(" ") { it.text }
        val incomeWords = listOf("收入", "退款成功", "已退款", "退款到账", "收款", "转入", "返现", "报销")
        val expenseWords = listOf("支出", "付款", "消费", "转出", "扣款", "支付成功", "实付款")
        return when {
            incomeWords.any { text.contains(it) } -> BillType.INCOME
            expenseWords.any { text.contains(it) } -> BillType.EXPENSE
            else -> null
        }
    }

    private fun isNewTransaction(line: String, prevLines: List<String>): Boolean {
        if (prevLines.isEmpty()) return false

        val hasDateHere = hasValidDate(line)
        val hasAmountHere = AMOUNT_PATTERN.matcher(line).find()

        val prevHasAmount = prevLines.any { AMOUNT_PATTERN.matcher(it).find() }

        if (hasDateHere && prevHasAmount) return true

        if (hasAmountHere && prevLines.any { hasValidDate(it) }) {
            if (!prevLines.any { AMOUNT_PATTERN.matcher(it).find() }) return true
        }

        if (line.startsWith("-¥") || line.startsWith("-￥") || line.startsWith("+¥") || line.startsWith("+￥")) {
            if (prevHasAmount) return true
        }

        val separators = listOf("---", "═══", "————", "-----", "————————")
        if (separators.any { line.contains(it) }) return true

        return false
    }

    /**
     * 行内是否存在**可用**日期：正则命中还不够，月/日必须真的能解析出日期。
     * 商品型号常形如"适用65-7..."、"S75-2025"，宽松的裸日期正则会命中它们，
     * 于是把同一笔订单从中间切成两笔。
     */
    private fun hasValidDate(line: String): Boolean =
        DATE_PATTERNS.any { pat ->
            val m = pat.matcher(line)
            m.find() && parseDateGroup(m.group(1)) != null
        }

    // ============================ 金额 ============================

    private fun findAmount(lines: List<OcrLine>): AmountPick? =
        findPaidAmount(lines) ?: largestAmount(lines)

    /**
     * 优先取“实付款/实收”等实付金额行——电商订单页常含商品原价/优惠/推荐商品价格，
     * 盲取最大值会取错（如拼多多详情页会把 ¥6.25 原价当成实付 ¥4.16）。
     * 该分支取自带标签的行且金额带 ¥ 前缀，判为 [FieldConfidence.HIGH]。
     */
    private fun findPaidAmount(lines: List<OcrLine>): AmountPick? {
        val paidLabel = Regex("实付|实收|付款金额|支付金额|本次支付")
        for (i in lines.indices) {
            val line = lines[i].text
            val labelMatch = paidLabel.find(line) ?: continue
            val afterLabel = line.substring(labelMatch.range.last + 1)
            val afterAmts = amountsIn(afterLabel)
            val nextAmts = lines.getOrNull(i + 1)?.let { amountsIn(it.text) } ?: emptyList()
            // “共减/红包”行上的是优惠金额（如淘宝“实付款 共减¥3”），真实付款额常在下一行
            val useNext = Regex("共减|红包|立减").containsMatchIn(afterLabel) && nextAmts.isNotEmpty()
            val picked = when {
                useNext -> nextAmts
                afterAmts.isNotEmpty() -> afterAmts
                else -> nextAmts
            }
            // 取最大而非第一个：同一行常同时出现优惠与实付（"共减¥629.85 合计¥3569.15"），
            // 优惠额必然小于实付额，取第一个会把"省下的钱"记成消费额
            picked.maxOrNull()?.takeIf { it > 0 }?.let {
                val source = if (useNext) lines.getOrNull(i + 1) else lines[i]
                return AmountPick(Money.fromYuan(it).cents, FieldConfidence.HIGH, source?.confidence)
            }
        }
        return null
    }

    /** 兜底：无实付行时取最大金额（微信/支付宝账单等原有场景）。¥ 金额与裸金额合并取最大——
     *  账单列表行常带"已退款(¥0.23)"副行，若 ¥ 优先会取到退款额而非实付额。
     *  命中 ¥ 金额判 HIGH，命中裸金额（盲取最大）判 INFERRED。 */
    private fun largestAmount(lines: List<OcrLine>): AmountPick? {
        val yuan = lines.flatMap { amountsIn(it.text) }.filter { it > 0 }
        val loose = lines.flatMap { looseAmountsIn(it.text) }.filter { it > 0 }
        val best = (yuan + loose).maxOrNull() ?: return null
        val confidence = if (yuan.contains(best)) FieldConfidence.HIGH else FieldConfidence.INFERRED
        val source = lines.firstOrNull { amountsIn(it.text).contains(best) || looseAmountsIn(it.text).contains(best) }
        return AmountPick(Money.fromYuan(best).cents, confidence, source?.confidence)
    }

    /** 提取一段文本中所有 ¥ 前缀金额（元） */
    private fun amountsIn(text: String): List<Double> {
        val out = mutableListOf<Double>()
        val m = AMOUNT_PATTERN.matcher(text)
        while (m.find()) m.group(1)?.toDoubleOrNull()?.let { out.add(it) }
        return out
    }

    /** 提取裸数字金额（无 ¥ 前缀，两位小数） */
    private fun looseAmountsIn(text: String): List<Double> {
        val out = mutableListOf<Double>()
        val m = LOOSE_AMOUNT.matcher(text)
        while (m.find()) m.group(1)?.toDoubleOrNull()?.let { out.add(it) }
        return out
    }

    // ============================ 商户 ============================

    /**
     * 商户：带"商户/收款方/商家…"标签的行判 [FieldConfidence.HIGH]；
     * 仅靠金额邻近行的启发式候选或全文首个干净行判 [FieldConfidence.INFERRED]；找不到返回 null（MISSING）。
     */
    private fun findMerchant(lines: List<OcrLine>): MerchantPick? {
        val merchantKeywords = listOf(
            "商户(?!单号|号)", "商家", "收款方", "收款单位", "付款方", "对方", "门店", "店铺", "公司",
            "付款给", "向.*付款"
        )
        for (line in lines) {
            for (kw in merchantKeywords) {
                val regex = Regex("$kw[：:]*\\s*(.+)")
                val match = regex.find(line.text)
                if (match != null) {
                    return MerchantPick(
                        match.groupValues[1].trim().removeSurrounding("\"").take(50),
                        FieldConfidence.HIGH,
                        line.confidence
                    )
                }
            }
        }

        // 支付截图布局中商户名通常紧邻金额：优先在金额行的上下邻近行找候选，
        // 而不是取识别文本最前面的行（那常是"微信支付"/"账单详情"等页面装饰——issue#1）
        val amountIdx = lines.indexOfFirst { AMOUNT_PATTERN.matcher(it.text).find() || LOOSE_AMOUNT.matcher(it.text).find() }
        if (amountIdx >= 0) {
            val nearby = (amountIdx - 1 downTo maxOf(0, amountIdx - 2)) + ((amountIdx + 1)..minOf(lines.size - 1, amountIdx + 2))
            nearby.map { lines[it] }.firstOrNull { isCleanMerchantCandidate(it.text) }
                ?.let { return MerchantPick(it.text, FieldConfidence.INFERRED, it.confidence) }
        }

        // 兜底：全文首个干净行（排除页面装饰词）
        return lines.firstOrNull { isCleanMerchantCandidate(it.text) }
            ?.let { MerchantPick(it.text, FieldConfidence.INFERRED, it.confidence) }
    }

    /** 页面级收支汇总行（"支出¥156.45 收入¥2.40"），不是交易记录 */
    private fun isSummaryLine(line: String): Boolean =
        Regex("支出\\s*[¥￥]?\\s*\\d").containsMatchIn(line) && Regex("收入\\s*[¥￥]?\\s*\\d").containsMatchIn(line)

    /** 日期分组头（"9月9日 星期三 支0.00 收0.14"）返回其日期，非分组头返回 null */
    private fun dayHeaderDate(line: String): Long? {
        val m = DAY_HEADER.find(line) ?: return null
        if (m.range.first != 0) return null
        return parseDateGroup(m.value)
    }

    /** 去掉日期分组头上的"支0.00 收0.14"汇总数字，避免污染金额识别 */
    private fun stripDaySummary(line: String): String =
        line.replace(Regex("[支收]\\s*[\\d.]+"), "").trim()

    @Suppress("ReturnCount")
    private fun isCleanMerchantCandidate(line: String): Boolean {
        val clean = line.replace(" ", "").replace("　", "")
        if (clean.length !in 2..30) return false
        // 含金额符号/纯标点的行不是商户
        if (clean.any { it in "¥￥%*#@!&=" }) return false
        // 日期/时间行（"9月8日 17:23"）不是商户——账单列表页它们紧邻金额行
        if (TIME_PATTERN.matcher(clean).find()) return false
        if (Regex("^\\d{1,2}月\\d{1,2}日").containsMatchIn(clean)) return false
        // 页面装饰词（支付截图的标题/状态/按钮文字）
        val decorationWords = listOf(
            "支出", "收入", "交易", "账单", "支付", "完成", "时间", "状态", "成功",
            "凭证", "详情", "收款", "付款码", "二维码", "零钱", "余额", "钱包", "明细",
            "小票", "收据", "订单", "编号", "单号", "金额", "备注", "当前", "申请退款",
            "微信", "支付宝", "银行", "余额宝", "银行卡",
            // 电商订单页的操作按钮/服务标签（抖音/拼多多/淘宝实测会紧邻金额行）
            "评价", "售后", "转卖", "购物车", "拼单", "收货", "物流", "无理由", "退货"
        )
        if (decorationWords.any { clean.contains(it) }) return false
        // 至少含 2 个汉字（纯数字/单符号行不是商户）
        if (clean.count { it.code in 0x4E00..0x9FFF } < 2) return false
        return true
    }

    // ============================ 日期 ============================

    /**
     * 完整日期模式（含年份）判 [FieldConfidence.HIGH]；月/日式或"09.01"式（需补当前年）
     * 以及 [LOOSE_DATE_PATTERNS] 判 [FieldConfidence.INFERRED]。
     */
    private fun findDate(lines: List<OcrLine>): DatePick? {
        for (line in lines) {
            matchDateDetail(line.text, DATE_PATTERNS)?.let { (millis, hasYear) ->
                return DatePick(millis, if (hasYear) FieldConfidence.HIGH else FieldConfidence.INFERRED, line.confidence)
            }
        }
        for (line in lines) {
            matchDateDetail(line.text, LOOSE_DATE_PATTERNS)?.let { (millis, _) ->
                return DatePick(millis, FieldConfidence.INFERRED, line.confidence)
            }
        }
        return null
    }

    /** 返回 (毫秒, 是否含年份)；同一行内按模式顺序取第一个能解析出日期的模式 */
    private fun matchDateDetail(line: String, patterns: List<Pattern>): Pair<Long, Boolean>? =
        patterns.mapNotNull { matchDateOf(line, it) }.firstOrNull()

    /** 用单个日期模式在该行匹配并解析；未命中或无法解析出日期时返回 null */
    private fun matchDateOf(line: String, pat: Pattern): Pair<Long, Boolean>? {
        val m = pat.matcher(line)
        return if (m.find()) datePairOf(m.group(1)) else null
    }

    /** 把捕获组解析为 (毫秒, 是否含年份)；group 为空或解析失败返回 null */
    private fun datePairOf(group: String?): Pair<Long, Boolean>? =
        group?.let { g -> parseDateGroup(g)?.let { millis -> millis to YEAR_IN_GROUP.matcher(g).find() } }

    /** 解析日期文本；无年份的“8月30日 / 09.01”失败后补当前年再试（订单列表页常只显示月.日） */
    private fun parseDateGroup(group: String?): Long? {
        val dateStr = group
            ?.replace("年", "-")
            ?.replace("月", "-")
            ?.replace("日", "")
            ?.replace("/", "-")
            ?.replace(".", "-")
            ?: return null
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-M-d")
        val zone = java.time.ZoneId.systemDefault()
        return runCatching {
            java.time.LocalDate.parse(dateStr, fmt).atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching {
                val currentYear = java.time.LocalDate.now().year
                java.time.LocalDate.parse("$currentYear-$dateStr", fmt)
                    .atStartOfDay(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }
    }

    // ============================ 备注 / 分类 ============================

    // 说明：签名保持 List<String>（见 detekt baseline 的 CyclomaticComplexMethod 条目），
    // 调用方按 .map { it.text } 传入纯文本；备注不参与置信度分级。
    private fun findNote(lines: List<String>, merchant: String): String {
        val noteKeywords = listOf("商品", "品名", "名称", "明细", "说明", "描述")
        for (line in lines) {
            for (kw in noteKeywords) {
                val regex = Regex("$kw[：:]*\\s*(.+)")
                val match = regex.find(line)
                if (match != null) return match.groupValues[1].trim().take(100)
            }
        }
        val candidates = lines.filter {
            it.length in 2..60
                && it != merchant
                && !it.any { c -> c in "¥￥%/*-+@#" }
                && !DATE_PATTERNS.any { p -> p.matcher(it).find() }
                && !AMOUNT_PATTERN.matcher(it).find()
                // 截图角落/状态栏的纯时间行（"22:47"）不是备注
                && !TIME_PATTERN.matcher(it).find()
                // 至少含一个汉字或字母——纯数字/日期串（"20260909"）不是备注
                && it.any { c -> c.code in 0x4E00..0x9FFF || c.isLetter() }
                && it.replace(" ", "").let { c ->
                    !c.contains("支出") && !c.contains("收入") && !c.contains("交易") &&
                        !c.contains("支付") && !c.contains("时间") && !c.contains("状态") &&
                        !c.contains("成功") && !c.contains("凭证") && !c.contains("详情") &&
                        !c.contains("账单") && !c.contains("商户") &&
                        !c.contains("日期") && !c.contains("订单号") && !c.contains("单号")
                }
        }
        return candidates.firstOrNull() ?: ""
    }

    private fun guessCategory(lines: List<OcrLine>, merchant: String, note: String): String {
        val text = lines.joinToString(" ") { it.text } + " " + merchant + " " + note
        val raw = CategoryClassifier.guessCategory(text)
        return BillCsvImporter.normalizeCategory(raw, BillType.EXPENSE.value)
    }

    // ============================ 置信度归约 ============================

    /**
     * 来源行置信度低于 [OCR_CONFIDENCE_THRESHOLD] 时，字段至多判为 [FieldConfidence.INFERRED]。
     * 来源置信度为 null（字符串适配路径）时不降级——行为与历史版本一致。
     */
    private fun applyThreshold(confidence: FieldConfidence?, sourceConfidence: Float?): FieldConfidence {
        if (confidence == null) return FieldConfidence.MISSING
        if (confidence == FieldConfidence.HIGH && sourceConfidence != null && sourceConfidence < OCR_CONFIDENCE_THRESHOLD) {
            return FieldConfidence.INFERRED
        }
        return confidence
    }

    /** 日期来自分组头回退时判 INFERRED；来自识别行则按其自身置信度（见 [applyThreshold]） */
    private fun dateConfidenceOf(date: DatePick?, groupDate: Long?): FieldConfidence = when {
        date != null -> applyThreshold(date.confidence, date.sourceConfidence)
        groupDate != null -> FieldConfidence.INFERRED
        else -> FieldConfidence.MISSING
    }

    /** 分类始终由 [CategoryClassifier] 规则推断，永远不是直接读到：其余分类 INFERRED、"其他" MISSING */
    private fun categoryConfidenceOf(category: String): FieldConfidence =
        if (category == "其他") FieldConfidence.MISSING else FieldConfidence.INFERRED

    /** 金额解析结果：分值 + 置信度 + 来源行置信度（用于阈值降级） */
    private data class AmountPick(val cents: Long, val confidence: FieldConfidence, val sourceConfidence: Float?)

    /** 商户解析结果：名称 + 置信度 + 来源行置信度 */
    private data class MerchantPick(val name: String, val confidence: FieldConfidence, val sourceConfidence: Float?)

    /** 日期解析结果：毫秒 + 置信度 + 来源行置信度 */
    private data class DatePick(val millis: Long, val confidence: FieldConfidence, val sourceConfidence: Float?)

    companion object {
        /**
         * OCR 置信度阈值：低于此值的识别行只作参考，其推导出的字段至多判为 INFERRED。
         * 【初始值，待真机标定】——不同机型/字体的分数分布差异较大，需按实测调整。
         */
        private const val OCR_CONFIDENCE_THRESHOLD = 0.75f

        private val AMOUNT_PATTERN = Pattern.compile("[¥￥]\\s*(\\d+[.,]?\\d{0,2})")
        private val LOOSE_AMOUNT = Pattern.compile("(?<!\\d)(\\d+\\.\\d{2})(?!\\d)")
        private val YEAR_IN_GROUP = Pattern.compile("\\d{4}")
        private val DATE_PATTERNS = listOf(
            Pattern.compile("(\\d{4}[-年]\\d{1,2}[-月]\\d{1,2}[日]?)"),
            Pattern.compile("(\\d{4}[/.]\\d{1,2}[/.]\\d{1,2})"),
            Pattern.compile("(\\d{1,2}[-月]\\d{1,2}[日]?)"),
            // 订单列表的“09.01”式日期（零填充月.日）；价格如 19.90 会因月份>12解析失败被跳过
            Pattern.compile("(\\d{2}\\.\\d{2})")
        )
        private val LOOSE_DATE_PATTERNS = listOf(
            Pattern.compile("(\\d{4}\\d{2}\\d{2})")
        )
        private val TIME_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}(:\\d{2})?")
        // 账单列表页的裸金额行（微信"-10.14"、支付宝"+0.12"），须带两位小数避免误切年份等整数行
        private val BARE_AMOUNT = Pattern.compile("[+＋\\-−－]?\\d{1,6}\\.\\d{2}")
        private val BARE_SIGNED_INCOME = Pattern.compile("^[+＋]\\s*\\d")
        private val BARE_SIGNED_EXPENSE = Pattern.compile("^[-−－]\\s*\\d")
        private val DAY_HEADER = Regex("^\\d{1,2}月\\d{1,2}日")
    }
}
