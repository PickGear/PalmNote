package com.palmnote.ui.life

import com.palmnote.data.db.dao.LifeItemDao
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LifeFullListSortTest {

    @Test
    fun `agenda rows follow the home due time order with unscheduled rows first`() {
        val date = LocalDate.of(2026, 9, 26)
        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rows = listOf(
            row(id = 1L, dueDate = dayStart + 60_000L, dueTime = 540),
            row(id = 2L, dueDate = dayStart, dueTime = null),
            row(id = 3L, dueDate = dayStart + 120_000L, dueTime = 60)
        )

        val sorted = sortScheduledRows(rows)

        // 无时间（随时可做）置顶，有时刻的按时间表排下面（全天位排在有时刻之前）
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.itemId })
    }

    @Test
    fun `agenda date filter keeps only the selected local day`() {
        val dayStart = LocalDate.of(2026, 9, 26)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val rows = listOf(
            row(id = 1L, dueDate = dayStart),
            row(id = 2L, dueDate = dayStart + 86_400_000L),
            row(id = 3L, dueDate = null),
            row(id = 4L, dueDate = dayStart, status = "ARCHIVED")
        )

        val filtered = rows.scheduledOn(LocalDate.of(2026, 9, 26), today = LocalDate.of(2026, 9, 26))

        assertEquals(listOf(1L), filtered.map { it.itemId })
    }

    private fun row(
        id: Long,
        dueDate: Long?,
        dueTime: Int? = null,
        status: String = "ACTIVE"
    ) = LifeItemDao.LifeBoardItemRow(
        itemId = id,
        title = "item-$id",
        status = status,
        fieldsData = "{}",
        createdAt = id,
        updatedAt = id,
        dueDate = dueDate,
        dueTime = dueTime,
        isDemo = false,
        templateId = id,
        templateName = "template",
        icon = "checklist",
        color = "#3F51B5",
        category = "计划",
        fieldsConfig = "[]",
        isSpecial = false,
        repeatYearly = false
    )
}
