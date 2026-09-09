package com.palmnote.data.ocr

import com.palmnote.data.export.BillCsvImporter
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.Money
import com.palmnote.domain.util.CategoryClassifier
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

data class OcrBillResult(
    val amount: Long? = null, // 金额（分）
    val merchant: String = "",
    val date: Long? = null,
    val note: String = "",
    val category: String = "其他",
    /** 按笔识别的收/支类型；null = 无法判断，由调用方回退到用户手选的默认类型 */
    val type: BillType? = null
)

class BillOcrParser {

    fun parse(text: String): OcrBillResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return OcrBillResult()
        val amount = findAmount(lines)
        val merchant = findMerchant(lines)
        val date = findDate(lines)
        val note = findNote(lines, merchant)
        val category = guessCategory(lines, merchant, note)
        return OcrBillResult(amount = amount, merchant = merchant, date = date, note = note,
            category = category, type = detectType(lines))
    }

    fun parseMultiple(text: String): List<OcrBillResult> {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (rawLines.isEmpty()) return emptyList()
        // 页面级汇总行（微信"支出¥156.45 收入¥2.40"、支付宝记账本"9月总支出…"）不是交易
        val lines = rawLines.filterNot { isSummaryLine(it) }

        val blocks = splitIntoBlocks(lines)
        if (blocks.size <= 1) return listOf(parse(text))

        return blocks.mapNotNull { (block, gDate) ->
            val amount = findAmount(block) ?: return@mapNotNull null
            val merchant = findMerchant(block)
            val date = findDate(block) ?: gDate
            val note = findNote(block, merchant)
            val category = guessCategory(block, merchant, note)
            OcrBillResult(amount = amount, merchant = merchant, date = date, note = note,
                category = category, type = detectType(block))
        }.filter { (it.amount ?: 0) > 0 }
    }

    /**
     * 按页面结构把识别行切成交易块，块关联所属日期分组的日期（支付宝记账本按日分组，
     * 组内条目行常不带日期）。三种块边界：
     * 1. 电商订单卡片以"实付款"行收尾（抖音/拼多多/淘宝订单页实测）
     * 2. 微信/支付宝账单列表页金额无¥前缀独立成行，金额行即一条交易的结尾
     * 3. isNewTransaction 的日期/分隔线边界
     */
    private fun splitIntoBlocks(lines: List<String>): List<Pair<MutableList<String>, Long?>> {
        val blocks = mutableListOf<Pair<MutableList<String>, Long?>>()
        var current = mutableListOf<String>()
        var groupDate: Long? = null
        fun flush() {
            if (current.isNotEmpty()) blocks.add(current to groupDate)
            current = mutableListOf()
        }
        for (line in lines) {
            val headerDate = dayHeaderDate(line)
            when {
                // 电商订单卡片以"实付款"行收尾；账单列表页裸金额行即一条交易的结尾
                (line.contains("实付") && current.any { AMOUNT_PATTERN.matcher(it).find() }) ||
                    BARE_AMOUNT.matcher(line).matches() -> { current.add(line); flush() }
                // 日期分组头（"9月9日 星期三 支0.00 收0.14"）：记录组日期供条目回退
                headerDate != null -> { groupDate = headerDate; current.add(stripDaySummary(line)) }
                else -> {
                    if (isNewTransaction(line, current)) flush()
                    current.add(line)
                }
            }
        }
        flush()
        return blocks
    }

    /**
     * 按笔识别收/支类型（收支混排截图不能共用同一类型——issue#1 修复的延伸）：
     * 金额符号前缀最可靠，其次关键词；都没有时返回 null 交由用户默认值。
     */
    private fun detectType(lines: List<String>): BillType? =
        detectSignType(lines) ?: detectKeywordType(lines)

    /** 符号前缀：+¥/-¥ 最可靠，其次账单列表页的行首符号裸金额（+0.23/-32.22） */
    private fun detectSignType(lines: List<String>): BillType? {
        val text = lines.joinToString(" ")
        return when {
            Regex("[+＋]\\s*[¥￥]").containsMatchIn(text) -> BillType.INCOME
            Regex("[-−－]\\s*[¥￥]").containsMatchIn(text) -> BillType.EXPENSE
            lines.any { BARE_SIGNED_INCOME.matcher(it).find() } -> BillType.INCOME
            lines.any { BARE_SIGNED_EXPENSE.matcher(it).find() } -> BillType.EXPENSE
            else -> null
        }
    }

    /** 关键词兜底：电商订单页常含"申请退款/红包抵扣"等按钮文字，不能用宽泛的"退款/红包"判收入 */
    private fun detectKeywordType(lines: List<String>): BillType? {
        val text = lines.joinToString(" ")
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

        val hasDateHere = DATE_PATTERNS.any { it.matcher(line).find() }
        val hasAmountHere = AMOUNT_PATTERN.matcher(line).find()

        val prevHasAmount = prevLines.any { AMOUNT_PATTERN.matcher(it).find() }

        if (hasDateHere && prevHasAmount) return true

        if (hasAmountHere && prevLines.any { DATE_PATTERNS.any { p -> p.matcher(it).find() } }) {
            if (!prevLines.any { AMOUNT_PATTERN.matcher(it).find() }) return true
        }

        if (line.startsWith("-¥") || line.startsWith("-￥") || line.startsWith("+¥") || line.startsWith("+￥")) {
            if (prevHasAmount) return true
        }

        val separators = listOf("---", "═══", "————", "-----", "————————")
        if (separators.any { line.contains(it) }) return true

        return false
    }

    private fun findAmount(lines: List<String>): Long? =
        findPaidAmount(lines) ?: largestAmount(lines)

    /**
     * 优先取“实付款/实收”等实付金额行——电商订单页常含商品原价/优惠/推荐商品价格，
     * 盲取最大值会取错（如拼多多详情页会把 ¥6.25 原价当成实付 ¥4.16）
     */
    private fun findPaidAmount(lines: List<String>): Long? {
        val paidLabel = Regex("实付|实收|付款金额|支付金额|本次支付")
        for (i in lines.indices) {
            val line = lines[i]
            val labelMatch = paidLabel.find(line) ?: continue
            val afterLabel = line.substring(labelMatch.range.last + 1)
            val afterAmts = amountsIn(afterLabel)
            val nextAmts = lines.getOrNull(i + 1)?.let { amountsIn(it) } ?: emptyList()
            // “共减/红包”行上的是优惠金额（如淘宝“实付款 共减¥3”），真实付款额常在下一行
            val picked = when {
                Regex("共减|红包|立减").containsMatchIn(afterLabel) && nextAmts.isNotEmpty() -> nextAmts
                afterAmts.isNotEmpty() -> afterAmts
                else -> nextAmts
            }
            picked.firstOrNull { it > 0 }?.let { return Money.fromYuan(it).cents }
        }
        return null
    }

    /** 兜底：无实付行时取最大金额（微信/支付宝账单等原有场景）。¥ 金额与裸金额合并取最大——
     *  账单列表行常带"已退款(¥0.23)"副行，若 ¥ 优先会取到退款额而非实付额 */
    private fun largestAmount(lines: List<String>): Long? {
        val candidates = (lines.flatMap(::amountsIn) + lines.flatMap(::looseAmountsIn)).filter { it > 0 }
        return candidates.maxOrNull()?.let { Money.fromYuan(it).cents }
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

    private fun findMerchant(lines: List<String>): String {
        val merchantKeywords = listOf(
            "商户(?!单号|号)", "商家", "收款方", "收款单位", "付款方", "对方", "门店", "店铺", "公司",
            "付款给", "向.*付款"
        )
        for (line in lines) {
            for (kw in merchantKeywords) {
                val regex = Regex("$kw[：:]*\\s*(.+)")
                val match = regex.find(line)
                if (match != null) {
                    return match.groupValues[1].trim().removeSurrounding("\"").take(50)
                }
            }
        }

        // 支付截图布局中商户名通常紧邻金额：优先在金额行的上下邻近行找候选，
        // 而不是取识别文本最前面的行（那常是"微信支付"/"账单详情"等页面装饰——issue#1）
        val amountIdx = lines.indexOfFirst { AMOUNT_PATTERN.matcher(it).find() || LOOSE_AMOUNT.matcher(it).find() }
        if (amountIdx >= 0) {
            val nearby = (amountIdx - 1 downTo maxOf(0, amountIdx - 2)) + ((amountIdx + 1)..minOf(lines.size - 1, amountIdx + 2))
            nearby.map { lines[it] }.firstOrNull { isCleanMerchantCandidate(it) }?.let { return it }
        }

        // 兜底：全文首个干净行（排除页面装饰词）
        return lines.firstOrNull { isCleanMerchantCandidate(it) } ?: ""
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

    private fun findDate(lines: List<String>): Long? =
        firstDate(lines, DATE_PATTERNS) ?: firstDate(lines, LOOSE_DATE_PATTERNS)

    private fun firstDate(lines: List<String>, patterns: List<Pattern>): Long? =
        lines.firstNotNullOfOrNull { line -> matchDate(line, patterns) }

    private fun matchDate(line: String, patterns: List<Pattern>): Long? =
        patterns.firstNotNullOfOrNull { pat ->
            val m = pat.matcher(line)
            if (m.find()) parseDateGroup(m.group(1)) else null
        }

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

    private fun guessCategory(lines: List<String>, merchant: String, note: String): String {
        val text = lines.joinToString(" ") + " " + merchant + " " + note
        val raw = CategoryClassifier.guessCategory(text)
        return BillCsvImporter.normalizeCategory(raw, BillType.EXPENSE.value)
    }

    companion object {
        private val AMOUNT_PATTERN = Pattern.compile("[¥￥]\\s*(\\d+[.,]?\\d{0,2})")
        private val LOOSE_AMOUNT = Pattern.compile("(?<!\\d)(\\d+\\.\\d{2})(?!\\d)")
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
