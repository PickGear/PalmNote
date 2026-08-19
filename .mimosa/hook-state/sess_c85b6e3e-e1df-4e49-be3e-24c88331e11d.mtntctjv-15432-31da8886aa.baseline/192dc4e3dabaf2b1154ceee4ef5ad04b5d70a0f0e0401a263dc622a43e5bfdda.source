package com.palmnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    // ── parse ──

    @Test
    fun `parse converts yuan string to cents`() {
        assertEquals(Money(123456), Money.parse("1234.56"))
        assertEquals(Money(1200), Money.parse("12"))
        assertEquals(Money(50), Money.parse("0.5"))
        assertEquals(Money(50), Money.parse(".5"))
        assertEquals(Money(0), Money.parse("0.00"))
        assertEquals(Money(100000), Money.parse("1,000"))
        assertEquals(Money(100000), Money.parse("1,000.00"))
    }

    @Test
    fun `parse handles negative values`() {
        assertEquals(Money(-550), Money.parse("-5.5"))
        assertEquals(Money(-150), Money.parse("-1.50"))
    }

    @Test
    fun `parse trims whitespace`() {
        assertEquals(Money(1250), Money.parse("  12.50  "))
    }

    @Test
    fun `parse rounds more than two decimals`() {
        assertEquals(Money(1235), Money.parse("12.345"))
        assertEquals(Money(1234), Money.parse("12.344"))
    }

    @Test
    fun `parse returns null for invalid input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("   "))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12a"))
        assertNull(Money.parse("12.5.6"))
    }

    // ── fromYuan ──

    @Test
    fun `fromYuan converts double yuan to cents`() {
        assertEquals(Money(1234), Money.fromYuan(12.34))
        assertEquals(Money(0), Money.fromYuan(0.0))
        assertEquals(Money(5), Money.fromYuan(0.05))
    }

    @Test
    fun `fromYuan rounds half up`() {
        assertEquals(Money(1235), Money.fromYuan(12.345))
        assertEquals(Money(1234), Money.fromYuan(12.344))
    }

    @Test
    fun `fromYuan handles NaN and Infinity`() {
        assertEquals(Money.ZERO, Money.fromYuan(Double.NaN))
        assertEquals(Money.ZERO, Money.fromYuan(Double.POSITIVE_INFINITY))
        assertEquals(Money.ZERO, Money.fromYuan(Double.NEGATIVE_INFINITY))
    }

    // ── arithmetic ──

    @Test
    fun `plus and minus are exact`() {
        val a = Money.fromYuan(0.1)
        val b = Money.fromYuan(0.2)
        assertEquals(Money(30), a + b)
        assertEquals(Money(10), b - a)
        assertEquals(Money(0), a - b + b - a)
    }

    @Test
    fun `unary minus and times`() {
        assertEquals(Money(-150), -Money(150))
        assertEquals(Money(300), Money(150) * 2)
        assertEquals(Money(-300), Money(-150) * 2)
    }

    @Test
    fun `abs`() {
        assertEquals(Money(150), Money(-150).abs())
        assertEquals(Money(0), Money.ZERO.abs())
        assertEquals(Money(150), Money(150).abs())
    }

    @Test
    fun `isPositive isNegative isZero`() {
        assertTrue(Money(1).isPositive)
        assertFalse(Money(-1).isPositive)
        assertTrue(Money(-1).isNegative)
        assertFalse(Money(1).isNegative)
        assertTrue(Money.ZERO.isZero)
        assertFalse(Money(1).isZero)
    }

    // ── toYuanString ──

    @Test
    fun `toYuanString formats cents as yuan`() {
        assertEquals("1234.56", 123456L.toYuanString())
        assertEquals("0.05", 5L.toYuanString())
        assertEquals("1.00", 100L.toYuanString())
        assertEquals("0.00", 0L.toYuanString())
        assertEquals("-12.34", (-1234L).toYuanString())
        assertEquals("999999999.99", 99999999999L.toYuanString())
    }

    // ── toMoney ──

    @Test
    fun `toMoney wraps long cents`() {
        assertEquals(Money(5000), 5000L.toMoney())
        assertEquals(Money(-5000), (-5000L).toMoney())
    }
}
