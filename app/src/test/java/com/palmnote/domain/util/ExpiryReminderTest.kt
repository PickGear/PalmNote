package com.palmnote.domain.util

import com.palmnote.domain.model.ExpiryReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 物品到期提醒的窗口口径：**以到期日为中心的对称区间**——提前 N 天起，到过期后 N 天止。
 *
 * 过期那半边是有意加的（对食品/药品来说「它过期了」比「还有 3 天」更该知道），
 * 而敢推过期的前提是通知用每件物品固定的 id、就地更新而不是越堆越多。
 */
class ExpiryReminderTest {

    @Test
    fun `到期当天与提前 N 天`() {
        assertEquals(ExpiryReminderKind.TODAY, expiryReminderKind(0, 3))
        assertEquals(ExpiryReminderKind.SOON, expiryReminderKind(1, 3))
        assertEquals(ExpiryReminderKind.SOON, expiryReminderKind(3, 3))
    }

    @Test
    fun `过期后 N 天内仍然提醒`() {
        assertEquals(ExpiryReminderKind.EXPIRED, expiryReminderKind(-1, 3))
        assertEquals(ExpiryReminderKind.EXPIRED, expiryReminderKind(-3, 3))
    }

    @Test
    fun `窗口两头之外都不提醒`() {
        assertNull("第 4 天起超出提前窗口", expiryReminderKind(4, 3))
        assertNull("过期第 4 天起超出延后窗口，不再追着推", expiryReminderKind(-4, 3))
        assertNull(expiryReminderKind(30, 3))
        assertNull(expiryReminderKind(-30, 3))
    }

    @Test
    fun `提前天数为 0 时只提醒到期当天`() {
        assertEquals(ExpiryReminderKind.TODAY, expiryReminderKind(0, 0))
        assertNull(expiryReminderKind(1, 0))
        assertNull("提前 0 天意味着过期也不再提醒", expiryReminderKind(-1, 0))
    }
}
