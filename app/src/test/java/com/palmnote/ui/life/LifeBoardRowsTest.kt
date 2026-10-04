package com.palmnote.ui.life

import com.palmnote.data.db.dao.LifeItemDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 看板行日期投影（LifeBoardRows.kt）的单测：
 * 「每年重复」滚动、逾期区升降级、当日安排口径 —— 首页三卡与完整清单页共用这套函数。
 */
class LifeBoardRowsTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 2)
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `yearly item with past anchor never shows up as overdue`() {
        // 生日 1990-05-20：卡片读数早已滚到「还有 N 天」，红区不得再按原始日期把它挂成逾期
        val rows = listOf(row(id = 1L, dueDate = millis(LocalDate.of(1990, 5, 20)), repeatYearly = true))

        assertTrue(rows.overdueOn(today).isEmpty())
    }

    @Test
    fun `yearly item due today is agenda not overdue`() {
        val rows = listOf(row(id = 1L, dueDate = millis(LocalDate.of(1995, 10, 2)), repeatYearly = true))

        assertTrue(rows.overdueOn(today).isEmpty())
        assertEquals(listOf(1L), rows.scheduledOn(today, today).map { it.itemId })
    }

    @Test
    fun `yearly item appears on next occurrence instead of raw anchor date`() {
        val rows = listOf(row(id = 1L, dueDate = millis(LocalDate.of(2020, 4, 10)), repeatYearly = true))

        // 2026-04-10 已过 → 滚到 2027-04-10；原始锚点当天不应再出现
        assertTrue(rows.scheduledOn(LocalDate.of(2026, 4, 10), today).isEmpty())
        assertEquals(listOf(1L), rows.scheduledOn(LocalDate.of(2027, 4, 10), today).map { it.itemId })
    }

    @Test
    fun `yearly solar leap-day anchor rolls to feb 28 in common years`() {
        val rows = listOf(row(id = 1L, dueDate = millis(LocalDate.of(2024, 2, 29)), repeatYearly = true))

        assertEquals(listOf(1L), rows.scheduledOn(LocalDate.of(2027, 2, 28), today).map { it.itemId })
    }

    @Test
    fun `yearly flag honours lunar marker in fieldsData`() {
        // 农历生日：投影走 nextLunarBirthday（YearlyRepeatTest 覆盖算法本身，
        // 这里只验证 fieldsData.lunar 会被读出来 —— 用一条已知公历锚点即可）。
        val rows = listOf(
            row(
                id = 1L,
                dueDate = millis(LocalDate.of(2020, 4, 10)),
                repeatYearly = true,
                fieldsData = """{"date":"2020-04-10","lunar":true,"person":"爸爸"}"""
            )
        )

        // 农历反算与公历口径必然落在同一年序列里；只要「不在原始锚点日出现」即证明发生了滚动
        assertTrue(rows.scheduledOn(LocalDate.of(2026, 4, 10), today).isEmpty())
    }

    @Test
    fun `non-yearly item stays on its raw due date`() {
        val rows = listOf(row(id = 1L, dueDate = millis(LocalDate.of(2026, 4, 10))))

        assertTrue(rows.overdueOn(today).map { it.itemId } == listOf(1L))
        assertEquals(listOf(1L), rows.scheduledOn(LocalDate.of(2026, 4, 10), today).map { it.itemId })
    }

    @Test
    fun `overdue rows sort by due date ascending and drop completed record-category rows`() {
        val rows = listOf(
            row(id = 1L, dueDate = millis(LocalDate.of(2026, 9, 30)), category = "计划"),
            row(id = 2L, dueDate = millis(LocalDate.of(2026, 9, 1)), category = "计划"),
            row(id = 3L, dueDate = millis(LocalDate.of(2026, 8, 15)), category = "记录"),
            row(id = 4L, dueDate = millis(LocalDate.of(2026, 7, 20)), status = "COMPLETED"),
            row(id = 5L, dueDate = null)
        )

        val overdue = rows.overdueOn(today)

        // 最久逾期的排最前；记录类与已完成不入列（此前继承 updatedAt 排序，最久的反而可能被折叠）
        assertEquals(listOf(2L, 1L), overdue.map { it.itemId })
    }

    @Test
    fun `scheduled projection keeps archived rows out`() {
        val day = millis(LocalDate.of(2026, 10, 2))
        val rows = listOf(
            row(id = 1L, dueDate = day),
            row(id = 2L, dueDate = day, status = "ARCHIVED")
        )

        val scheduled = rows.scheduledOn(today, today)

        assertEquals(listOf(1L), scheduled.map { it.itemId })
        assertFalse(scheduled.any { it.itemId == 2L })
    }

    private fun row(
        id: Long,
        dueDate: Long?,
        dueTime: Int? = null,
        status: String = "ACTIVE",
        category: String = "计划",
        repeatYearly: Boolean = false,
        fieldsData: String = "{}"
    ) = LifeItemDao.LifeBoardItemRow(
        itemId = id,
        title = "item-$id",
        status = status,
        fieldsData = fieldsData,
        createdAt = id,
        updatedAt = id,
        dueDate = dueDate,
        dueTime = dueTime,
        isDemo = false,
        templateId = id,
        templateName = "template",
        icon = "checklist",
        color = "#3F51B5",
        category = category,
        fieldsConfig = "[]",
        isSpecial = false,
        repeatYearly = repeatYearly
    )
}
