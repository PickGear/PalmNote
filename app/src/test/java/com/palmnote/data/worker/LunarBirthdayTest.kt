package com.palmnote.data.worker

import com.nlf.calendar.Solar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

/**
 * 农历生日提醒的日期换算：与 lunar-java 库做 round-trip 校验
 * （公历 → 农历月日 → 换算回目标农历年必须保持农历月-日一致），不硬编码农历历表。
 *
 * 注意口径：农历生日是「农历月-日每年重复」，腊月/冬月生的公历年 ≠ 农历年
 * （如 2000-01-01 是农历 1999 年冬月初五），round-trip 比对必须按农历月-日而非公历日。
 */
class LunarBirthdayTest {

    /** 公历 → "农历年/农历月(负数为闰)/农历日"。 */
    private fun solarToLunarLabel(date: LocalDate): String {
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        return "${lunar.year}/${lunar.month}/${lunar.day}"
    }

    @Test
    fun `lunarToSolar keeps the lunar month-day in the target year`() {
        listOf(
            LocalDate.of(1990, 6, 15),
            LocalDate.of(2000, 1, 1), // 农历 1999 冬月初五：公历年 ≠ 农历年
            LocalDate.of(2025, 10, 6)
        ).forEach { d ->
            val converted = lunarToSolar(d, d.year)!!
            val anchor = solarToLunarLabel(d).split("/")
            val result = solarToLunarLabel(converted).split("/")
            assertEquals("农历年应为目标年", d.year, result[0].toInt())
            assertEquals("农历月应一致 $d", anchor[1], result[1])
            assertEquals("农历日应一致 $d", anchor[2], result[2])
        }
    }

    @Test
    fun `lunarToSolar preserves leap month when the year has it`() {
        // 2023 年有闰二月；闰二月初一 = 2023-03-22，2023 年换算应保留闰月语义
        val leapFirst = LocalDate.of(2023, 3, 22)
        val converted = lunarToSolar(leapFirst, 2023)!!
        assertEquals(solarToLunarLabel(leapFirst), solarToLunarLabel(converted))
    }

    @Test
    fun `leap month birthday falls back to regular month when year lacks it`() {
        // 1995 年有闰八月：1995-10-06 = 闰八月十二；2026 年无闰八月 → 按平八月过
        val converted = lunarToSolar(LocalDate.of(1995, 10, 6), 2026)!!
        val result = solarToLunarLabel(converted).split("/")
        assertEquals("2026", result[0])
        assertEquals("8", result[1])
        assertEquals("12", result[2])
    }

    @Test
    fun `nextLunarBirthday returns this year when not yet passed`() {
        val birthday = LocalDate.of(1990, 6, 15)
        // 「今天」取今年农历锚点前 5 天，保证锚点还没过
        val today = lunarToSolar(birthday, 2026)!!.minusDays(5)
        val next = nextLunarBirthday(birthday, today)
        assertNotNull(next)
        assertEquals(today.year, next!!.year)
        assertEquals(
            solarToLunarLabel(birthday).split("/").takeLast(2),
            solarToLunarLabel(next).split("/").takeLast(2)
        )
    }

    @Test
    fun `nextLunarBirthday rolls to next year when passed`() {
        val birthday = LocalDate.of(1990, 6, 15)
        // 「今天」取今年农历锚点后 5 天，锚点今年已过 → 应滚到明年
        val today = lunarToSolar(birthday, 2026)!!.plusDays(5)
        val next = nextLunarBirthday(birthday, today)
        assertNotNull(next)
        assertEquals(today.year + 1, next!!.year)
    }

    @Test
    fun `nextLunarBirthday treats the birthday day itself as today`() {
        val birthday = LocalDate.of(1990, 6, 15)
        val today = lunarToSolar(birthday, 2026)!!
        assertEquals(today, nextLunarBirthday(birthday, today))
    }
}
