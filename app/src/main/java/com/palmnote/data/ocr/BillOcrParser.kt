package com.palmnote.data.ocr

import com.palmnote.data.export.BillCsvImporter
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
            "商户", "商家", "收款方", "对方", "门店", "店铺", "公司",
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

        for (line in lines) {
            val clean = line.replace(" ", "").replace("　", "")
            if (clean.length in 2..30
                && !clean.any { it in "0123456789¥￥%./-+:*#@!&" }
                && !clean.contains("支出") && !clean.contains("收入")
                && !clean.contains("交易") && !clean.contains("账单")
                && !clean.contains("支付") && !clean.contains("完成")
                && !clean.contains("时间") && !clean.contains("状态")
            ) {
                return clean
            }
        }
        return ""
    }

    private fun findDate(lines: List<String>): Long? {
        for (line in lines) {
            for (pat in DATE_PATTERNS) {
                val m = pat.matcher(line)
                if (m.find()) {
                    val dateStr = m.group(1)!!
                        .replace("年", "-").replace("月", "-").replace("日", "")
                        .replace("/", "-").replace(".", "-")
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
                    val dateStr = m.group(1)!!.replace("/", "-").replace(".", "-")
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
            && !it.contains("支出") && !it.contains("收入")
            && !it.contains("交易") && !it.contains("支付")
            && !it.contains("时间") && !it.contains("状态")
        }
        return candidates.firstOrNull() ?: ""
    }

    private fun guessCategory(lines: List<String>, merchant: String, note: String): String {
        val text = lines.joinToString(" ") + " " + merchant + " " + note
        val raw = CategoryClassifier.guessCategory(text)
        return BillCsvImporter.normalizeCategory(raw, "EXPENSE")
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
