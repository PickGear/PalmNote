package com.palmnote.ui.life

import android.app.Application
import com.palmnote.app.R
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.CrossLink
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.EntityType
import com.palmnote.domain.model.LinkType
import com.palmnote.domain.model.ProgressForm
import com.palmnote.domain.util.StreakEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LifeDetailAggregatesTest {

    /**
     * 按**行的标签**取行（前缀匹配）。
     *
     * 结构区已按 §14.2 改为"**按字段的手分组**"：组标题是手名，不再是字段名 ——
     * 所以这些测试不能再按组标题找（此前 `groups.single { it.title == "当前" }` 之所以成立，
     * 是因为组标题恰好等于字段名）。断言的**意图没变**，只是查找方式跟着布局走。
     *
     * 前缀匹配的原因：某些行的标签里**含值**（Bar 是「当前 2 / 4」这种，设计如此）。
     */
    private fun DetailUi.row(label: String): DetailRowModel =
        groups.flatMap { it.rows }.first { it.labelOf() == label || it.labelOf().startsWith("$label ") }

    private fun DetailUi.groupOfRow(label: String): DetailGroup =
        groups.first { group -> group.rows.any { it.labelOf() == label || it.labelOf().startsWith("$label ") } }

    /** 行标签：模板专属块（打卡统计 / 专注 / 热力 / 表格 / 周柱）没有标签，返回空串。 */
    @Suppress("CyclomaticComplexMethod")
    private fun DetailRowModel.labelOf(): String = when (this) {
        is DetailRowModel.Kv -> label
        is DetailRowModel.Toggle -> label
        is DetailRowModel.Swatch -> label
        is DetailRowModel.Link -> label
        is DetailRowModel.Stars -> label
        is DetailRowModel.Bar -> label
        is DetailRowModel.CheckList -> label
        is DetailRowModel.Paragraph -> label
        is DetailRowModel.Persons -> label
        is DetailRowModel.Media -> label
        is DetailRowModel.StackBar -> label
        is DetailRowModel.MapMini -> label
        is DetailRowModel.Timeline -> label
        is DetailRowModel.Chips -> label
        else -> ""
    }

    @Test
    fun `checkin aggregate uses longest streak from full history`() {
        val result = StreakEngine.compute(
            listOf("2026-09-18", "2026-09-19", "2026-09-20", "2026-09-21", "2026-09-22"),
            today = java.time.LocalDate.of(2026, 9, 24)
        )

        assertEquals(0, result.current)
        assertEquals(5, result.longest)
    }

    @Test
    fun `focus duration formats hours and minutes like design`() {
        assertEquals("2h15m", fmtDuration(135 * 60_000L))
        assertEquals("9h40m", fmtDuration(580 * 60_000L))
        assertEquals("45m", fmtDuration(45 * 60_000L))
    }

    @Test
    fun `focus session entries keep positive durations in creation order`() {
        val rows = listOf(
            LifeItemDao.FocusSessionRow("2026-09-24", "写重构方案", """{"duration":1500000}""", atTime(9, 30)),
            LifeItemDao.FocusSessionRow("2026-09-24", "空计时", "{}", atTime(10, 0)),
            LifeItemDao.FocusSessionRow("2026-09-24", "读论文", """{"duration":1500000}""", atTime(10, 15))
        )

        val entries = focusSessionEntries(rows, "2026-09-24")

        assertEquals(listOf("写重构方案" to "09:30", "读论文" to "10:15"), entries.map { it.title to it.time })
        assertEquals(listOf(25, 25), entries.map { it.minutes })
    }

    @Test
    fun `focus day duration preserves raw milliseconds`() {
        val rows = listOf(
            LifeItemDao.FocusSessionRow("2026-09-24", "short a", """{"duration":90000}""", atTime(9, 30)),
            LifeItemDao.FocusSessionRow("2026-09-24", "short b", """{"duration":90000}""", atTime(10, 0)),
            LifeItemDao.FocusSessionRow("2026-09-23", "other day", """{"duration":3000000}""", atTime(11, 0))
        )

        assertEquals(180_000L, focusDayDurationMs(rows, "2026-09-24"))
    }

    @Test
    fun `focus week bars sum each day and retain the full monday to sunday axis`() {
        val weekStart = LocalDate.of(2026, 9, 21)
        val rows = listOf(
            LifeItemDao.FocusSessionRow("2026-09-21", "a", """{"duration":1500000}""", atTime(9, 0)),
            LifeItemDao.FocusSessionRow("2026-09-21", "b", """{"duration":600000}""", atTime(10, 0)),
            LifeItemDao.FocusSessionRow("2026-09-23", "c", """{"duration":3000000}""", atTime(11, 0))
        )

        val bars = focusWeekBars(rows, weekStart)

        assertEquals(7, bars.size)
        assertEquals(listOf(2_100_000L, 0L, 3_000_000L, 0L, 0L, 0L, 0L), bars.map { it.durationMs })
    }

    @Test
    fun `book photo aggregate counts arrays and comma separated values`() {
        val array = Json.decodeFromString<JsonObject>("""{"photos":["a","b"]}""")
        val comma = Json.decodeFromString<JsonObject>("""{"photos":"a,b,c"}""")
        assertEquals(2, array.photoCount())
        assertEquals(3, comma.photoCount())
    }

    @Test
    fun `diary timeline aggregates multiple entries by day`() {
        val ui = assemble(
            icon = "book",
            aggregates = DetailAggregates(),
            dayRows = listOf(
                LifeItemDao.TemplateDayRow("2026-09-19", "first", """{"weather":"晴","mood":"开心","photos":["a"]}"""),
                LifeItemDao.TemplateDayRow("2026-09-19", "second", """{"weather":"阴","mood":"平静","photos":"b,c"}"""),
                LifeItemDao.TemplateDayRow("2026-09-18", "older", """{"weather":"雨","photos":[]}""")
            )
        )

        val group = ui.groups.single { it.titleRes == R.string.life_habit_filter_timeline }
        val entries = (group.rows.single() as DetailRowModel.Timeline).entries
        assertEquals(listOf("09-19", "09-18"), entries.map { it.day })
        assertEquals(3, entries.first().photoCount)
        assertEquals("阴", entries.first().weather)
        assertEquals("平静", entries.first().mood)
    }

    @Test
    fun `current streak can cross month boundary`() {
        val result = StreakEngine.compute(
            listOf("2026-08-30", "2026-08-31", "2026-09-01"),
            today = LocalDate.of(2026, 9, 1)
        )

        assertEquals(3, result.current)
    }

    @Test
    fun `diary metrics follow design labels and units`() {
        val ui = assemble(
            icon = "book",
            aggregates = DetailAggregates(bookMonthCount = 14, bookStreak = 5, bookPhotoCount = 3)
        )

        assertEquals(
            listOf(14 to R.string.life_metric_month_pieces, 5 to R.string.life_metric_streak_days, 3 to R.string.life_metric_photos_count),
            ui.metrics.map { it.value.toInt() to it.formatRes }
        )
        assertEquals(
            listOf(R.string.life_metric_month, R.string.life_metric_streak, R.string.life_metric_photos),
            ui.metrics.map { it.labelRes }
        )
    }

    @Test
    fun `checkin total comes from record aggregate not distinct days`() {
        val ui = assemble(
            icon = "calendar_month",
            aggregates = DetailAggregates(checkInMonthCount = 18, checkInLongest = 21, checkInTotal = 63)
        )

        assertEquals(listOf("18", "21", "63"), ui.metrics.map { it.value })
        assertEquals(
            listOf(R.string.life_metric_count_times, R.string.life_metric_streak_days, R.string.life_metric_count_times),
            ui.metrics.map { it.formatRes }
        )
    }

    @Test
    fun `focus metrics show today and week windows`() {
        val ui = assemble(
            icon = "timer",
            aggregates = DetailAggregates(focusTodayMs = 135 * 60_000L, focusWeekMs = 580 * 60_000L)
        )

        assertEquals(listOf("2h15m", "9h40m"), ui.metrics.map { it.value })
        assertEquals(listOf(R.string.life_metric_today, R.string.life_metric_week), ui.metrics.map { it.labelRes })
    }

    @Test
    fun `subscription costs normalize monthly and yearly billing cycles`() {
        assertEquals(25.0, subscriptionMonthlyCost(25.0, "monthly"), 0.001)
        assertEquals(10.0, subscriptionMonthlyCost(30.0, "quarterly"), 0.001)
        assertEquals(10.0, subscriptionMonthlyCost(120.0, "yearly"), 0.001)
        assertEquals(300.0, subscriptionYearlyCost(25.0, "monthly"), 0.001)
        assertEquals(120.0, subscriptionYearlyCost(30.0, "quarterly"), 0.001)
        assertEquals(120.0, subscriptionYearlyCost(120.0, "yearly"), 0.001)
    }

    @Test
    fun `subscription detail keeps billing cycle chip and localizes its value`() {
        val ui = assemble(
            icon = "subscriptions",
            aggregates = DetailAggregates(),
            fieldsConfig = """
                [
                  {"key":"price","label":"扣费金额","type":"NUMBER","unit":"元","sortOrder":1},
                  {"key":"billingCycle","label":"扣费周期","type":"SELECT","required":true,
                   "options":["monthly","quarterly","yearly"],"sortOrder":2},
                  {"key":"nextBilling","label":"下次扣费","type":"DATE","sortOrder":3}
                ]
            """.trimIndent(),
            fieldsData = """{"price":25,"billingCycle":"monthly","nextBilling":"2026-09-26"}"""
        )

        assertEquals(listOf("monthlyExpense", "yearlyExpense", "nextBilling"), ui.metrics.map { it.key })
        assertEquals(listOf("¥25", "¥300"), ui.metrics.take(2).map { it.value })

        val group = ui.groupOfRow("扣费周期")
        val chips = group.rows.single() as DetailRowModel.Chips
        assertEquals(listOf("monthly"), chips.values)
        assertEquals(listOf(R.string.life_detail_cycle_monthly), chips.valueRes)
    }

    @Test
    fun `non percent progress row follows the stored form and segment target`() {
        val ui = assemble(
            icon = "custom",
            aggregates = DetailAggregates(),
            fieldsConfig = """
                [
                  {"key":"goal","label":"目标","type":"NUMBER","unit":"次","sortOrder":1},
                  {"key":"current","label":"当前","type":"NUMBER","unit":"次",
                   "showAsProgress":true,"progressTargetKey":"goal",
                   "progressStyle":"SEGMENTED_RING","sortOrder":2}
                ]
            """.trimIndent(),
            fieldsData = """{"goal":4,"current":2}"""
        )

        val row = ui.row("当前") as DetailRowModel.Bar
        assertEquals(ProgressForm.SEGMENTED_RING, row.form)
        assertEquals(4, row.segments)
        assertEquals(0.5f, row.fraction, 0.001f)
    }

    @Test
    fun `formula progress falls back to its derived value`() {
        val ui = assemble(
            icon = "custom",
            aggregates = DetailAggregates(),
            fieldsConfig = """
                [
                  {"key":"goal","label":"目标","type":"NUMBER","sortOrder":1},
                  {"key":"done","label":"完成","type":"NUMBER","sortOrder":2},
                  {"key":"ratio","label":"完成率","type":"FORMULA","defaultValue":"done / goal",
                   "showAsProgress":true,"max":1.0,"sortOrder":3}
                ]
            """.trimIndent(),
            fieldsData = """{"goal":8,"done":2}"""
        )

        val row = ui.row("完成率") as DetailRowModel.Bar
        assertEquals(0.25f, row.fraction, 0.001f)
    }

    @Test
    fun `checklist progress uses item count as its segment target`() {
        val ui = assemble(
            icon = "custom",
            aggregates = DetailAggregates(),
            fieldsConfig = """
                [
                  {"key":"subtasks","label":"子任务","type":"CHECKLIST",
                   "showAsProgress":true,"sortOrder":1}
                ]
            """.trimIndent(),
            fieldsData = """
                {"subtasks":{"v":1,"items":[
                  {"text":"a","done":true},
                  {"text":"b","done":true},
                  {"text":"c","done":false},
                  {"text":"d","done":false}
                ]}}
            """.trimIndent()
        )

        val row = ui.row("子任务") as DetailRowModel.Bar
        assertEquals(ProgressForm.SEGMENTED_RING, row.form)
        assertEquals(4, row.segments)
        assertEquals("2 / 4", row.value)
    }

    @Test
    fun `field without progress capability does not render a progress row`() {
        val ui = assemble(
            icon = "custom",
            aggregates = DetailAggregates(),
            fieldsConfig = """
                [
                  {"key":"note","label":"备注","type":"TEXT",
                   "showAsProgress":true,"sortOrder":1}
                ]
            """.trimIndent(),
            fieldsData = """{"note":"普通文本"}"""
        )

        val row = ui.row("备注")
        assertEquals(DetailRowModel.Kv("备注", "普通文本"), row)
    }

    @Test
    fun `focus detail groups render today sessions and week bars`() {
        val ui = assemble(
            icon = "timer",
            aggregates = DetailAggregates(
                focusSessions = listOf(FocusSessionEntry(25, "写重构方案", "09:30")),
                focusWeekBars = listOf(
                    FocusWeekBar(java.time.DayOfWeek.MONDAY, 0L),
                    FocusWeekBar(java.time.DayOfWeek.TUESDAY, 0L),
                    FocusWeekBar(java.time.DayOfWeek.WEDNESDAY, 0L),
                    FocusWeekBar(java.time.DayOfWeek.THURSDAY, 1_500_000L),
                    FocusWeekBar(java.time.DayOfWeek.FRIDAY, 0L),
                    FocusWeekBar(java.time.DayOfWeek.SATURDAY, 0L),
                    FocusWeekBar(java.time.DayOfWeek.SUNDAY, 0L)
                )
            )
        )

        val sessions = ui.groups.single { it.titleRes == R.string.life_detail_focus_sessions }
        assertEquals(
            listOf(FocusSessionEntry(25, "写重构方案", "09:30")),
            (sessions.rows.single() as DetailRowModel.FocusSessions).entries
        )
        val week = ui.groups.single { it.titleRes == R.string.life_detail_focus_week }
        assertEquals(7, (week.rows.single() as DetailRowModel.WeekBars).bars.size)
    }

    @Test
    fun `focus week group stays visible when the whole week is zero`() {
        val ui = assemble(
            icon = "timer",
            aggregates = DetailAggregates(
                focusWeekBars = java.time.DayOfWeek.entries.map { FocusWeekBar(it, 0L) }
            )
        )

        val week = ui.groups.single { it.titleRes == R.string.life_detail_focus_week }
        assertEquals(7, (week.rows.single() as DetailRowModel.WeekBars).bars.size)
        assertEquals(List(7) { 0L }, (week.rows.single() as DetailRowModel.WeekBars).bars.map { it.durationMs })
    }

    @Test
    fun `school metrics use real completion data instead of missing rating field`() {
        val metrics = schoolMetricsOf(
            listOf(
                """{"duration":45,"completedLessons":8}""",
                """{"duration":30,"completedLessons":12}"""
            )
        )

        assertEquals(75.0, metrics.totalMinutes ?: 0.0, 0.001)
        assertEquals(12.0, metrics.completedLessons ?: 0.0, 0.001)
    }

    @Test
    fun `relation counts include only bills and notes`() {
        val counts = relationCountsOf(
            listOf(
                link(EntityType.BILL, 1),
                link(EntityType.BILL, 2),
                link(EntityType.NOTE, 3),
                link(EntityType.ITEM, 4)
            )
        )

        assertEquals(2, counts.bills)
        assertEquals(1, counts.notes)
    }

    @Test
    fun `multi select renders read-only chips with one chip per selected value`() {
        val ui = assemble(
            icon = "custom",
            aggregates = DetailAggregates(),
            fieldsConfig = """[{"key":"category","label":"category","type":"MULTI_SELECT","sortOrder":1}]""",
            fieldsData = """{"category":["work","study"]}"""
        )

        val row = ui.groups.single().rows.single() as DetailRowModel.Chips
        assertEquals(listOf("work", "study"), row.values)
    }

    /**
     * COLOR 曾经与 BOOLEAN 共用 `Toggle` 渲染器，而 `ctx.flag(key)` 从 `#RRGGBB` 里
     * 取布尔值恒为 false ⇒ 用户选的颜色在详情页**永远显示成「关」**。
     * 这条钉住「色值走色块、布尔才是开关」，避免两者再次合流。
     */
    @Test
    fun `color field renders as a swatch while boolean stays a toggle`() {
        val ui = assemble(
            icon = "custom",
            aggregates = DetailAggregates(),
            fieldsConfig = """
                [
                  {"key":"ink","label":"颜色","type":"COLOR","sortOrder":1},
                  {"key":"pinned","label":"置顶","type":"BOOLEAN","sortOrder":2}
                ]
            """.trimIndent(),
            fieldsData = """{"ink":"#E53935","pinned":true}"""
        )

        val swatch = ui.row("颜色") as DetailRowModel.Swatch
        assertEquals("#E53935", swatch.hex)

        val toggle = ui.row("置顶") as DetailRowModel.Toggle
        assertEquals(true, toggle.checked)
    }

    private fun link(targetType: EntityType, targetId: Long) = CrossLink(
        sourceType = EntityType.ITEM,
        sourceId = 1L,
        targetType = targetType,
        targetId = targetId,
        linkType = LinkType.RELATED_TO
    )

    private fun assemble(
        icon: String,
        aggregates: DetailAggregates,
        dayRows: List<LifeItemDao.TemplateDayRow> = emptyList(),
        fieldsConfig: String = "[]",
        fieldsData: String = "{}"
    ): DetailUi = DetailAssembler.assemble(
        item = LifeItem(id = 1, templateId = 1, title = "test", fieldsData = fieldsData),
        template = LifeTemplate(
            id = 1,
            name = "test",
            category = "记录",
            icon = icon,
            color = "#3F51B5",
            fieldsConfig = fieldsConfig,
            layoutType = "card",
            availableLayouts = """["card"]""",
            statusFlowConfig = "{}",
            linkConfig = "{}"
        ),
        dayRows = dayRows,
        aggregates = aggregates
    )

    private fun atTime(hour: Int, minute: Int): Long = java.time.LocalDateTime.of(2026, 9, 24, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
