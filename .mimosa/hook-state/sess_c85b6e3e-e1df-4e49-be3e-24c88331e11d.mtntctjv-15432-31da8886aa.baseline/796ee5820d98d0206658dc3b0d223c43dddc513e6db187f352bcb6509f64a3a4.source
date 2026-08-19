package com.palmnote.domain.util

import com.palmnote.R
import com.palmnote.domain.model.Money
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyUtilsTest {

    private fun mockContext(): android.content.Context = mockk {
        every { getString(any(), *anyVararg<Any>()) } answers {
            val resId = firstArg<Int>()
            val formatted = (args[1] as Array<*>)[0] as String
            when (resId) {
                R.string.currency_format -> "¥$formatted"
                R.string.currency_compact_wan -> "¥${formatted}万"
                R.string.currency_compact_yi -> "¥${formatted}亿"
                else -> formatted
            }
        }
    }

    @Test
    fun `formatCurrency formats cents with thousands separator`() {
        val context = mockContext()
        assertEquals("¥1,234.56", CurrencyUtils.formatCurrency(context, Money(123456)))
    }

    @Test
    fun `formatCurrency handles zero`() {
        val context = mockContext()
        assertEquals("¥0.00", CurrencyUtils.formatCurrency(context, Money(0)))
    }

    @Test
    fun `formatCurrency handles negative`() {
        val context = mockContext()
        assertEquals("¥-1,234.56", CurrencyUtils.formatCurrency(context, Money(-123456)))
    }

    @Test
    fun `formatCurrency handles small amounts without separator`() {
        val context = mockContext()
        assertEquals("¥0.05", CurrencyUtils.formatCurrency(context, Money(5)))
    }

    @Test
    fun `formatCompact below one hundred wan uses normal format`() {
        val context = mockContext()
        assertEquals("¥9,999.99", CurrencyUtils.formatCompact(context, Money(999999)))
        assertEquals("¥999,999.00", CurrencyUtils.formatCompact(context, Money(99_9999_00L)))
    }

    @Test
    fun `formatCompact exactly one hundred wan uses wan`() {
        val context = mockContext()
        assertEquals("¥100万", CurrencyUtils.formatCompact(context, Money(1_0000_0000L)))
    }

    @Test
    fun `formatCompact one hundred twenty three wan uses wan`() {
        val context = mockContext()
        assertEquals("¥123万", CurrencyUtils.formatCompact(context, Money(123_0000_00L)))
    }

    @Test
    fun `formatCompact wan with decimal`() {
        val context = mockContext()
        assertEquals("¥123.45万", CurrencyUtils.formatCompact(context, Money(12345_0000L)))
    }

    @Test
    fun `formatCompact one yi uses yi`() {
        val context = mockContext()
        assertEquals("¥1亿", CurrencyUtils.formatCompact(context, Money(10000_0000_00L)))
    }

    @Test
    fun `formatCompact ten yi uses yi`() {
        val context = mockContext()
        assertEquals("¥10亿", CurrencyUtils.formatCompact(context, Money(1000_0000_0000L)))
    }

    @Test
    fun `formatCompact yi with decimal`() {
        val context = mockContext()
        assertEquals("¥1.5亿", CurrencyUtils.formatCompact(context, Money(15000_0000_00L)))
    }
}
