package com.palmnote.domain.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DateUtilsTest {

    @Test
    fun `getCurrentYearMonth returns correct format`() {
        val result = DateUtils.getCurrentYearMonth()
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}")))
    }

    @Test
    fun `formatYearMonth returns correct format`() {
        val timestamp = System.currentTimeMillis()
        val result = DateUtils.formatYearMonth(timestamp)
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}")))
    }

    @Test
    fun `formatDisplayDate returns non-empty string`() {
        val timestamp = System.currentTimeMillis()
        val result = DateUtils.formatDisplayDate(timestamp)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `formatDisplayYearDate returns year month day format`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            val timestamp = System.currentTimeMillis()
            val result = DateUtils.formatDisplayYearDate(timestamp)
            assertTrue(result.contains("年"))
            assertTrue(result.contains("月"))
            assertTrue(result.contains("日"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
