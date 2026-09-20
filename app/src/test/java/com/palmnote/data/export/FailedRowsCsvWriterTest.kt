package com.palmnote.data.export

import com.palmnote.data.export.BillCsvImporter.CsvFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T4 验证：失败记录导出的 CSV 能被 [BillCsvImporter] 读回（round-trip）。
 *
 * 工人声称：使用微信官方表头（含「交易时间 + 收/支 + 商品」）可保证 detectFormat 命中 WECHAT，
 * 而纯 GENERIC 表头会被失败行的原始内容里的品牌关键词（微信支付/支付宝）误判。
 */
class FailedRowsCsvWriterTest {

    private val importer = BillCsvImporter()

    private fun reasonLabel(reason: ImportFailureReason): String = when (reason) {
        ImportFailureReason.MISSING_AMOUNT -> "无法识别金额"
        ImportFailureReason.MISSING_DATE -> "缺少日期"
        ImportFailureReason.UNPARSEABLE -> "无法解析"
    }

    private fun headerLine(csv: String): String =
        csv.removePrefix("\uFEFF").lines().first { it.isNotBlank() }

    @Test
    fun `emitted header is present and BOM prefixed`() {
        val csv = FailedRowsCsvWriter.build(emptyList(), ::reasonLabel)
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals(
            "交易时间,交易类型,交易对方,商品,收/支,金额,备注,交易单号,原始内容,失败原因",
            headerLine(csv)
        )
    }

    @Test
    fun `failed csv is detected as WECHAT not UNKNOWN`() {
        val csv = FailedRowsCsvWriter.build(
            listOf(ImportFailure("2026-07-20 微信支付 支出 ¥12.34", ImportFailureReason.MISSING_AMOUNT)),
            ::reasonLabel
        )
        val lines = csv.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        val format = importer.detectFormat(lines)
        assertNotEquals(CsvFormat.UNKNOWN, format)
        assertEquals(CsvFormat.WECHAT, format)
    }

    @Test
    fun `failure raw text containing Alipay keyword still detected as WECHAT`() {
        val csv = FailedRowsCsvWriter.build(
            listOf(ImportFailure("支付宝 交易 对方已退款", ImportFailureReason.MISSING_DATE)),
            ::reasonLabel
        )
        val lines = csv.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        assertEquals(CsvFormat.WECHAT, importer.detectFormat(lines))
    }

    /**
     * 完整 round-trip：工人导出的 CSV → 用户补齐「交易时间 / 金额」两列 → 重新导入。
     * 断言解析出的账单与原始记录一致。
     */
    @Test
    fun `round trip filling amount and date yields the expected bill`() {
        val raw = "2026-07-20 12:30:00,商户消费,美团外卖,,支出,,微信支付,已支付,420000123456,商户单号"
        val csv = FailedRowsCsvWriter.build(
            listOf(ImportFailure(raw, ImportFailureReason.MISSING_AMOUNT)),
            ::reasonLabel
        )
        val lines = csv.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        assertEquals(CsvFormat.WECHAT, importer.detectFormat(lines))

        // 用户补齐前 8 列（表头规定的 交易时间/交易类型/交易对方/商品/收/支/金额/备注/交易单号 中的前 8）
        val dataLine = lines[1]
        // 导出时 0..7 列全空，故数据行以 8 个逗号开头
        assertTrue("data line should start with 8 empty columns: $dataLine", dataLine.startsWith(",,,,,,,,"))
        val filledPrefix = listOf(
            "2026-07-20 12:30:00", "商户消费", "美团外卖", "", "支出", "45.00", "", "420000123456"
        ).joinToString(",") + ","
        val filledLine = dataLine.replaceFirst(",,,,,,,,", filledPrefix)

        val fails = mutableListOf<ImportFailure>()
        val bills = importer.parseWithFailures(listOf(lines[0], filledLine), CsvFormat.WECHAT, null, fails)

        assertEquals("round-trip should yield exactly one bill, failures=$fails", 1, bills.size)
        val bill = bills[0]
        assertEquals(4500L, bill.amount)
        assertEquals("EXPENSE", bill.type)
        assertEquals("美团外卖", bill.merchant)
        assertEquals("420000123456", bill.transactionId)
    }

    /** 未补齐的失败行再次导入时应被再次收集为失败（不会静默丢失）。 */
    @Test
    fun `unfilled failed csv re-import collects them as failures again`() {
        val csv = FailedRowsCsvWriter.build(
            listOf(ImportFailure("2026-07-20 微信支付 支出 ¥12.34", ImportFailureReason.MISSING_AMOUNT)),
            ::reasonLabel
        )
        val lines = csv.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        val fails = mutableListOf<ImportFailure>()
        val bills = importer.parseWithFailures(lines, CsvFormat.WECHAT, null, fails)
        assertEquals(0, bills.size)
        assertEquals(1, fails.size)
    }

    /**
     * 反例（验证工人的取舍理由）：若失败 CSV 改用纯 GENERIC 表头，而原始内容含品牌关键词，
     * detectFormat 的品牌扫描会先命中，导致整个文件被误判、找不到表头、0 行导入。
     */
    @Test
    fun `generic-only header with brand keyword body would be mis-detected and import nothing`() {
        val genericHeader = "日期,金额,备注"
        val lines = listOf(genericHeader, "2026-07-20,12.34,微信支付退款")
        // 品牌关键词扫描（第二段循环）在 GENERIC 兜底之前命中 → 误判为 WECHAT
        assertEquals(CsvFormat.WECHAT, importer.detectFormat(lines))
        // 以 WECHAT 解析时找不到「交易时间」表头 → 0 行
        assertEquals(0, importer.parseFromLines(lines, CsvFormat.WECHAT).size)
    }

    @Test
    fun `escape wraps commas quotes and flattens newlines`() {
        val csv = FailedRowsCsvWriter.build(
            listOf(ImportFailure("a,b \"q\"\nnext", ImportFailureReason.UNPARSEABLE)),
            ::reasonLabel
        )
        assertTrue(csv.contains("\"a,b \"\"q\"\" next\""))
    }
}
