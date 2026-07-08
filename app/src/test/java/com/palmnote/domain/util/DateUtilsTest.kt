package com.palmnote.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

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
        val timestamp = System.currentTimeMillis()
        val result = DateUtils.formatDisplayYearDate(timestamp)
        assertTrue(result.contains("年"))
        assertTrue(result.contains("月"))
        assertTrue(result.contains("日"))
    }

    @Test
    fun `formatShortDate returns MM/dd format`() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.JULY, 3)
        val timestamp = calendar.timeInMillis
        val result = DateUtils.formatShortDate(timestamp)
        assertEquals("07/03", result)
    }

    @Test
    fun `formatCountdown returns remaining days`() {
        val result = DateUtils.formatCountdown(5)
        assertEquals("剩余 5 天", result)
    }

    @Test
    fun `formatStreak returns consecutive days`() {
        val result = DateUtils.formatStreak(7)
        assertEquals("连续 7 天", result)
    }
}
