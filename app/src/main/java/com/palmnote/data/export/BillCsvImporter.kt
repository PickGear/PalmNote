package com.palmnote.data.export

import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.Money
import com.palmnote.domain.util.CategoryClassifier
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedBill(
    val date: Long,
    val type: String,
    val amount: Long, // 金额（分）
    val category: String,
    val merchant: String,
    val note: String,
    val paymentMethod: String,
    val transactionId: String = "",
    /** 分类是否「明确」：true=源分类标签/词表命中；false=未能识别、回退「其他」（供复核判定用） */
    val categoryResolved: Boolean = true,
    /** false = 源账单标记为「不计收支」（转账/还款/理财申赎等），导入预览默认不勾选、落入「已跳过」档；用户勾上即可导入 */
    val defaultSelected: Boolean = true
)

class BillCsvImporter {

    fun detectFormat(lines: List<String>): CsvFormat {
        for (line in lines) {
            val clean = line.trimStart('\uFEFF').trim()
            if (clean.contains("记录时间") && clean.contains("收支")) return CsvFormat.ALIPAY
            // 支付宝当代官方导出：表头为"交易创建时间…收/支"（无"记录时间/交易时间"字样）
            if (clean.contains("交易创建时间") && clean.contains("收/支")) return CsvFormat.ALIPAY
            if (clean.contains("交易时间") && clean.contains("收/支")) {
                if (clean.contains("商品说明") || clean.contains("交易分类")) return CsvFormat.ALIPAY
                if (clean.contains("商品") || clean.contains("交易对方") || clean.contains("交易类型")) return CsvFormat.WECHAT
            }
        }
        for (line in lines) {
            if (line.contains("微信支付") || line.contains("微信账单")) return CsvFormat.WECHAT
            if (line.contains("支付宝") || line.contains("Alipay")) return CsvFormat.ALIPAY
        }
        // 通用兜底：任意来源（银行/云闪付/手动表格等），表头含"时间/日期"+"金额"即可尝试
        for (line in lines) {
            val clean = line.trimStart('\uFEFF').trim()
            if (clean.contains("金额") && (clean.contains("时间") || clean.contains("日期"))) return CsvFormat.GENERIC
        }
        return CsvFormat.UNKNOWN
    }

    fun parseFromLines(lines: List<String>, format: CsvFormat, diag: StringBuilder? = null): List<ParsedBill> =
        parseWithFailures(lines, format, diag, null)

    /**
     * 与 [parseFromLines] 接受完全相同的记录，但额外把被丢弃的行（原始文本 + 原因）收集到 [fails]，
     * 供结果页统计与导出「可修正后重新导入」的 CSV。
     */
    fun parseWithFailures(
        lines: List<String>,
        format: CsvFormat,
        diag: StringBuilder? = null,
        fails: MutableList<ImportFailure>? = null
    ): List<ParsedBill> {
        val headerLine = findHeaderLine(lines, format)
        diag?.append("CSV表头行: ${if (headerLine != null) headerLine.take(80) else "未找到"}\n")
        if (headerLine == null) {
            // detectFormat 的品牌分支比品牌解析器宽松（如「交易时间 + 收/支 + 商品说明」判为支付宝，
            // 但支付宝解析器只认「记录时间 / 交易创建时间」）。判成品牌却找不到表头时回退通用，
            // 别让整份文件白白失败——银行、云闪付的表头常落进这个缝里。
            if (format == CsvFormat.WECHAT || format == CsvFormat.ALIPAY) {
                diag?.append("品牌表头未命中 → 回退通用\n")
                return parseWithFailures(lines, CsvFormat.GENERIC, diag, fails)
            }
            return emptyList()
        }
        val sep = detectSeparator(headerLine)
        val headerIdx = parseCsvLine(headerLine, sep).mapIndexed { i, h -> h.trim() to i }.toMap()
        val dataLines = dataLinesAfter(lines, headerLine)

        return when (format) {
            CsvFormat.WECHAT -> parseWechat(dataLines, headerIdx, sep, fails)
            CsvFormat.ALIPAY -> parseAlipay(dataLines, headerIdx, sep, fails)
            CsvFormat.GENERIC -> parseGeneric(dataLines, headerIdx, sep, fails)
            CsvFormat.UNKNOWN -> emptyList()
        }
    }

