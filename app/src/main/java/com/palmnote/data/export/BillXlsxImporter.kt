package com.palmnote.data.export

import android.content.Context
import android.net.Uri
import com.palmnote.domain.util.DateUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipInputStream

class BillXlsxImporter {

    fun parse(context: Context, uri: Uri): List<ParsedBill> {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { readAllBytes(it) } ?: return emptyList()
            parseZipBytes(bytes, StringBuilder())
        } catch (_: Exception) { emptyList() }
    }

    fun parseBytes(bytes: ByteArray, diag: StringBuilder): List<ParsedBill> {
        return try { parseZipBytes(bytes, diag) } catch (e: Exception) { diag.append("异常: ${e.message}\n"); emptyList() }
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        var len: Int
        while (input.read(tmp).also { len = it } != -1) {
            buf.write(tmp, 0, len)
        }
        return buf.toByteArray()
    }

    private fun parseZipBytes(bytes: ByteArray, diag: StringBuilder): List<ParsedBill> {
        val entries = mutableMapOf<String, ByteArray>()
        val zis = ZipInputStream(bytes.inputStream())
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) entries[entry.name] = readAllBytes(zis)
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
        diag.append("ZIP条目: ${entries.keys.joinToString(", ")}\n")

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()
        diag.append("共享字符串: ${sharedStrings.size}个\n")

        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries["xl/worksheets/sheet.xml"]
            ?: entries.entries.firstOrNull { it.key.endsWith(".xml") && it.key.contains("sheet", ignoreCase = true) }?.value
        diag.append("工作表: ${sheetBytes != null}\n")
        val rows = sheetBytes?.let { parseSheet(it.inputStream(), sharedStrings) } ?: emptyList()
        diag.append("行数: ${rows.size}\n")
        if (rows.isNotEmpty()) diag.append("表头: ${rows.first().joinToString(" | ")}\n")

        if (rows.isEmpty()) return emptyList()

        val headerIdx = mutableMapOf<String, Int>()
        var headerRowIdx = -1
        for ((i, row) in rows.withIndex()) {
            if (row.any { it.contains("交易时间") }) {
                row.forEachIndexed { ci, h -> headerIdx[h.trim()] = ci }
                headerRowIdx = i
                break
            }
        }
        diag.append("表头行: ${if (headerRowIdx >= 0) "第${headerRowIdx + 1}行" else "未找到"}\n")
        diag.append("匹配列: ${headerIdx.keys.joinToString(", ")}\n")
        if (headerRowIdx < 0) return emptyList()

        val dateIdx = findCol(headerIdx, "交易时间") ?: 0
        val typeIdx = findCol(headerIdx, "交易类型")
        val merchantIdx = findCol(headerIdx, "交易对方")
        val goodsIdx = findCol(headerIdx, "商品")
        val ieIdx = findCol(headerIdx, "收/支")
        val amountIdx = findCol(headerIdx, "金额")
        val methodIdx = findCol(headerIdx, "支付方式")
        val statusIdx = findCol(headerIdx, "状态")
        val noteIdx = findCol(headerIdx, "备注")

        return rows.drop(headerRowIdx + 1).mapNotNull { cols ->
            try {
                val timeStr = cols.getOrNull(dateIdx)?.trim()?.replace("T", " ")?.replace("Z", "") ?: return@mapNotNull null
                if (timeStr.isBlank()) return@mapNotNull null
                val amountStr = amountIdx?.let { cols.getOrNull(it)?.trim() } ?: return@mapNotNull null
                val cleanAmount = amountStr.replace(",", "").replace("¥", "").replace("￥", "").replace(" ", "")
                val amount = cleanAmount.toDoubleOrNull() ?: return@mapNotNull null
                val status = statusIdx?.let { cols.getOrNull(it)?.trim() } ?: ""
                if (status.isNotEmpty() && status !in listOf("已支付", "支付成功", "已到账", "已收钱", "对方已收钱")) return@mapNotNull null
                val merchant = merchantIdx?.let { cols.getOrNull(it)?.trim() } ?: ""
                val goodsDesc = goodsIdx?.let { cols.getOrNull(it)?.trim() } ?: ""
                val typeStr = typeIdx?.let { cols.getOrNull(it)?.trim() } ?: ""
                val incomeExpense = ieIdx?.let { cols.getOrNull(it)?.trim() } ?: ""
                val method = methodIdx?.let { cols.getOrNull(it)?.trim() } ?: ""
                val note = noteIdx?.let { cols.getOrNull(it)?.trim() } ?: ""
                val isIncome = incomeExpense.contains("收入")
                val date = parseXlsxDate(timeStr) ?: return@mapNotNull null

                ParsedBill(
                    date = date,
                    type = if (isIncome) "INCOME" else "EXPENSE",
                    amount = amount,
                    category = BillCsvImporter.normalizeCategory(BillCsvImporter.guessCategory(merchant, note, typeStr), if (isIncome) "INCOME" else "EXPENSE"),
                    merchant = merchant,
                    note = note.ifEmpty { goodsDesc.ifEmpty { typeStr } },
                    paymentMethod = BillCsvImporter.mapPaymentMethod(method)
                )
            } catch (_: Exception) { null }
        }.also { result ->
            diag.append("有效记录: ${result.size}条\n")
            if (result.isEmpty()) {
                val sampleRow = rows.drop(headerRowIdx + 1).firstOrNull()
                if (sampleRow != null) diag.append("首行数据: ${sampleRow.joinToString(" | ").take(200)}\n")
            }
        }
    }

    private fun findCol(headerIdx: Map<String, Int>, keyword: String): Int? {
        return headerIdx.entries.firstOrNull { it.key.contains(keyword) }?.value
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val result = mutableListOf<String>()
        try {
            val parser = newParser(input)
            var inSi = false
            var buf = StringBuilder()
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> if (tag(parser) == "si") { inSi = true; buf = StringBuilder() }
                    XmlPullParser.TEXT -> if (inSi) buf.append(parser.text)
                    XmlPullParser.END_TAG -> if (tag(parser) == "si") { result.add(buf.toString()); inSi = false }
                }
                parser.next()
            }
        } catch (_: Exception) { }
        return result
    }

    private fun parseSheet(input: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rawRows = mutableListOf<Map<Int, String>>()
        try {
            val parser = newParser(input)
            var currentRow = mutableMapOf<Int, String>()
            var buf = StringBuilder()
            var inCell = false
            var inV = false
            var inIs = false
            var cellType: String? = null
            var cellCol = 0

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (tag(parser)) {
                        "row" -> currentRow = mutableMapOf()
                        "c" -> {
                            inCell = true
                            cellType = parser.getAttributeValue(null, "t")
                            inIs = false
                            cellCol = colIndex(parser.getAttributeValue(null, "r") ?: "A")
                        }
                        "v" -> if (inCell) inV = true
                        "is" -> if (inCell) inIs = true
                    }
                    XmlPullParser.TEXT -> { if (inV || inIs) buf.append(parser.text) }
                    XmlPullParser.END_TAG -> when (tag(parser)) {
                        "c" -> {
                            val raw = buf.toString()
                            val value = when (cellType) {
                                "s" -> raw.toIntOrNull()?.let { if (it < sharedStrings.size) sharedStrings[it] else "" } ?: ""
                                "str", "inlineStr" -> raw
                                else -> raw
                            }
                            if (value.isNotBlank()) currentRow[cellCol] = value
                            buf.clear(); inCell = false; cellType = null; inIs = false
                        }
                        "v" -> inV = false
                        "is" -> inIs = false
                        "row" -> if (currentRow.isNotEmpty()) rawRows.add(currentRow.toMap())
                    }
                }
                parser.next()
            }
        } catch (_: Exception) { }
        if (rawRows.isEmpty()) return emptyList()
        val globalMax = rawRows.maxOf { it.keys.max() }
        return rawRows.map { row -> (0..globalMax).map { row[it] ?: "" } }
    }

    private fun newParser(input: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")
        return parser
    }

    private fun tag(parser: XmlPullParser) = parser.name.substringAfter(':')

    private fun colIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        return letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
    }

    private fun parseXlsxDate(timeStr: String): Long? {
        val clean = timeStr.trim()
        for (pat in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")) {
            try { return SimpleDateFormat(pat, Locale.getDefault()).parse(clean)?.time } catch (_: Exception) { }
        }
        val num = clean.toDoubleOrNull()
        if (num != null && num > 40000 && num < 60000) {
            return excelSerialToMillis(num)
        }
        return null
    }

    private fun excelSerialToMillis(serial: Double): Long {
        val days = serial.toInt()
        val dayMs = (days - 25569L) * DateUtils.MILLIS_PER_DAY
        val fraction = serial - days
        val fracMs = (fraction * DateUtils.MILLIS_PER_DAY).toLong()
        return dayMs + fracMs
    }
}
