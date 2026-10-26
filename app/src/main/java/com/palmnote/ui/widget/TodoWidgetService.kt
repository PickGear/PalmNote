package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 待办 4×3 的「分段议程」列表（v4 设计稿）：今天 / 逾期 / 明天 / 之后 四段，
 * 段头带计数，逾期段红色警示；行 = 方框 + 名称 + 时间（今天给 HH:mm，其余给 M/d）。
 *
 * 行内容与点击行为在 [TodoViewsFactory] 里构造；条目点击走集合的
 * `setPendingIntentTemplate` + 行内 fill-in intent（集合里的行不能各自持有 PendingIntent）。
 */
class TodoWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodoViewsFactory(
        context = applicationContext,
        appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    companion object {
        const val EXTRA_APPWIDGET_ID = "widget_todo_appwidget_id"
    }
}

/** 议程的一行：段头或待办行（列表扁平化后逐行出 RemoteViews）。 */
internal sealed class AgendaRow {
    data class Header(val label: String, val count: Int, val section: AgendaSection) : AgendaRow()
    data class Item(val item: LifeItem, val section: AgendaSection) : AgendaRow()
}

/** 议程分段（设计稿顺序：今天 → 逾期 → 明天 → 之后）。 */
internal enum class AgendaSection { TODAY, OVERDUE, TOMORROW, LATER }

internal class TodoViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<AgendaRow> = emptyList()

    /**
     * 该实例的**内容色调**（[WidgetWallpaper.toneOnly]）。列表行由这里单独出 RemoteViews，
     * 走不到发布出口的色调包装，所以自己取一次；行上的字才会和卡上的字同一个明暗。
     */
    private var tone: WidgetTone? = null

    /** 该实例的不透明度：只乘进底色（行底），与卡面同一档，避免「卡边透、行里实色板」。 */
    private var opacity: Float = 1f

    /** 带色调的 Context：行上的取色（警示红、色族 hue）与 [widgetViews] 都走它。 */
    private fun themed(): Context = WidgetToneContext.of(context, tone, opacity)

    override fun onCreate() = Unit

    /**
     * 取数并分段。这里读库是阻塞的，但运行在桌面的 binder 线程上（不是广播/主线程），
     * 也是 RemoteViewsService 的标准用法——广播线程那边仍然只走挂起路径。
     */
    override fun onDataSetChanged() {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TodoWidgetService.WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesManager()
        runBlocking {
            tone = WidgetWallpaper.toneOnly(context, prefs, appWidgetId)
            opacity = prefs.widgetOpacityFor(appWidgetId).first()
            val board = WidgetData.fetchTodoBoard(
                entryPoint.lifeItemDao(),
                entryPoint.lifeTemplateDao(),
                includeDemo = prefs.lifeDemoMode.first(),
                demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
            )
            rows = buildAgenda(board.items, LocalDate.now())
        }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        return when (val row = rows.getOrNull(position) ?: return null) {
            is AgendaRow.Header -> headerViews(row)
            is AgendaRow.Item -> itemViews(row)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    /** 段头用负数 id（条目 id 是正数自增，不会撞车），保证桌面动画/缓存不串行。 */
    override fun getItemId(position: Int): Long = when (val row = rows.getOrNull(position)) {
        is AgendaRow.Item -> row.item.id
        is AgendaRow.Header -> -1L * row.label.hashCode()
        null -> position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    /** 按设计稿顺序拍平：今天 → 逾期 → 明天 → 之后，空段跳过；只列未完成条目。 */
    private fun buildAgenda(items: List<LifeItem>, today: LocalDate): List<AgendaRow> {
        val zone = ZoneId.systemDefault()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayAfter = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val pending = items.filter { it.status != "COMPLETED" }

        val rows = mutableListOf<AgendaRow>()
        for ((section, list) in buckets(pending, todayStart, tomorrowStart, dayAfter)) {
            if (list.isEmpty()) continue
            rows += AgendaRow.Header(context.getString(sectionLabelRes(section)), list.size, section)
            list.forEach { rows += AgendaRow.Item(it, section) }
        }
        return rows
    }

    /** 四段的条目分桶与组内排序（今天/明天按时间再按手动序，逾期按日期）。 */
    private fun buckets(
        pending: List<LifeItem>,
        todayStart: Long,
        tomorrowStart: Long,
        dayAfter: Long
    ): List<Pair<AgendaSection, List<LifeItem>>> {
        // dueDate 来自 core 模块，跨模块不能 smart-cast，先取局部 val
        val byTime = compareBy<LifeItem>({ it.dueTime ?: Int.MAX_VALUE }, { it.sortOrder })
        return listOf(
            AgendaSection.TODAY to pending
                .filter { val due = it.dueDate; due != null && due >= todayStart && due < tomorrowStart }
                .sortedWith(byTime),
            AgendaSection.OVERDUE to pending
                .filter { val due = it.dueDate; due != null && due < todayStart }
                .sortedBy { it.dueDate },
            AgendaSection.TOMORROW to pending
                .filter { val due = it.dueDate; due != null && due >= tomorrowStart && due < dayAfter }
                .sortedWith(byTime),
            AgendaSection.LATER to pending
                .filter { val due = it.dueDate; due == null || due >= dayAfter }
        )
    }

    private fun sectionLabelRes(section: AgendaSection): Int = when (section) {
        AgendaSection.TODAY -> R.string.widget_today
        AgendaSection.OVERDUE -> R.string.widget_overdue
        AgendaSection.TOMORROW -> R.string.widget_tomorrow
        AgendaSection.LATER -> R.string.widget_todo_sect_later
    }

    private fun headerViews(row: AgendaRow.Header): RemoteViews {
        val views = widgetViews(themed(), R.layout.widget_todo_agenda_header)
        views.setTextViewText(R.id.widget_todo_seghd_label, row.label)
        views.setTextViewText(
            R.id.widget_todo_seghd_count,
            context.getString(R.string.widget_todo_sect_count, row.count)
        )
        // 段头配色按稿子：逾期段红（.seghd.rd），今天段 var(--h1)（计数 .em 恒为次级色，不跟着变）
        when (row.section) {
            AgendaSection.OVERDUE ->
                views.setTextColor(R.id.widget_todo_seghd_label, themed().getColor(R.color.widget_risk_red))
            AgendaSection.TODAY ->
                views.setTextColor(R.id.widget_todo_seghd_label, WidgetData.colorFamily(themed(), 0).hue)
            else -> Unit
        }
        return views
    }

    private fun itemViews(row: AgendaRow.Item): RemoteViews =
        WidgetHelper.todoAgendaRowViews(
            themed(),
            row.item,
            timeTextOf(row),
            row.section == AgendaSection.OVERDUE
        )

    /**
     * 时间列（设计稿口径）：今天给 HH:mm（没填时间就空着，段头已说明）；
     * 逾期给「昨天」或 M/d；明天与之后给 M/d。
     */
    private fun timeTextOf(row: AgendaRow.Item): String {
        val zone = ZoneId.systemDefault()
        val due = row.item.dueDate ?: return ""
        val date = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        val time = row.item.dueTime
        return when (row.section) {
            AgendaSection.TODAY -> time?.let { "%02d:%02d".format(Locale.US, it / 60, it % 60) } ?: ""
            AgendaSection.OVERDUE ->
                if (date == LocalDate.now().minusDays(1)) context.getString(R.string.widget_yesterday)
                else date.format(DateTimeFormatter.ofPattern("M/d"))
            else -> date.format(DateTimeFormatter.ofPattern("M/d"))
        }
    }
}
