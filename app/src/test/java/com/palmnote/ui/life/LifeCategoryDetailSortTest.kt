package com.palmnote.ui.life

import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LifeCategoryDetailSortTest {

    @Test
    fun `first display puts action items before future and completed`() {
        val template = template()
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val overdue = item("overdue", today - 86_400_000L, updatedAt = 10L)
        val future = item("future", today + 86_400_000L, updatedAt = 20L)
        val noDate = item("no date", null, updatedAt = 30L)
        val done = item("done", today - 86_400_000L, updatedAt = 40L, status = "COMPLETED")

        val sorted = sortForFirstDisplay(listOf(done, noDate, future, overdue), template)

        assertEquals(listOf("overdue", "future", "no date", "done"), sorted.map { it.title })
    }

    @Test
    fun `date field supports iso string and millis`() {
        val iso = Json.parseToJsonElement("\"2026-09-26\"")
        val millis = Json.parseToJsonElement("1780000000000")

        assertEquals(
            "2026-09-26",
            java.time.Instant.ofEpochMilli(dateFieldEpoch(iso)!!)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        )
        assertEquals(1780000000000L, dateFieldEpoch(millis))
    }

    private fun item(
        title: String,
        dueDate: Long?,
        updatedAt: Long,
        status: String = "ACTIVE"
    ) = LifeItem(
        id = title.hashCode().toLong(),
        templateId = 1L,
        title = title,
        fieldsData = """{"subtasks":{"v":1,"items":[{"text":"a","done":false}]}}""",
        status = status,
        dueDate = dueDate,
        updatedAt = updatedAt
    )

    private fun template() = LifeTemplate(
        id = 1L,
        name = "todo",
        category = "plan",
        icon = "checklist",
        color = "#3F51B5",
        fieldsConfig = """[{"key":"subtasks","label":"subtasks","type":"CHECKLIST"}]""",
        layoutType = "card",
        availableLayouts = """["card"]""",
        statusFlowConfig = "{}",
        linkConfig = "{}"
    )
}
