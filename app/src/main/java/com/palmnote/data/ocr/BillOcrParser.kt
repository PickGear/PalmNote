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
    val category: String = "其他"
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
        return OcrBillResult(amount = amount, merchant = merchant, date = date, note = note, category = category)
    }

    fun parseMultiple(text: String): List<OcrBillResult> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (line in lines) {
            if (isNewTransaction(line, current)) {
                if (current.isNotEmpty()) blocks.add(current)
                current = mutableListOf()
            }
            current.add(line)
        }
        if (current.isNotEmpty()) blocks.add(current)

        if (blocks.size <= 1) return listOf(parse(text))

        return blocks.map { block ->
            val amount = findAmount(block)
            val merchant = findMerchant(block)
            val date = findDate(block)
            val note = findNote(block, merchant)
            val category = guessCategory(block, merchant, note)
            OcrBillResult(amount = amount, merchant = merchant, date = date, note = note, category = category)
        }.filter { (it.amount ?: 0) > 0 }
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

    private fun findAmount(lines: List<String>): Long? {
        val candidates = mutableListOf<Double>()

        for (line in lines) {
            val m = AMOUNT_PATTERN.matcher(line)
            while (m.find()) {
                val v = m.group(1)?.toDoubleOrNull()
                if (v != null && v > 0) candidates.add(v)
            }
        }

        if (candidates.isEmpty()) {
            for (line in lines) {
                val m = LOOSE_AMOUNT.matcher(line)
                while (m.find()) {
                    val v = m.group(1)?.toDoubleOrNull()
                    if (v != null && v > 0) candidates.add(v)
                }
            }
        }

        val best = candidates.maxOrNull() ?: return null
        return Money.fromYuan(best).cents
    }

    private fun findMerchant(lines: List<String>): String {
        val merchantKeywords = listOf(
            "商户", "商家", "收款方", "收款单位", "付款方", "对方", "门店", "店铺", "公司",
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

    private fun isCleanMerchantCandidate(line: String): Boolean {
        val clean = line.replace(" ", "").replace("　", "")
        if (clean.length !in 2..30) return false
        // 含金额符号/纯标点的行不是商户
        if (clean.any { it in "¥￥%*#@!&=" }) return false
        // 页面装饰词（支付截图的标题/状态/按钮文字）
        val decorationWords = listOf(
            "支出", "收入", "交易", "账单", "支付", "完成", "时间", "状态", "成功",
            "凭证", "详情", "收款", "付款码", "二维码", "零钱", "余额", "钱包", "明细",
            "小票", "收据", "订单", "编号", "单号", "金额", "备注", "当前", "退款",
            "微信", "支付宝", "银行", "余额宝", "银行卡"
        )
        if (decorationWords.any { clean.contains(it) }) return false
        // 至少含 2 个汉字（纯数字/单符号行不是商户）
        if (clean.count { it.code in 0x4E00..0x9FFF } < 2) return false
        return true
    }

    private fun findDate(lines: List<String>): Long? {
        for (line in lines) {
            for (pat in DATE_PATTERNS) {
                val m = pat.matcher(line)
                if (m.find()) {
                    val dateStr = m.group(1)?.let {
                        it.replace("年", "-").replace("月", "-").replace("日", "")
                            .replace("/", "-").replace(".", "-")
                    } ?: continue
                    try {
                        return java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-M-d")).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (_: Exception) { }
                }
            }
        }
        for (line in lines) {
            for (pat in LOOSE_DATE_PATTERNS) {
                val m = pat.matcher(line)
                if (m.find()) {
                    val dateStr = m.group(1)?.replace("/", "-")?.replace(".", "-") ?: continue
                    try {
                        return java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-M-d")).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (_: Exception) { }
                }
            }
        }
        return null
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
            it.length in 3..60
                && it != merchant
                && !it.any { c -> c in "¥￥%/*-+@#" }
                && !DATE_PATTERNS.any { p -> p.matcher(it).find() }
                && !AMOUNT_PATTERN.matcher(it).find()
                && it.replace(" ", "").let { c ->
                    !c.contains("支出") && !c.contains("收入") && !c.contains("交易") &&
                        !c.contains("支付") && !c.contains("时间") && !c.contains("状态") &&
                        !c.contains("成功") && !c.contains("凭证") && !c.contains("详情") &&
                        !c.contains("账单") && !c.contains("商户")
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
            Pattern.compile("(\\d{1,2}[-月]\\d{1,2}[日]?)")
        )
        private val LOOSE_DATE_PATTERNS = listOf(
            Pattern.compile("(\\d{4}\\d{2}\\d{2})")
        )
    }
}
