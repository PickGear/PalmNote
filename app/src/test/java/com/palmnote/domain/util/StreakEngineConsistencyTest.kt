package com.palmnote.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 一致性（滚动窗口）口径：连击互补的软指标，漏一天只微降不归零。 */
class StreakEngineConsistencyTest {

    private val today = LocalDate.of(2026, 9, 28)

    @Test
    fun `empty window has zero percent`() {
        val result = StreakEngine.consistency(emptyList(), today = today)
        assertEquals(7, result.window)
        assertEquals(0, result.hits)
        assertEquals(0, result.percent)
    }

    @Test
    fun `full week scores hundred percent`() {
        val days = (0..6).map { today.minusDays(it.toLong()) }
        val result = StreakEngine.consistency(days.map { it.toString() }, today = today)
        assertEquals(7, result.hits)
        assertEquals(100, result.percent)
    }

    @Test
    fun `missed days only dip the percentage`() {
        // 近 7 天只打了 3 天 → 43%（连击口径这里已经断了，一致性仍保留意义）
        val days = listOf(today, today.minusDays(2), today.minusDays(4))
        val result = StreakEngine.consistency(days.map { it.toString() }, today = today)
        assertEquals(3, result.hits)
        assertEquals(42, result.percent)
    }

    @Test
    fun `days outside the window do not count`() {
        val days = (0..13).map { today.minusDays(it.toLong()) }
        val result = StreakEngine.consistency(days.map { it.toString() }, today = today)
        assertEquals(7, result.hits)
    }

    @Test
    fun `future dates are ignored`() {
        val days = listOf(today, today.plusDays(1), today.plusDays(5))
        val result = StreakEngine.consistency(days.map { it.toString() }, today = today)
        assertEquals(1, result.hits)
    }

    @Test
    fun `custom window size works`() {
        val days = (0..13).map { today.minusDays(it.toLong()) }
        val result = StreakEngine.consistency(days.map { it.toString() }, window = 30, today = today)
        assertEquals(14, result.hits)
        assertEquals(30, result.window)
        assertEquals(46, result.percent)
    }
}