    /** 按品牌格式的关键词定位表头行 */
    private fun findHeaderLine(lines: List<String>, format: CsvFormat): String? = when (format) {
        CsvFormat.ALIPAY -> lines.firstOrNull {
            it.contains("记录时间") || it.contains("交易创建时间") ||
                (it.contains("交易时间") && it.contains("收支"))
        }
        CsvFormat.GENERIC -> lines.firstOrNull {
            it.contains("金额") && (it.contains("时间") || it.contains("日期"))
        }
        else -> lines.firstOrNull {
            it.contains("交易时间") && (it.contains("收/支") || it.contains("金额"))
        }
    }

    /** 表头之后的真实数据行（过滤空行 / 分隔线 / 合计行） */
    private fun dataLinesAfter(lines: List<String>, headerLine: String): List<String> =
        lines.drop(lines.indexOf(headerLine) + 1).filter {
            it.isNotBlank() && !it.startsWith("---") && !it.contains("合计") && !it.contains("本笔")
        }

    /** 记录一条被丢弃的行并返回 null，便于在 `ifBlank {}` / elvis 表达式中内联使用 */
    private fun MutableList<ImportFailure>?.reject(line: String, reason: ImportFailureReason): Nothing? {
        this?.add(ImportFailure(line.trim(), reason))
        return null
    }

    private fun detectSeparator(line: String): Char {
        if (line.contains('\t')) return '\t'
        val commas = line.count { it == ',' }
        val semicolons = line.count { it == ';' }
        return if (semicolons > commas && semicolons > 2) ';' else ','
    }

    private fun col(headerIdx: Map<String, Int>, keyword: String): Int? {
        return headerIdx.entries.firstOrNull { it.key.contains(keyword) }?.value
    }

    private fun cell(cols: List<String>, idx: Int?): String {
        return idx?.let { cols.getOrNull(it)?.trim() } ?: ""
    }

    /**
     * 金额文本清洗：去货币符号/正负号/各类空格；逗号按「是不是三位分组」区别对待——
     * `1,234.56` 的逗号是千分位（去掉），`45,00` 的逗号是小数逗号（换成小数点）。
     * 一刀切会把 `45,00` 读成 4500 元（金额错 100 倍，实测某银行的分号导出就是这种写法）。
     * 全角逗号实测是支付宝导出的千分位，保持按千分位处理。
     */
    private fun cleanAmountText(raw: String): String = raw
        .replace("¥", "").replace("￥", "")
        .replace("+", "").replace("-", "")
        .replace(THOUSANDS_COMMA, "")
        .replace(',', '.')
        .replace("\uFF0C", "")
        .replace(" ", "").replace("\u3000", "").replace("\u00A0", "")

