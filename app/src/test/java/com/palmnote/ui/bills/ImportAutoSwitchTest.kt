package com.palmnote.ui.bills

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入确认页「自动落档」判定：首屏别停在空的「待复核」档，但也别抢用户的手动选择。
 *
 * 三条行为：
 * 1. 首轮（lastPending == -1）进「待复核」且没有待复核项 → 自动落「全部」；
 * 2. 审核完最后一条（lastPending > 0 → 0）→ 自动落「全部」；
 * 3. 用户自己点回「待复核」看空态（lastPending 已经是 0）→ 不再被弹走。
 */
class ImportAutoSwitchTest {

    @Test
    fun `first entry with nothing to review falls back to all`() {
        assertTrue(shouldFallbackToAll(ReviewFilter.REVIEW, pendingCount = 0, lastPending = -1))
    }

    @Test
    fun `finishing the last pending item falls back to all`() {
        assertTrue(shouldFallbackToAll(ReviewFilter.REVIEW, pendingCount = 0, lastPending = 3))
    }

    @Test
    fun `manually returning to an empty review tab is not bounced away`() {
        // lastPending == 0 表示上一轮已经没有待复核项：此时是用户主动点回来看空态
        assertFalse(shouldFallbackToAll(ReviewFilter.REVIEW, pendingCount = 0, lastPending = 0))
    }

    @Test
    fun `never switches while items are still pending`() {
        assertFalse(shouldFallbackToAll(ReviewFilter.REVIEW, pendingCount = 2, lastPending = -1))
        assertFalse(shouldFallbackToAll(ReviewFilter.REVIEW, pendingCount = 2, lastPending = 5))
    }

    @Test
    fun `never switches when the user is on another tab`() {
        assertFalse(shouldFallbackToAll(ReviewFilter.ALL, pendingCount = 0, lastPending = -1))
        assertFalse(shouldFallbackToAll(ReviewFilter.SKIPPED, pendingCount = 0, lastPending = 5))
    }
}
