package com.palmnote.domain.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 订阅下次扣费日的口径（与提醒链路同一套规则）。 */
class SubscriptionBillingTest {

    private val today = LocalDate.of(2026, 10, 7)

    @Test
    fun `本月还没到就落本月`() {
        assertEquals(LocalDate.of(2026, 10, 20), nextBillingDate(20, "monthly", null, today))
    }

    @Test
    fun `本月已过就落下一月`() {
        assertEquals(LocalDate.of(2026, 11, 3), nextBillingDate(3, "monthly", null, today))
    }

    @Test
    fun `当天就是扣费日`() {
        assertEquals(today, nextBillingDate(7, "monthly", null, today))
    }

    @Test
    fun `短月钳到最后一天`() {
        // 11 月没有 31 号 → 落 30 号
        assertEquals(
            LocalDate.of(2026, 11, 30),
            nextBillingDate(31, "monthly", null, LocalDate.of(2026, 11, 7))
        )
        // 1 月 31 号 + 一个月 = 2 月 28 号（平年）
        assertEquals(
            LocalDate.of(2027, 2, 28),
            nextBillingDate(31, "monthly", LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 1))
        )
    }

    @Test
    fun `上次已扣过就按周期往后推`() {
        // 上次 10/20 扣过（月付）→ 本次要超过 11/20
        assertEquals(
            LocalDate.of(2026, 11, 20),
            nextBillingDate(20, "monthly", LocalDate.of(2026, 10, 20), today)
        )
        // 季付：上次 10/20 → 下次至少 2027/1/20
        assertEquals(
            LocalDate.of(2027, 1, 20),
            nextBillingDate(20, "quarterly", LocalDate.of(2026, 10, 20), today)
        )
        // 年付
        assertEquals(
            LocalDate.of(2027, 10, 20),
            nextBillingDate(20, "yearly", LocalDate.of(2026, 10, 20), today)
        )
    }

    @Test
    fun `缺扣费日返回 null`() {
        assertNull(nextBillingDate(0, "monthly", null, today))
        assertNull(nextBillingDate(32, "monthly", null, today))
    }
}
