package com.palmnote.ui.bills

import com.palmnote.data.db.entity.Budget
import com.palmnote.domain.model.BudgetProgress
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 预算页两处「算出来才显示」的逻辑。
 *
 * 这两条判断在 Compose 里只是几行 lambda，但 decide 了界面上有没有东西出现：
 * 日期范围写错会误导用户看错周期，合计提示算错则会在没超额时报警、或该报时不报。
 */
class BudgetHintLogicTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(date: String, endOfDay: Boolean = false): Long {
        val d = LocalDate.parse(date)
        val t = if (endOfDay) d.atTime(23, 59, 59, 999_000_000) else d.atStartOfDay()
        return t.atZone(zone).toInstant().toEpochMilli()
    }

    // ── 本期日期范围 ──

    @Test
    fun `同一月内的周显示为 月日 到 月日`() {
        assertEquals(
            "11.02 – 11.08",
            budgetRangeText(millis("2026-11-02"), millis("2026-11-08", endOfDay = true), zone)
        )
    }

    @Test
    fun `跨月的周两边都写月份`() {
        assertEquals(
            "10.26 – 11.01",
            budgetRangeText(millis("2026-10-26"), millis("2026-11-01", endOfDay = true), zone)
        )
    }

    @Test
    fun `跨年的周显示为 12月 到 1月`() {
        assertEquals(
            "12.28 – 01.03",
            budgetRangeText(millis("2026-12-28"), millis("2027-01-03", endOfDay = true), zone)
        )
    }

    // ── 分类合计 vs 总预算 ──

    @Test
    fun `没设总预算时不提示`() {
        assertNull(categoryOverTotal(categoryLimitTotal = 6_000L, totalLimit = 0L))
    }

    @Test
    fun `分类合计小于总预算时不提示`() {
        assertNull(categoryOverTotal(categoryLimitTotal = 3_000L, totalLimit = 5_000L))
    }

    @Test
    fun `分类合计正好等于总预算时不提示`() {
        assertNull(categoryOverTotal(categoryLimitTotal = 5_000L, totalLimit = 5_000L))
    }

    @Test
    fun `分类合计超出时给出差额`() {
        assertEquals(
            1_000L,
            categoryOverTotal(categoryLimitTotal = 6_000L, totalLimit = 5_000L)
        )
    }

    @Test
    fun `一条分类预算都没有时不提示`() {
        assertNull(categoryOverTotal(categoryLimitTotal = 0L, totalLimit = 5_000L))
    }

    // ── 时间进度（条上那道刻度的位置） ──

    private fun progressWithWindow(start: Long, end: Long) = BudgetProgress(
        budget = Budget(period = "MONTH", scope = "TOTAL", totalBudget = 10_000L),
        spent = 0L,
        windowStart = start,
        windowEnd = end
    )

    @Test
    fun `时间进度在窗口正中为一半`() {
        val start = millis("2026-11-02")
        val end = millis("2026-11-08", endOfDay = true)
        val mid = start + (end - start) / 2
        assertEquals(0.5f, progressWithWindow(start, end).elapsedRatio(mid), 0.01f)
    }

    @Test
    fun `窗口还没开始时间进度为 0`() {
        val start = millis("2026-11-02")
        val end = millis("2026-11-08", endOfDay = true)
        assertEquals(0f, progressWithWindow(start, end).elapsedRatio(start - 86_400_000L), 0.001f)
    }

    @Test
    fun `窗口结束后时间进度封顶为 1`() {
        val start = millis("2026-11-02")
        val end = millis("2026-11-08", endOfDay = true)
        assertEquals(1f, progressWithWindow(start, end).elapsedRatio(end + 86_400_000L), 0.001f)
    }

    @Test
    fun `非法窗口不画刻度`() {
        assertEquals(0f, progressWithWindow(1_000L, 1_000L).elapsedRatio(2_000L), 0.001f)
    }

    // ── 阈值滑杆的落值（无极滑动、1% 粒度） ──

    @Test
    fun `滑到 81_37 落在 81`() {
        assertEquals(0.81f, quantizeThreshold(0.8137f), 0.0001f)
    }

    @Test
    fun `四舍五入到最近的整数百分比`() {
        // 0.8146 → 81%（不是直接截断的 81%，也不是 82%）
        assertEquals(0.81f, quantizeThreshold(0.8146f), 0.0001f)
        assertEquals(0.82f, quantizeThreshold(0.8156f), 0.0001f)
    }

    @Test
    fun `超出范围的值被夹在 1 到 100 之间`() {
        assertEquals(0.01f, quantizeThreshold(0f), 0.0001f)
        assertEquals(0.01f, quantizeThreshold(-0.3f), 0.0001f)
        assertEquals(1f, quantizeThreshold(1.4f), 0.0001f)
    }

    @Test
    fun `低阈值按 1% 粒度保留`() {
        // 下限放宽到 1% 后，5% / 12% 这种低阈值要能原样落库
        assertEquals(0.05f, quantizeThreshold(0.05f), 0.0001f)
        assertEquals(0.12f, quantizeThreshold(0.1237f), 0.0001f)
    }
}
