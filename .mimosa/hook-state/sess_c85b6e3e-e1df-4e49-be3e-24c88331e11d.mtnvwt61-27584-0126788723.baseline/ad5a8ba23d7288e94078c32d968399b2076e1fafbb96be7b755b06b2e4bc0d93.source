package com.palmnote.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class BillOcrParserTest {

    private val parser = BillOcrParser()

    @Test
    fun `synthetic wechat receipt extracts amount merchant date note category`() {
        val text = """
            微信支付凭证
            交易时间：2025-08-03 12:34:56
            商户：老王面馆
            商品：牛肉面
            支付方式：零钱
            金额：¥23.50
            支付成功
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(2350L, result.amount)
        assertEquals("老王面馆", result.merchant)
        assertEquals("牛肉面", result.note)
        assertEquals("餐饮", result.category)
        assertNotNull(result.date)
        assertSameLocalDate(2025, 8, 3, result.date ?: error("missing date"))
    }

    @Test
    fun `alipay style receipt with 年月日 formats`() {
        val text = """
            支付宝
            2026年1月15日 09:08:00
            收款方：早餐店
            金额：¥52.00
            交易完成
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(5200L, result.amount)
        assertEquals("早餐店", result.merchant)
        assertSameLocalDate(2026, 1, 15, result.date ?: error("missing date"))
    }

    @Test
    fun `no amount returns null amount with defaults`() {
        val result = parser.parse("无金额样张")
        assertNull(result.amount)
        assertEquals("其他", result.category)
    }

    @Test
    fun `parseMultiple splits receipts at separator`() {
        val text = """
            商户：店A
            金额：¥10.00
            ---
            商户：店B
            金额：¥20.00
        """.trimIndent()

        val results = parser.parseMultiple(text)

        assertEquals(2, results.size)
        assertEquals(1000L, results[0].amount)
        assertEquals(2000L, results[1].amount)
        assertEquals("店A", results[0].merchant)
        assertEquals("店B", results[1].merchant)
    }

    @Test
    fun `parseMultiple returns single result when only one receipt`() {
        val text = """
            商户：便利店
            金额：¥8.50
        """.trimIndent()

        val results = parser.parseMultiple(text)
        assertEquals(1, results.size)
        assertEquals(850L, results[0].amount)
    }

    @Test
    fun `empty text returns empty default result`() {
        val result = parser.parse("")
        assertNull(result.amount)
        assertEquals("", result.merchant)
    }

    @Test
    fun `takes largest amount when a total line exists`() {
        val text = """
            商户：超市
            小计 ¥18.00
            实付 ¥18.00
        """.trimIndent()
        assertEquals(1800L, parser.parse(text).amount)
    }

    private fun assertSameLocalDate(year: Int, month: Int, day: Int, epochMillis: Long) {
        val expected = java.time.LocalDate.of(year, month, day).toEpochDay()
        val actual = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        assertEquals(expected, actual)
    }
}
