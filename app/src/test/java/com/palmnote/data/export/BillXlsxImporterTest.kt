package com.palmnote.data.export

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BillXlsxImporterTest {

    private val importer = BillXlsxImporter()

    @Test
    fun `wechat-style sheet round trips all bill fields`() {
        val shared = listOf(
            "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
            "支付方式", "当前状态", "交易单号", "商户单号", "备注",
            "2026-07-20 12:30:00", "商户消费", "美团外卖", "外卖", "支出",
            "微信支付", "已支付", "420000123456", "午饭"
        )
        val xlsx = buildXlsx(
            shared,
            sheetXml(listOf(
                row(1, listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10).map { sharedCell(it, it, 1) }),
                row(2, listOf(
                    sharedCell(0, 11, 2), sharedCell(1, 12, 2), sharedCell(2, 13, 2), sharedCell(3, 14, 2),
                    sharedCell(4, 15, 2), numCell(5, "45"), sharedCell(6, 16, 2), sharedCell(7, 17, 2),
                    sharedCell(8, 18, 2), "", sharedCell(10, 19, 2)
                ))
            ))
        )

        val bills = importer.parseBytes(xlsx, StringBuilder())

        assertEquals(1, bills.size)
        val bill = bills[0]
        assertEquals(parseDate("2026-07-20 12:30:00"), bill.date)
        assertEquals("EXPENSE", bill.type)
        assertEquals(4500L, bill.amount)
        assertEquals("美团外卖", bill.merchant)
        assertEquals("午饭", bill.note)
        assertEquals("WECHAT", bill.paymentMethod)
        // 交易单号列现在会被读取并用于精确去重
        assertEquals("420000123456", bill.transactionId)
    }

    @Test
    fun `inline string cells flag and raw number cells are supported`() {
        val xlsx = buildXlsx(
            listOf(),
            sheetXml(listOf(
                row(1, listOf(
                    inlineCell(0, "交易时间", 1), inlineCell(1, "交易类型", 1),
                    inlineCell(2, "交易对方", 1), inlineCell(3, "商品", 1),
                    inlineCell(4, "收/支", 1), inlineCell(5, "金额(元)", 1),
                    inlineCell(6, "支付方式", 1), inlineCell(7, "当前状态", 1)
                )),
                row(2, listOf(
                    inlineCell(0, "2026-07-22 10:00:00", 2),
                    inlineCell(2, "地铁", 2),
                    inlineCell(4, "支出", 2), numCell(5, "5.5"),
                    inlineCell(6, "零钱", 2), inlineCell(7, "已支付", 2)
                ))
            ))
        )

        val bills = importer.parseBytes(xlsx, StringBuilder())

        assertEquals(1, bills.size)
        val bill = bills.single()
        assertEquals(parseDate("2026-07-22 10:00:00"), bill.date)
        assertEquals(550L, bill.amount)
        assertEquals("地铁", bill.merchant)
        assertEquals("CASH", bill.paymentMethod)
    }

    @Test
    fun `excel serial date is converted to epoch millis`() {
        val serial = 45840 // 2025-07-02 (UTC)
        val xlsx = buildXlsx(
            listOf("交易时间", "收/支", "金额"),
            sheetXml(listOf(
                row(1, listOf(
                    sharedCell(0, 0, 1), inlineCell(1, "收/支", 1), inlineCell(2, "金额", 1)
                )),
                row(2, listOf(
                    numCell(0, serial.toString()), inlineCell(1, "收入", 2), numCell(2, "88.60")
                ))
            ))
        )

        val bills = importer.parseBytes(xlsx, StringBuilder())

        assertEquals(1, bills.size)
        val bill = bills.single()
        assertEquals(8860L, bill.amount)
        assertEquals("INCOME", bill.type)
        // 45840 - 25569 = 20271 days after Unix epoch (2025-07-02 UTC)
        assertEquals(20271L * 86_400_000L, bill.date)
    }

    @Test
    fun `empty bytes return empty list without crash`() {
        assertEquals(0, importer.parseBytes(ByteArray(0), StringBuilder()).size)
    }

    @Test
    fun `alipay-style sheet is detected as alipay brand with ALIPAY payment method`() {
        // 支付宝当代官方导出：无"支付方式"列，有"交易创建时间/资金状态"
        val headers = listOf(
            "交易号", "商家订单号", "交易创建时间", "付款时间", "最近修改时间", "交易来源地",
            "类型", "交易对方", "商品名称", "金额", "收/支", "交易状态", "备注", "资金状态"
        )
        val data = listOf(
            "2026093101", "2026093102", "2026-09-09 18:57:11", "2026-09-09 19:10:11",
            "2026-09-09 19:10:11", "中国", "即时到账交易", "肯德基", "汉堡套餐",
            "支出", "交易成功", "", "已支出"
        )
        // 共享字符串索引 0-13 为表头，14-26 为数据行
        val xlsx = buildXlsx(
            headers + data,
            sheetXml(listOf(
                row(1, headers.indices.map { sharedCell(it, it, 1) }),
                row(2, listOf(
                    sharedCell(0, 14, 2), sharedCell(1, 15, 2), sharedCell(2, 16, 2), sharedCell(3, 17, 2),
                    sharedCell(4, 18, 2), sharedCell(5, 19, 2), sharedCell(6, 20, 2), sharedCell(7, 21, 2),
                    sharedCell(8, 22, 2), numCell(9, "4.56"), sharedCell(10, 23, 2), sharedCell(11, 24, 2),
                    sharedCell(12, 25, 2), sharedCell(13, 26, 2)
                ))
            ))
        )

        val (format, bills) = importer.parseBytesWithFormat(xlsx, StringBuilder())
        assertEquals(BillCsvImporter.CsvFormat.ALIPAY, format)
        assertEquals(1, bills.size)
        assertEquals("ALIPAY", bills.single().paymentMethod)
        assertEquals("EXPENSE", bills.single().type)
        assertEquals(456L, bills.single().amount)
        assertEquals("2026093101", bills.single().transactionId)
    }

    // ── xlsx builders ──

    private fun buildXlsx(shared: List<String>, sheetXml: String): ByteArray {
        val sst = StringBuilder()
        sst.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sst.append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${shared.size}\">")
        for (text in shared) sst.append("<si><t>$text</t></si>")
        sst.append("</sst>")

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(sst.toString().toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetXml.toByteArray())
            zos.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun sheetXml(rows: List<String>): String {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" +
            rows.joinToString("") +
            "</sheetData></worksheet>"
    }

    private fun row(r: Int, cells: List<String>): String = "<row r=\"$r\">" + cells.joinToString("") + "</row>"

    private fun cell(ref: String, type: String?, body: String): String {
        val t = if (type == null) "" else " t=\"$type\""
        return "<c r=\"$ref\"$t>$body</c>"
    }

    private fun sharedCell(colIdx: Int, sharedIdx: Int, rowNum: Int): String =
        cell(colLetter(colIdx) + rowNum, "s", "<v>$sharedIdx</v>")

    private fun inlineCell(colIdx: Int, text: String, rowNum: Int): String =
        if (text.isEmpty()) "" else cell(colLetter(colIdx) + rowNum, "inlineStr", "<is><t>$text</t></is>")

    private fun numCell(colIdx: Int, raw: String): String =
        cell(colLetter(colIdx) + "2", null, "<v>$raw</v>")

    private fun colLetter(i: Int): String {
        val sb = StringBuilder()
        var n = i
        while (n >= 0) {
            sb.append('A' + n % 26)
            n = n / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun parseDate(s: String): Long =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s)!!.time
}
