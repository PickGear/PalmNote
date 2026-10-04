package com.palmnote.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 快捷添加解析：日期/时间词摘出、标题剩余、无日期词默认今天。 */
class QuickEntryParserTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 28) // 周一

    private fun parsed(input: String, base: LocalDate = today): QuickEntryParser.Parsed =
        requireNotNull(QuickEntryParser.parse(input, base)) { "parse failed: $input" }

    @Test
    fun `blank input is rejected`() {
        assertNull(QuickEntryParser.parse("   "))
    }

    @Test
    fun `relative day words are consumed`() {
        assertEquals(today, parsed("今天 买菜").date)
        assertEquals(today.plusDays(1), parsed("明天 开会").date)
        assertEquals(today.plusDays(2), parsed("后天 复查").date)
        assertEquals(today.plusDays(3), parsed("大后天 交报告").date)
    }

    @Test
    fun `title is the remaining text`() {
        val result = parsed("明天下午3点 开会")
        assertEquals("开会", result.title)
        assertEquals(today.plusDays(1), result.date)
        assertEquals(LocalTime.of(15, 0), result.time)
    }

    @Test
    fun `weekday picks next occurrence and next week variant`() {
        // 今天是周一：周三 → 本周三
        assertEquals(today.plusDays(2), parsed("周三 健身").date)
        // 下周三 → 下周一 + 2 天
        assertEquals(today.plusWeeks(1).plusDays(2), parsed("下周三 健身").date)
    }

    @Test
    fun `absolute month-day rolls to next year when past`() {
        assertEquals(LocalDate.of(2026, 12, 16), parsed("12月16日 体检").date)
        // 今年 9 月 1 日已过 → 明年
        assertEquals(LocalDate.of(2027, 9, 1), parsed("9月1日 续费").date)
    }

    @Test
    fun `time formats cover colon and half`() {
        assertEquals(LocalTime.of(15, 30), parsed("明天 15:30 取药").time)
        assertEquals(LocalTime.of(9, 30), parsed("明天早上9点半 晨会").time)
        assertEquals(LocalTime.of(21, 0), parsed("晚上9点 吃药").time)
        assertEquals(LocalTime.of(12, 0), parsed("中午12点 午饭").time)
    }

    @Test
    fun `no date word defaults to today`() {
        val result = parsed("买机票")
        assertEquals(today, result.date)
        assertNull(result.time)
        assertEquals("买机票", result.title)
    }

    @Test
    fun `date-only input keeps whole remainder as title`() {
        val result = parsed("明天买机票")
        assertEquals(today.plusDays(1), result.date)
        assertEquals("买机票", result.title)
        assertTrue(result.time == null)
    }
}
