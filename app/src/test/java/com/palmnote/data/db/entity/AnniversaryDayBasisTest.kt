package com.palmnote.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 纪念日的「还有几天」必须按**自然日**算，且**当天就是 0**。
 *
 * 原来的实现拿时间戳比大小，而日期存的是当天 00:00、永远小于"现在"，于是纪念日当天被判成
 * "今年已过"跳到明年，卡片显示「还有 365 天」；平时又因为毫秒除法截断少算一天。
 * 界面里「`daysUntil == 0` → 今天」那条分支因此在当天根本走不到。
 */
class AnniversaryDayBasisTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun at(offsetDays: Long): Long =
        LocalDate.now(zone).plusDays(offsetDays).atStartOfDay(zone).toInstant().toEpochMilli()

    /** 只保留「月-日」，年份给个旧年份，逼 daysUntil 自己去算今年/明年那一次。 */
    private fun sameDayInOldYear(offsetDays: Long): Long =
        LocalDate.now(zone).plusDays(offsetDays).withYear(1999)
            .atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `纪念日当天是 0 天`() {
        assertEquals(0, Anniversary(title = "生日", solarDate = at(0)).daysUntil)
        assertEquals(0, Anniversary(title = "生日", solarDate = sameDayInOldYear(0)).daysUntil)
    }

    @Test
    fun `明天是 1 天`() {
        assertEquals(1, Anniversary(title = "生日", solarDate = sameDayInOldYear(1)).daysUntil)
    }

    @Test
    fun `一周后是 7 天`() {
        assertEquals(7, Anniversary(title = "生日", solarDate = sameDayInOldYear(7)).daysUntil)
    }

    @Test
    fun `刚过去的那次算到明年`() {
        val days = Anniversary(title = "生日", solarDate = sameDayInOldYear(-1)).daysUntil
        assertTrue("刚过完应该算到明年的 364~366 天，实际 $days", days in 364..366)
    }

    @Test
    fun `已经过去的天数按自然日算，当天是 0`() {
        assertEquals(0, Anniversary(title = "结婚", solarDate = at(0)).daysSince)
        assertEquals(100, Anniversary(title = "结婚", solarDate = at(-100)).daysSince)
        assertEquals(3650, Anniversary(title = "结婚", solarDate = at(-3650)).daysSince)
    }
}