    private fun parseWechat(
        lines: List<String>,
        headerIdx: Map<String, Int>,
        sep: Char,
        fails: MutableList<ImportFailure>?
    ): List<ParsedBill> {
        val dateIdx = col(headerIdx, "交易时间")
        val typeIdx = col(headerIdx, "交易类型")
        val merchantIdx = col(headerIdx, "交易对方")
        val goodsIdx = col(headerIdx, "商品")
        val ieIdx = col(headerIdx, "收/支")
        val amountIdx = col(headerIdx, "金额")
        val methodIdx = col(headerIdx, "支付方式")
        val noteIdx = col(headerIdx, "备注")
        val txIdIdx = col(headerIdx, "交易单号")

        return lines.mapNotNull { line ->
            try {
                val cols = parseCsvLine(line, sep)
                val timeStr = cell(cols, dateIdx).ifBlank { return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_DATE) }
                val amountStr = cell(cols, amountIdx).ifBlank { return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_AMOUNT) }
                val cleanAmount = cleanAmountText(amountStr)
                val amount = Money.parse(cleanAmount)?.cents ?: return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_AMOUNT)
                val date = parseDate(timeStr) ?: return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_DATE)
                val isIncome = cell(cols, ieIdx).contains("收入")
                // 备注常为空，商品名承载消费内容（分类推断的重要信号），回退后再参与推断
                val note = cell(cols, noteIdx).ifBlank { cell(cols, goodsIdx).ifBlank { cell(cols, typeIdx) } }
                val billType = if (isIncome) BillType.INCOME.value else BillType.EXPENSE.value
                val nc = normalizeCategoryEx(
                    guessCategory(cell(cols, merchantIdx), note, cell(cols, typeIdx)),
                    billType
                )

                ParsedBill(
                    date = date,
                    type = billType,
                    amount = amount,
                    category = nc.category,
                    merchant = cell(cols, merchantIdx),
                    note = note,
                    paymentMethod = mapPaymentMethod(cell(cols, methodIdx)),
                    transactionId = cell(cols, txIdIdx),
                    categoryResolved = nc.category != "其他"
                )
            } catch (_: Exception) {
                fails?.add(ImportFailure(line.trim(), ImportFailureReason.UNPARSEABLE))
                null
            }
        }
    }

    // 状态过滤已移除：无效/退款行全部进入预览，由用户在导入预览中自行勾选
    // （原白名单遗漏"已存入零钱"等大量真实状态变体，导致 Excel 导入大面积丢行——issue#1）

    /** 支付宝两种官方格式的列索引（旧版：记录时间/交易分类/商品说明；当代：交易创建时间/类型/商品名称） */
    private class AlipayColumns(
        val dateIdx: Int?,
        val categoryIdx: Int?,
        val merchantIdx: Int?,
        val goodsIdx: Int?,
        val ieIdx: Int?,
        val amountIdx: Int?,
        val noteIdx: Int?,
        val accountIdx: Int?,
        val txIdIdx: Int?
    )

    private fun alipayColumns(headerIdx: Map<String, Int>): AlipayColumns = AlipayColumns(
        dateIdx = col(headerIdx, "记录时间") ?: col(headerIdx, "交易创建时间")
           ?: col(headerIdx, "交易时间") ?: col(headerIdx, "付款时间"),
        categoryIdx = col(headerIdx, "交易分类") ?: col(headerIdx, "分类"),
        merchantIdx = col(headerIdx, "交易对方") ?: col(headerIdx, "商品说明"),
        goodsIdx = col(headerIdx, "商品名称") ?: col(headerIdx, "商品说明") ?: col(headerIdx, "商品"),
        ieIdx = col(headerIdx, "收支类型") ?: col(headerIdx, "收/支"),
        amountIdx = col(headerIdx, "金额"),
        noteIdx = col(headerIdx, "备注"),
        accountIdx = col(headerIdx, "账户"),
        txIdIdx = col(headerIdx, "交易号")
    )

    private fun parseAlipay(
        lines: List<String>,
        headerIdx: Map<String, Int>,
        sep: Char,
        fails: MutableList<ImportFailure>?
    ): List<ParsedBill> {
        val c = alipayColumns(headerIdx)

        return lines.mapNotNull { line ->
            try {
                val cols = parseCsvLine(line, sep)
                val timeStr = cell(cols, c.dateIdx).ifBlank { return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_DATE) }
                val amountStr = cell(cols, c.amountIdx).ifBlank { return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_AMOUNT) }
                val cleanAmount = cleanAmountText(amountStr)
                val amount = Money.parse(cleanAmount)?.cents ?: return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_AMOUNT)
                val ieType = cell(cols, c.ieIdx)
                // 手机支付宝导出没有"交易对方/商品说明/商品名称"列，此时"备注"是唯一可读文本（实测多为商户名）。
                // 不回退则每行 merchant 皆空 → reviewReasonOf 全命中 BLANK_MERCHANT → 分拣台退化成"全部待复核"
                val rawMerchant = cell(cols, c.merchantIdx).ifBlank { cell(cols, c.goodsIdx) }
                val note = cell(cols, c.noteIdx).ifBlank { cell(cols, c.goodsIdx) }.ifBlank { cell(cols, c.accountIdx) }
                val merchant = rawMerchant.ifBlank { note }
                val category = cell(cols, c.categoryIdx)
                val date = parseDate(timeStr) ?: return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_DATE)
                val isIncome = ieType.contains("收入")
                val billType = if (isIncome) BillType.INCOME.value else BillType.EXPENSE.value
                val nc = if (category.isNotBlank()) {
                    normalizeCategoryEx(category, billType)
                } else {
                    // 源分类为空：靠商户/备注推断，推断结果仍为「其他」时视为未识别
                    val inferred = normalizeCategoryEx(guessCategory(merchant, note, ""), billType)
                    NormalizedCategory(inferred.category, inferred.category != "其他")
                }

                ParsedBill(
                    date = date,
                    type = billType,
                    amount = amount,
                    category = nc.category,
                    merchant = merchant,
                    note = note,
                    paymentMethod = "ALIPAY",
                    // 交易号是最可靠去重键：同商户同金额同时刻的账单不会被误判重复
                    transactionId = cell(cols, c.txIdIdx),
                    categoryResolved = nc.resolved,
                    defaultSelected = !ieType.contains("不计收支")
                )
            } catch (_: Exception) {
                fails?.add(ImportFailure(line.trim(), ImportFailureReason.UNPARSEABLE))
                null
            }
        }
    }

    // 通用格式：不依赖品牌表头，按关键词匹配列（银行/云闪付/手动表格等其他导出来源）
    private fun parseGeneric(
        lines: List<String>,
        headerIdx: Map<String, Int>,
        sep: Char,
        fails: MutableList<ImportFailure>?
    ): List<ParsedBill> {
        val c = GenericColumns.resolve(headerIdx)

        return lines.mapNotNull { line ->
            try {
                val cols = parseCsvLine(line, sep)
                val date = parseRowDate(cols, c.dateIdx, c.timeIdx)
                    ?: return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_DATE)
                val rowAmount = rowAmount(cols, c)
                    ?: return@mapNotNull fails.reject(line, ImportFailureReason.MISSING_AMOUNT)
                val ieText = cell(cols, c.ieIdx)
                val isIncome = rowAmount.incomeByColumn ?: when {
                    rowAmount.signIncome -> true
                    ieText.contains("收入") || ieText.contains("转入") || ieText.contains("贷") -> true
                    else -> false
                }
                val merchant = cell(cols, c.merchantIdx)
                val note = cell(cols, c.noteIdx)
                val categoryText = cell(cols, c.categoryIdx)
                val type = if (isIncome && !rowAmount.negative) BillType.INCOME.value else BillType.EXPENSE.value
                val nc = if (categoryText.isNotBlank()) {
                    normalizeCategoryEx(categoryText, type)
                } else {
                    val inferred = normalizeCategoryEx(guessCategory(merchant, note, ieText), type)
                    NormalizedCategory(inferred.category, inferred.category != "其他")
                }

                ParsedBill(
                    date = date,
                    type = type,
                    amount = rowAmount.cents,
                    category = nc.category,
                    merchant = merchant,
                    note = note,
                    paymentMethod = "OTHER",
                    categoryResolved = nc.resolved,
                    defaultSelected = !ieText.contains("不计收支")
                )
            } catch (_: Exception) {
                fails?.add(ImportFailure(line.trim(), ImportFailureReason.UNPARSEABLE))
                null
            }
        }
    }

    /** 通用表头 → 列索引。银行导出的表头分化很大，三类金额布局都要认（见 [rowAmount]） */
    private class GenericColumns(
        val dateIdx: Int?,
        val timeIdx: Int?,
        val singleAmountIdx: Int?,
        val incomeAmountIdx: Int?,
        val expenseAmountIdx: Int?,
        val ieIdx: Int?,
        val merchantIdx: Int?,
        val noteIdx: Int?,
        val categoryIdx: Int?
    ) {
        companion object {
            fun resolve(h: Map<String, Int>): GenericColumns {
                /** 命中第一个出现的关键词（同义词按优先级传入） */
                fun col(vararg keywords: String): Int? = keywords.firstNotNullOfOrNull { kw ->
                    h.entries.firstOrNull { it.key.contains(kw) }?.value
                }
                // 日期优先：只按「时间」取列会拿到纯时间列（"12:30:00"），整份文件将被判日期缺失
                val dateCol = col("日期", "时间")
                return GenericColumns(
                    dateIdx = dateCol,
                    timeIdx = col("时间")?.takeIf { it != dateCol },
                    singleAmountIdx = h.entries.firstOrNull { isPlainAmountColumn(it.key) }?.value,
                    incomeAmountIdx = col("收入", "贷方", "存入"),
                    expenseAmountIdx = col("支出", "借方", "支取"),
                    ieIdx = col("收/支", "收支", "借贷", "收付", "类型"),
                    merchantIdx = col("商户", "对方", "摘要", "描述", "收款方", "付款方"),
                    noteIdx = col("备注", "说明"),
                    categoryIdx = col("分类", "类别")
                )
            }
        }
    }

    /** 一行的金额与方向：分列决定的方向优先，其次是金额自带的正负号 */
    private class RowAmount(
        val cents: Long,
        val incomeByColumn: Boolean?,
        val signIncome: Boolean,
        val negative: Boolean
    )

    /**
     * 取一行的金额。三种真实布局：
     * 1. 单一「金额」列 + 收支/借贷标志列（标志由 [GenericColumns.ieIdx] 解读）；
     * 2. 收入、支出分列（银行常见）：哪列有值就用哪列，方向由列本身决定；
     * 3. 带符号的单列（+ 收入 / - 支出）。
     */
    private fun rowAmount(cols: List<String>, c: GenericColumns): RowAmount? {
        val single = cell(cols, c.singleAmountIdx)
        if (single.isNotBlank()) {
            centsOf(single)?.let {
                val head = single.trimStart()
                return RowAmount(it, null, head.startsWith("+"), head.startsWith("-"))
            }
        }
        val income = cell(cols, c.incomeAmountIdx)
        if (income.isNotBlank()) centsOf(income)?.let { return RowAmount(it, true, false, false) }
        val expense = cell(cols, c.expenseAmountIdx)
        if (expense.isNotBlank()) centsOf(expense)?.let { return RowAmount(it, false, false, false) }
        return null
    }

    private fun centsOf(raw: String): Long? =
        if (raw.isBlank()) null else Money.parse(cleanAmountText(raw))?.cents

    /**
     * 通用格式的日期：优先日期列；只有时间列时用它；两列都有但日期列不成日期时拼起来。
     * 银行常把日期与时间分成两列（交易日期 + 交易时间），这是此前整份文件被判日期缺失的根因。
     */
    private fun parseRowDate(cols: List<String>, dateIdx: Int?, timeIdx: Int?): Long? {
        val d = cell(cols, dateIdx)
        parseDate(d)?.let { return it }
        val t = cell(cols, timeIdx)
        if (t.isNotBlank() && t != d) parseDate(t)?.let { return it }
        return if (d.isNotBlank() && t.isNotBlank()) parseDate("$d $t") else null
    }

    private fun parseDate(timeStr: String): Long? {
        val clean = timeStr.trim()
        for (pat in listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
            "yyyy/M/d HH:mm:ss", "yyyy/M/d HH:mm", "yyyy-MM-dd", "yyyy/MM/dd", "yyyy/M/d",
            "yyyy年M月d日 HH:mm:ss", "yyyy年M月d日 HH:mm", "yyyy年M月d日"
        )) {
            // parse 返回 null 时继续试下一个模式；不再为每个失败模式记一条日志（真正无法解析由上层 MISSING_DATE 兜底）
            try { SimpleDateFormat(pat, Locale.getDefault()).parse(clean)?.let { return it.time } } catch (_: Exception) { /* 试下一个模式 */ }
        }
        return null
    }

    /**
     * 银行网页导出的「.xls」多半是 HTML 表格套壳（真 BIFF 另说）。把 `<tr>/<td>` 抽成 CSV 行，
     * 后续的格式判定与通用解析就能像普通表格一样处理；非 HTML 内容原样返回。
     */
    fun normalizeLines(lines: List<String>): List<String> {
        val text = lines.joinToString("\n")
        if (!text.contains("<table", ignoreCase = true) && !text.contains("<tr", ignoreCase = true)) return lines
        return TABLE_ROW.findAll(text)
            .map { row ->
                TABLE_CELL.findAll(row.groupValues[1])
                    .map { quoteCsvField(unescapeHtml(TAG.replace(it.groupValues[1], "")).trim()) }
                    .joinToString(",")
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun unescapeHtml(raw: String): String = raw
        .replace("&nbsp;", " ")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&amp;", "&")

    companion object {
        /** 「金额」列的分列名标记：带这些字样的「…金额」列属于收入/支出分列，不是通用金额列 */
        private val SPLIT_AMOUNT_MARKERS = listOf("收入", "支出", "借", "贷")

        /**
         * 「金额」列且**不是**收入/支出分列名。不排除的话，列序靠前的「收入金额」会被当成通用金额，
         * 于是支出行取到空值被丢弃、收入行按支出记账。
         */
        private fun isPlainAmountColumn(header: String): Boolean =
            header.contains("金额") && SPLIT_AMOUNT_MARKERS.none { header.contains(it) }

        /** 半角逗号当千分位的判定：后面正好跟 3 位数字且再后面不是数字（1,234 / 1,234.56 / 1,234,567） */
        private val THOUSANDS_COMMA = Regex(",(?=\\d{3}(?:\\D|$))")

        private val TABLE_ROW = Regex("(?is)<tr[^>]*>(.*?)</tr>")
        private val TABLE_CELL = Regex("(?is)<t[dh][^>]*>(.*?)</t[dh]>")
        private val TAG = Regex("(?is)<[^>]+>")

        /** 需要加引号包裹的字符：分隔符、引号与各类空白 */
        private const val QUOTE_TRIGGER_CHARS = ",;\"\t\n\r"

        /** CSV 字段转义：含分隔符/引号/空白换行时用双引号包裹，内部引号翻倍 */
        internal fun quoteCsvField(value: String): String =
            if (value.any { it in QUOTE_TRIGGER_CHARS }) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }

        private val EXPENSE_CATEGORIES = setOf("餐饮", "零食", "饮品", "交通", "购物", "服饰", "数码", "二手", "居住", "家居", "租金", "娱乐", "旅游", "运动", "医疗", "健身", "美容", "教育", "文具", "社交", "人情", "红包", "赠与", "通讯", "家政", "快递", "维修", "投资", "股票", "理财", "保险", "宠物", "母婴", "烟酒", "捐赠", "罚款", "手续费", "其他")
        private val INCOME_CATEGORIES = setOf("工资", "奖金", "兼职", "副业", "报销", "投资", "股票", "理财", "分红", "利息", "租金", "二手", "红包", "赠与", "人情", "退款", "中奖", "保险理赔", "继承", "其他")

        /**
         * 支付宝等渠道的**分类标签**别名（入参已知是分类名，不是自由文本；勿并入 CategoryClassifier）。
         * 按**完整**标签匹配——避免自由文本误伤（如「交通银行」「文化路支行」）。
         */
        private val CATEGORY_ALIASES: Map<String, String> = mapOf(
            "餐饮美食" to "餐饮", "食品酒饮" to "餐饮",
            "交通出行" to "交通",
            "日用百货" to "购物", "服饰装扮" to "购物", "生活日用" to "购物",
            "居家物业" to "居住", "住房物业" to "居住",
            "医疗保健" to "医疗",
            "数码电器" to "数码",
            "休闲玩乐" to "娱乐", "文化休闲" to "娱乐",
            "运动户外" to "运动",
            "母婴亲子" to "母婴",
            "酒店旅行" to "旅游",
            "投资理财" to "投资"
        )

        /**
         * [resolved]=true：分类来自 when 词表 / [CATEGORY_ALIASES] 的**明确**映射
         * （含源值本来就是「其他」，以及词表有意映射到「其他」的「转账/生活服务/其他支出/其他收入」）；
         * false：都没命中、只能回退「其他」。
         */
        data class NormalizedCategory(val category: String, val resolved: Boolean)

        fun normalizeCategory(category: String, type: String): String =
            normalizeCategoryEx(category, type).category

        // 大 when 词表：与旧 normalizeCategory 同体量，等价于 baseline 原条目（改名后需显式抑制）
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        fun normalizeCategoryEx(category: String, type: String): NormalizedCategory {
            val valid = if (type == BillType.EXPENSE.value) EXPENSE_CATEGORIES else INCOME_CATEGORIES
            val norm = when (category) {
                // 转账/其他
                "转账" -> "其他"
                "生活服务" -> "其他"
                "其他支出" -> "其他"
                "其他收入" -> "其他"
                // 餐饮
                "聚餐" -> "餐饮"
                "外卖" -> "餐饮"
                "饮品" -> "餐饮"
                "美食" -> "餐饮"
                "早餐" -> "餐饮"
                "午餐" -> "餐饮"
                "晚餐" -> "餐饮"
                "夜宵" -> "餐饮"
                "水果" -> "零食"
                // 购物
                "日用" -> "购物"
                "日化" -> "购物"
                "生活" -> "购物"
                "生活用品" -> "购物"
                "生活日用品" -> "购物"
                "超市" -> "购物"
                "网购" -> "购物"
                "快递" -> "购物"
                // 美容（合并美发）
                "美发" -> "美容"
                "理发" -> "美容"
                "护肤" -> "美容"
                "化妆品" -> "美容"
                "彩妆" -> "美容"
                // 交通
                "加油" -> "交通"
                "停车" -> "交通"
                "过路费" -> "交通"
                "保养" -> "交通"
                "洗车" -> "交通"
                "车险" -> "保险"
                "违章" -> "罚款"
                // 居住
                "房租" -> "租金"
                "水电" -> "居住"
                "燃气" -> "居住"
                "物业" -> "居住"
                "暖气" -> "居住"
                "房贷" -> "居住"
                "装修" -> "家居"
                "家具" -> "家居"
                "家电" -> "家居"
                // 通讯
                "宽带" -> "通讯"
                "话费" -> "通讯"
                "流量" -> "通讯"
                "手机" -> "通讯"
                // 医疗
                "药" -> "医疗"
                "看病" -> "医疗"
                "体检" -> "医疗"
                "挂号" -> "医疗"
                // 教育
                "培训" -> "教育"
                "课程" -> "教育"
                "学费" -> "教育"
                "书" -> "教育"
                "文具" -> "教育"
                // 娱乐
                "电影" -> "娱乐"
                "游戏" -> "娱乐"
                "KTV" -> "娱乐"
                "演出" -> "娱乐"
                "门票" -> "娱乐"
                // 旅游
                "酒店" -> "旅游"
                "民宿" -> "旅游"
                "景区" -> "旅游"
                // 交通
                "机票" -> "交通"
                "火车票" -> "交通"
                "高铁" -> "交通"
                "打车" -> "交通"
                "滴滴" -> "交通"
                "地铁" -> "交通"
                "公交" -> "交通"
                "共享单车" -> "交通"
                "出租" -> "交通"
                // 人情
                "人情" -> "人情"
                "份子钱" -> "人情"
                "红包" -> "红包"
                "礼物" -> "赠与"
                "随礼" -> "人情"
                "送礼" -> "赠与"
                "请客" -> "人情"
                // 烟酒
                "烟" -> "烟酒"
                "酒" -> "烟酒"
                "香烟" -> "烟酒"
                "白酒" -> "烟酒"
                "啤酒" -> "烟酒"
                "烟草" -> "烟酒"
                // 健身
                "健身" -> "健身"
                "瑜伽" -> "健身"
                "游泳" -> "运动"
                "跑步" -> "运动"
                // 投资理财
                "投资" -> "投资"
                "理财" -> "理财"
                "股票" -> "股票"
                "基金" -> "投资"
                "期货" -> "投资"
                "债券" -> "投资"
                "分红" -> "分红"
                "利息" -> "利息"
                // 保险
                "保险" -> "保险"
                "社保" -> "保险"
                "医保" -> "保险"
                // 退款
                "退款" -> "退款"
                "退货" -> "退款"
                // 生活服务
                "家政" -> "家政"
                "保洁" -> "家政"
                "维修" -> "维修"
                "修理" -> "维修"
                // 其他
                "罚款" -> "罚款"
                "滞纳金" -> "罚款"
                "手续费" -> "手续费"
                "服务费" -> "手续费"
                "捐赠" -> "捐赠"
                "捐款" -> "捐赠"
                else -> category
            }
            if (norm in valid) return NormalizedCategory(norm, true)
            // 分类标签专用别名表（精确匹配完整标签，可容纳「交通出行」这类在自由文本里高风险的高歧义词）
            val alias = CATEGORY_ALIASES[category]
            if (alias != null && alias in valid) return NormalizedCategory(alias, true)
            return NormalizedCategory("其他", false)
        }

        fun guessCategory(merchant: String, note: String, typeHint: String): String {
            val text = "$merchant $note $typeHint"
            return CategoryClassifier.guessCategory(text)
        }

        fun mapPaymentMethod(method: String): String {
            return when {
                method.contains("零钱") -> "CASH"
                method.contains("银行卡") || method.contains("储蓄卡") || method.contains("信用卡") -> "CARD"
                method.contains("微信") -> "WECHAT"
                method.contains("支付宝") -> "ALIPAY"
                else -> "OTHER"
            }
        }
    }

    private fun parseCsvLine(line: String, sep: Char = ','): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && !inQuotes -> inQuotes = true
                ch == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') { current.append('"'); i++ }
                    else inQuotes = false
                }
                ch == sep && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    enum class CsvFormat { WECHAT, ALIPAY, GENERIC, UNKNOWN }
}
