package com.palmnote.data.export

import android.util.Log
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
    val transactionId: String = ""
)

class BillCsvImporter {

    fun detectFormat(lines: List<String>): CsvFormat {
        for (line in lines) {
            val clean = line.trimStart('\uFEFF').trim()
            if (clean.contains("记录时间") && clean.contains("收支")) return CsvFormat.ALIPAY
            if (clean.contains("交易时间") && clean.contains("收/支")) {
                if (clean.contains("商品说明") || clean.contains("交易分类")) return CsvFormat.ALIPAY
                if (clean.contains("商品") || clean.contains("交易对方") || clean.contains("交易类型")) return CsvFormat.WECHAT
            }
        }
        for (line in lines) {
            if (line.contains("微信支付") || line.contains("微信账单")) return CsvFormat.WECHAT
            if (line.contains("支付宝") || line.contains("Alipay")) return CsvFormat.ALIPAY
        }
        return CsvFormat.UNKNOWN
    }

    fun parseFromLines(lines: List<String>, format: CsvFormat, diag: StringBuilder? = null): List<ParsedBill> {
        val headerLine = when (format) {
            CsvFormat.ALIPAY -> lines.firstOrNull {
                it.contains("记录时间") || (it.contains("交易时间") && it.contains("收支"))
            }
            else -> lines.firstOrNull {
                it.contains("交易时间") && (it.contains("收/支") || it.contains("金额"))
            }
        }
        diag?.append("CSV表头行: ${if (headerLine != null) headerLine.take(80) else "未找到"}\n")
        if (headerLine == null) return emptyList()
        val sep = detectSeparator(headerLine)
        val headerCols = parseCsvLine(headerLine, sep)
        val headerIdx = headerCols.mapIndexed { i, h -> h.trim() to i }.toMap()
        val headerLineIdx = lines.indexOf(headerLine)
        val dataLines = lines.drop(headerLineIdx + 1).filter {
            it.isNotBlank() && !it.startsWith("---") && !it.contains("合计") && !it.contains("本笔")
        }

        return when (format) {
            CsvFormat.WECHAT -> parseWechat(dataLines, headerIdx, sep)
            CsvFormat.ALIPAY -> parseAlipay(dataLines, headerIdx, sep)
            CsvFormat.UNKNOWN -> emptyList()
        }
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

    private fun parseWechat(lines: List<String>, headerIdx: Map<String, Int>, sep: Char): List<ParsedBill> {
        val dateIdx = col(headerIdx, "交易时间")
        val typeIdx = col(headerIdx, "交易类型")
        val merchantIdx = col(headerIdx, "交易对方")
        val goodsIdx = col(headerIdx, "商品")
        val ieIdx = col(headerIdx, "收/支")
        val amountIdx = col(headerIdx, "金额")
        val methodIdx = col(headerIdx, "支付方式")
        val statusIdx = col(headerIdx, "状态")
        val noteIdx = col(headerIdx, "备注")
        val txIdIdx = col(headerIdx, "交易单号")

        return lines.mapNotNull { line ->
            try {
                val cols = parseCsvLine(line, sep)
                val timeStr = cell(cols, dateIdx).ifBlank { return@mapNotNull null }
                val amountStr = cell(cols, amountIdx).ifBlank { return@mapNotNull null }
                val cleanAmount = amountStr.replace(",", "").replace("¥", "").replace("￥", "").replace(" ", "").replace("+", "").replace("-", "")
                val amount = Money.parse(cleanAmount)?.cents ?: return@mapNotNull null
                val status = cell(cols, statusIdx)
                if (status.isNotBlank() && status !in listOf("已支付", "支付成功", "已到账", "已收钱")) return@mapNotNull null
                val date = parseDate(timeStr) ?: return@mapNotNull null
                val isIncome = cell(cols, ieIdx).contains("收入")

                ParsedBill(
                    date = date,
                    type = if (isIncome) "INCOME" else "EXPENSE",
                    amount = amount,
                    category = normalizeCategory(guessCategory(cell(cols, merchantIdx), cell(cols, noteIdx), cell(cols, typeIdx)), if (isIncome) "INCOME" else "EXPENSE"),
                    merchant = cell(cols, merchantIdx),
                    note = cell(cols, noteIdx).ifBlank { cell(cols, goodsIdx).ifBlank { cell(cols, typeIdx) } },
                    paymentMethod = mapPaymentMethod(cell(cols, methodIdx)),
                    transactionId = cell(cols, txIdIdx)
                )
            } catch (_: Exception) { null }
        }
    }

    private fun parseAlipay(lines: List<String>, headerIdx: Map<String, Int>, sep: Char): List<ParsedBill> {
        val dateIdx = col(headerIdx, "记录时间") ?: col(headerIdx, "交易时间")
        val categoryIdx = col(headerIdx, "分类") ?: col(headerIdx, "交易分类")
        val merchantIdx = col(headerIdx, "交易对方") ?: col(headerIdx, "商品说明")
        val ieIdx = col(headerIdx, "收支类型") ?: col(headerIdx, "收/支")
        val amountIdx = col(headerIdx, "金额")
        val noteIdx = col(headerIdx, "备注")
        val accountIdx = col(headerIdx, "账户")

        return lines.mapNotNull { line ->
            try {
                val cols = parseCsvLine(line, sep)
                val timeStr = cell(cols, dateIdx).ifBlank { return@mapNotNull null }
                val amountStr = cell(cols, amountIdx).ifBlank { return@mapNotNull null }
                val cleanAmount = amountStr.replace(",", "").replace("¥", "").replace("￥", "").replace(" ", "").replace("+", "").replace("-", "")
                val amount = Money.parse(cleanAmount)?.cents ?: return@mapNotNull null
                val ieType = cell(cols, ieIdx)
                val merchant = cell(cols, merchantIdx)
                val note = cell(cols, noteIdx).ifBlank { cell(cols, accountIdx) }
                val category = cell(cols, categoryIdx)
                val date = parseDate(timeStr) ?: return@mapNotNull null
                val isIncome = ieType.contains("收入")

                ParsedBill(
                    date = date,
                    type = if (isIncome) "INCOME" else "EXPENSE",
                    amount = amount,
                    category = if (category.isNotBlank()) normalizeCategory(category, if (isIncome) "INCOME" else "EXPENSE") else normalizeCategory(guessCategory(merchant, note, ""), if (isIncome) "INCOME" else "EXPENSE"),
                    merchant = merchant,
                    note = note,
                    paymentMethod = "ALIPAY"
                )
            } catch (_: Exception) { null }
        }
    }

    private fun parseDate(timeStr: String): Long? {
        val clean = timeStr.trim()
        for (pat in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy-MM-dd", "yyyy/MM/dd")) {
            try {
                val parsed = SimpleDateFormat(pat, Locale.getDefault()).parse(clean)
                if (parsed != null) return parsed.time
            } catch (e: Exception) { Log.w("CsvImport", "parseDate failed", e) }
        }
        return null
    }

    companion object {
        private val EXPENSE_CATEGORIES = setOf("餐饮", "零食", "饮品", "交通", "购物", "服饰", "数码", "二手", "居住", "家居", "租金", "娱乐", "旅游", "运动", "医疗", "健身", "美容", "教育", "文具", "社交", "人情", "红包", "赠与", "通讯", "家政", "快递", "维修", "投资", "股票", "理财", "保险", "宠物", "母婴", "烟酒", "捐赠", "罚款", "手续费", "其他")
        private val INCOME_CATEGORIES = setOf("工资", "奖金", "兼职", "副业", "报销", "投资", "股票", "理财", "分红", "利息", "租金", "二手", "红包", "赠与", "人情", "退款", "中奖", "保险理赔", "继承", "其他")

        fun normalizeCategory(category: String, type: String): String {
            val valid = if (type == "EXPENSE") EXPENSE_CATEGORIES else INCOME_CATEGORIES
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
            return if (norm in valid) norm else "其他"
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

    enum class CsvFormat { WECHAT, ALIPAY, UNKNOWN }
}
