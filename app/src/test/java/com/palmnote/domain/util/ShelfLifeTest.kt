package com.palmnote.domain.util

import com.palmnote.domain.model.ShelfLifeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「生产日期 + 保质期时长」→ 到期日 的口径。
 *
 * 重点是**按自然月/年推进**而不是固定 30/365 天，以及月末收敛（1/31 + 1 个月 = 2/28）——
 * 这正是包装上「保质期 12 个月」的读法。
 */
class ShelfLifeTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millisOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun expiryOf(produced: LocalDate, amount: Int, unit: ShelfLifeUnit): LocalDate? =
        shelfLifeExpiryFrom(millisOf(produced), amount, unit, zone)
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    @Test
    fun `按天推进`() {
        assertEquals(LocalDate.of(2026, 1, 31), expiryOf(LocalDate.of(2026, 1, 1), 30, ShelfLifeUnit.DAY))
    }

    @Test
    fun `按月推进，月末自动收敛`() {
        assertEquals(LocalDate.of(2026, 2, 28), expiryOf(LocalDate.of(2026, 1, 31), 1, ShelfLifeUnit.MONTH))
        assertEquals(LocalDate.of(2027, 1, 15), expiryOf(LocalDate.of(2026, 1, 15), 12, ShelfLifeUnit.MONTH))
    }

    @Test
    fun `按年推进，闰日不丢`() {
        assertEquals(LocalDate.of(2028, 2, 29), expiryOf(LocalDate.of(2024, 2, 29), 4, ShelfLifeUnit.YEAR))
    }

    @Test
    fun `时长不是正数时算不出来`() {
        assertNull(expiryOf(LocalDate.of(2026, 1, 1), 0, ShelfLifeUnit.MONTH))
        assertNull(expiryOf(LocalDate.of(2026, 1, 1), -3, ShelfLifeUnit.DAY))
    }

    @Test
    fun `三项缺一项就算不出来（表单中途的半截状态）`() {
        val produced = millisOf(LocalDate.of(2026, 5, 10))
        assertNull(shelfLifeExpiryOrNull(null, 12, ShelfLifeUnit.MONTH, zone))
        assertNull(shelfLifeExpiryOrNull(produced, null, ShelfLifeUnit.MONTH, zone))
        assertNull(shelfLifeExpiryOrNull(produced, 12, null, zone))
        assertEquals(
            LocalDate.of(2027, 5, 10),
            shelfLifeExpiryOrNull(produced, 12, ShelfLifeUnit.MONTH, zone)
                ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        )
    }
}
