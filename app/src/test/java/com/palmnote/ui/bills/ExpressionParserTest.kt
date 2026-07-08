package com.palmnote.ui.bills

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionParserTest {

    @Test
    fun `evaluate empty string returns 0`() {
        assertEquals(0.0, ExpressionParser.evaluate(""), 0.01)
    }

    @Test
    fun `evaluate single number`() {
        assertEquals(50.0, ExpressionParser.evaluate("50"), 0.01)
    }

    @Test
    fun `evaluate decimal number`() {
        assertEquals(33.33, ExpressionParser.evaluate("33.33"), 0.01)
    }

    @Test
    fun `evaluate addition`() {
        assertEquals(80.0, ExpressionParser.evaluate("50+30"), 0.01)
    }

    @Test
    fun `evaluate subtraction`() {
        assertEquals(20.0, ExpressionParser.evaluate("50-30"), 0.01)
    }

    @Test
    fun `evaluate complex expression`() {
        assertEquals(46.0, ExpressionParser.evaluate("15+23+8"), 0.01)
    }

    @Test
    fun `evaluate expression with mixed operators`() {
        assertEquals(37.0, ExpressionParser.evaluate("50+20-33"), 0.01)
    }

    @Test
    fun `evaluate trims trailing operator`() {
        assertEquals(50.0, ExpressionParser.evaluate("50+"), 0.01)
    }

    @Test
    fun `evaluate rounds to 2 decimal places`() {
        assertEquals(33.33, ExpressionParser.evaluate("100/3"), 0.01)
    }

    @Test
    fun `isValid returns false for empty expression`() {
        assertFalse(ExpressionParser.isValid("", "餐饮"))
    }

    @Test
    fun `isValid returns false for zero amount`() {
        assertFalse(ExpressionParser.isValid("0", "餐饮"))
    }

    @Test
    fun `isValid returns false for empty category`() {
        assertFalse(ExpressionParser.isValid("50", ""))
    }

    @Test
    fun `isValid returns true for valid input`() {
        assertTrue(ExpressionParser.isValid("50", "餐饮"))
    }

    @Test
    fun `isValid returns true for expression`() {
        assertTrue(ExpressionParser.isValid("30+20", "餐饮"))
    }
}
