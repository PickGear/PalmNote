package com.palmnote.ui.life

import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 分类详情页行内徽标的现算口径：逾期天数、今日到期、未来日期、每年重复、清单进度。
 * 这些徽标是排序语义（actionBucket）的视觉化，口径必须与 firstActionEpoch 一致——
 * 排序把"逾期/今天"排到最前，徽标就要回答"为什么它排前面"。
 */
class LifeCategoryDetailMetaTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 4)
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun dayStart(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `overdue days count full days past today start`() {
        val template = template()
        val yesterday = lifeItemMeta(template, item(dueDate = dayStart(today.minusDays(1))), today, zone)
        assertEquals(1L, yesterday.overdueDays)

        val threeDaysAgo = lifeItemMeta(template, item(dueDate = dayStart(today.minusDays(3))), today, zone)
        assertEquals(3L, threeDaysAgo.overdueDays)
    }

    @Test
    fun `due today keeps time of day and tolerates missing time`() {
        val withTime = lifeItemMeta(template(), item(dueDate = dayStart(today), dueTime = 18 * 60), today, zone)
        assertEquals(18 * 60, withTime.dueTodayTime)

        val withoutTime = lifeItemMeta(template(), item(dueDate = dayStart(today)), today, zone)
        assertEquals(0, withoutTime.dueTodayTime)
    }

    @Test
    fun `future date renders short form in same year`() {
        val meta = lifeItemMeta(template(), item(dueDate = dayStart(today.plusDays(3))), today, zone)
        assertEquals("10-07", meta.dueDateText)
    }

    @Test
    fun `yearly template flags the repeat badge`() {
        val meta = lifeItemMeta(template(repeatYearly = true), item(dueDate = dayStart(today.plusDays(3))), today, zone)
        assertTrue(meta.yearly)

        val plain = lifeItemMeta(template(), item(dueDate = dayStart(today.plusDays(3))), today, zone)
        assertFalse(plain.yearly)
    }

    @Test
    fun `checklist progress comes from compound payload`() {
        val fieldsData = """{"subtasks":{"v":1,"items":[{"text":"a","done":true},{"text":"b","done":true},{"text":"c","done":false}]}}"""
        val meta = lifeItemMeta(template(), item(fieldsData = fieldsData), today, zone)
        assertEquals(2, meta.checklistDone)
        assertEquals(3, meta.checklistTotal)
    }

    @Test
    fun `completed item carries no date badges`() {
        val meta = lifeItemMeta(
            template(),
            item(dueDate = dayStart(today.minusDays(2)), status = "COMPLETED"),
            today,
            zone
        )
        assertNull(meta.overdueDays)
        assertNull(meta.dueTodayTime)
        assertNull(meta.dueDateText)
    }

    @Test
    fun `record category shows relative days instead of overdue`() {
        // 打卡 / 心情 / 日记这类记录的日期是"发生时间"，落在过去是常态——首页逾期判定明确排除它们，
        // 详情页徽标必须同一口径（否则打卡记录全被标成"已逾期 N 天"，真机截图里就是这个 bug）。
        val record = template(category = "记录")
        val meta = lifeItemMeta(record, item(dueDate = dayStart(today.minusDays(20))), today, zone)
        assertNull("记录类不该有逾期徽标", meta.overdueDays)
        assertEquals(20L, meta.recordDaysAgo)

        val todayRecord = lifeItemMeta(record, item(dueDate = dayStart(today)), today, zone)
        assertEquals(0L, todayRecord.recordDaysAgo)
    }

    @Test
    fun `start anchor templates are not overdue either`() {
        // 正数日（trending_up）：日期是起点，过去是常态
        val anchor = template(icon = "trending_up")
        val meta = lifeItemMeta(anchor, item(dueDate = dayStart(today.minusDays(365))), today, zone)
        assertNull(meta.overdueDays)
        assertEquals(365L, meta.recordDaysAgo)
    }

    @Test
    fun `record category keeps future dates visible`() {
        // 订阅记录属于记录类，但「下次扣费」是未来日期——必须保留日期徽标，不能因为"记录类"就压掉
        val record = template(category = "记录")
        val meta = lifeItemMeta(record, item(dueDate = dayStart(today.plusDays(4))), today, zone)
        assertNull(meta.recordDaysAgo)
        assertEquals("10-08", meta.dueDateText)
    }

    @Test
    fun `no dates and no checklist yields empty meta`() {
        val meta = lifeItemMeta(template(), item(dueDate = null), today, zone)
        assertNull(meta.overdueDays)
        assertNull(meta.dueTodayTime)
        assertNull(meta.dueDateText)
        assertNull(meta.checklistTotal)
    }

    private fun item(
        dueDate: Long? = null,
        status: String = "ACTIVE",
        dueTime: Int? = null,
        fieldsData: String = "{}"
    ) = LifeItem(
        id = dueDate.hashCode().toLong() + status.hashCode(),
        templateId = 1L,
        title = "item",
        fieldsData = fieldsData,
        status = status,
        dueDate = dueDate,
        dueTime = dueTime,
        updatedAt = 0L
    )

    private fun template(
        category: String = "plan",
        icon: String = "checklist",
        repeatYearly: Boolean = false
    ) = LifeTemplate(
        id = 1L,
        name = "todo",
        category = category,
        icon = icon,
        color = "#3F51B5",
        fieldsConfig = """[{"key":"subtasks","label":"subtasks","type":"CHECKLIST"}]""",
        layoutType = "card",
        availableLayouts = """["card"]""",
        statusFlowConfig = "{}",
        linkConfig = "{}",
        repeatYearly = repeatYearly
    )
}
