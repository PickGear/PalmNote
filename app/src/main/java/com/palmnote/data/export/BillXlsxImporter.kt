package com.palmnote.data.export

import android.content.Context
import com.palmnote.domain.model.BillType
import android.net.Uri
import com.palmnote.domain.util.AppLogger
import com.palmnote.domain.model.Money
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
            parseZipBytes(bytes, StringBuilder()).second
        } catch (_: Exception) { emptyList() }
    }

    fun parseBytes(bytes: ByteArray, diag: StringBuilder): List<ParsedBill> {
        return parseBytesWithFormat(bytes, diag).second
    }

    /** 解析并附带识别账单品牌（微信/支付宝），供导入预览显示格式标签与支付方式归属 */
    fun parseBytesWithFormat(bytes: ByteArray, diag: StringBuilder): Pair<BillCsvImporter.CsvFormat, List<ParsedBill>> {
        return try { parseZipBytes(bytes, diag) } catch (e: Exception) {
            diag.append("异常: ${e.message}\n")
            BillCsvImporter.CsvFormat.UNKNOWN to emptyList()
        }
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

    private fun parseZipBytes(bytes: ByteArray, diag: StringBuilder): Pair<BillCsvImporter.CsvFormat, List<ParsedBill>> {
        val rows = readSheetRows(bytes, diag)
        if (rows.isEmpty()) return BillCsvImporter.CsvFormat.UNKNOWN to emptyList()

        val layout = detectSheetLayout(rows, diag) ?: return BillCsvImporter.CsvFormat.UNKNOWN to emptyList()
        val bills = rows.drop(layout.headerRowIdx + 1).mapNotNull { cols ->
            try { parseDataRow(cols, layout) } catch (_: Exception) { null }
        }
        diag.append("有效记录: ${bills.size}条\n")
        if (bills.isEmpty()) {
            rows.drop(layout.headerRowIdx + 1).firstOrNull()?.let {
                diag.append("首行数据: ${it.joinToString(" | ").take(200)}\n")
            }
        }
        return layout.format to bills
    }

    /** 读取 ZIP 内工作表（含 shared strings 解引用），输出诊断信息 */
    private fun readSheetRows(bytes: ByteArray, diag: StringBuilder): List<List<String>> {
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
        return rows
    }

    /** 表头行定位、品牌识别与列索引映射（微信/支付宝两种官方格式） */
    private fun detectSheetLayout(rows: List<List<String>>, diag: StringBuilder): SheetLayout? {
        val headerRowIdx = rows.indexOfFirst { row ->
            row.any { it.contains("交易时间") || it.contains("交易创建时间") }
        }
        diag.append("表头行: ${if (headerRowIdx >= 0) "第${headerRowIdx + 1}行" else "未找到"}\n")
        if (headerRowIdx < 0) return null

        val headerIdx = rows[headerRowIdx]
            .mapIndexed { ci, h -> h.trim() to ci }
            .toMap()
        diag.append("匹配列: ${headerIdx.keys.joinToString(", ")}\n")

        // 品牌识别：微信表头有"支付方式"；支付宝表头有"资金状态/交易号"（新旧版均无支付方式列）
        val format = when {
            headerIdx.keys.any { it.contains("支付方式") } -> BillCsvImporter.CsvFormat.WECHAT
            headerIdx.keys.any { it.contains("资金状态") || it.contains("交易号") } -> BillCsvImporter.CsvFormat.ALIPAY
            else -> BillCsvImporter.CsvFormat.UNKNOWN
        }
        diag.append("品牌: $format\n")

        return SheetLayout(
            format = format,
            headerRowIdx = headerRowIdx,
            dateIdx = findCol(headerIdx, "交易时间") ?: findCol(headerIdx, "交易创建时间")
                ?: findCol(headerIdx, "时间") ?: findCol(headerIdx, "日期") ?: 0,
            typeIdx = findCol(headerIdx, "交易类型"),
            merchantIdx = findCol(headerIdx, "交易对方"),
            goodsIdx = findCol(headerIdx, "商品"),
            ieIdx = findCol(headerIdx, "收/支"),
            amountIdx = findCol(headerIdx, "金额"),
            methodIdx = findCol(headerIdx, "支付方式"),
            noteIdx = findCol(headerIdx, "备注"),
            txIdIdx = findCol(headerIdx, "交易单号") ?: findCol(headerIdx, "交易号")
        )
    }

    private fun parseDataRow(cols: List<String>, layout: SheetLayout): ParsedBill? {
        // 状态过滤已移除（issue#1：白名单遗漏"已存入零钱"等真实状态导致大面积丢行），
        // 全部行进入预览由用户勾选
        val date = parseXlsxDate(normalizeTime(cellOf(cols, layout.dateIdx))) ?: return null
        val amount = Money.parse(cleanAmountText(cellOf(cols, layout.amountIdx)))?.cents ?: return null
        val merchant = cellOf(cols, layout.merchantIdx)
        val typeStr = cellOf(cols, layout.typeIdx)
        val method = cellOf(cols, layout.methodIdx)
        // 备注常为空，商品名承载消费内容（分类推断的重要信号），回退后再参与推断
        val noteOrGoods = cellOf(cols, layout.noteIdx).ifEmpty { cellOf(cols, layout.goodsIdx) }
        val isIncome = cellOf(cols, layout.ieIdx).contains("收入")

        return ParsedBill(
            date = date,
            type = if (isIncome) BillType.INCOME.value else BillType.EXPENSE.value,
            amount = amount,
            category = BillCsvImporter.normalizeCategory(
                BillCsvImporter.guessCategory(merchant, noteOrGoods, typeStr),
                if (isIncome) BillType.INCOME.value else BillType.EXPENSE.value
            ),
            merchant = merchant,
            note = noteOrGoods.ifEmpty { typeStr },
            // 支付宝表头无支付方式列，按品牌归属而非落到 OTHER
            paymentMethod = if (layout.format == BillCsvImporter.CsvFormat.ALIPAY) {
                "ALIPAY"
            } else {
                BillCsvImporter.mapPaymentMethod(method)
            },
            // 交易单号是最可靠去重键（此前 xlsx 不读该列，只能靠属性匹配去重）
            transactionId = cellOf(cols, layout.txIdIdx)
        )
    }

    private fun cellOf(cols: List<String>, idx: Int?): String = idx?.let { cols.getOrNull(it)?.trim() } ?: ""

    private fun normalizeTime(timeStr: String): String = timeStr.replace("T", " ").replace("Z", "")

    private fun cleanAmountText(raw: String): String = raw.replace(",", "")
        .replace("¥", "").replace("￥", "")
        .replace(" ", "").replace("+", "").replace("-", "")

    @Suppress("LongParameterList")
    private class SheetLayout(
        val format: BillCsvImporter.CsvFormat,
        val headerRowIdx: Int,
        val dateIdx: Int,
        val typeIdx: Int?,
        val merchantIdx: Int?,
        val goodsIdx: Int?,
        val ieIdx: Int?,
        val amountIdx: Int?,
        val methodIdx: Int?,
        val noteIdx: Int?,
        val txIdIdx: Int?
    )

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
        } catch (e: Exception) { AppLogger.w("XlsxImport", "parseSharedStrings failed", e) }
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
        } catch (e: Exception) { AppLogger.w("XlsxImport", "parseSheet failed", e) }
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
        for (pat in listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
            "yyyy/M/d HH:mm:ss", "yyyy/M/d HH:mm", "yyyy-MM-dd", "yyyy/MM/dd", "yyyy/M/d", "yyyy年M月d日"
        )) {
            try { return SimpleDateFormat(pat, Locale.getDefault()).parse(clean)?.time } catch (e: Exception) { AppLogger.w("XlsxImport", "parseXlsxDate failed for pattern: $pat", e) }
        }
        val num = clean.toDoubleOrNull()
        if (num != null && num > 30000 && num < 80000) {
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
