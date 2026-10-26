package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.util.StreakEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * 待办清单组件（v22）：头部「今天 N/M」+ 今日事项行（勾选框 / 事项 / 时间）。
 *
 * 数据口径（与 [WidgetData.fetchTodoBoard] 同源）：**只列今天到期的事项**，
 * 按到期时间升序（同一天的按时间，无时间的排后）；「今天 N/M」= 今日未完成 N / 今日共 M。
 * 点行直接勾选/取消（[TodoToggleReceiver]），点卡片进「今日安排」完整清单。
 *
 * 四档构图按 [WidgetSizeClass] 切档：
 * - WIDE 4×2：三行可点胶囊（淡彩底 + 勾选框 + 事项 + 时间）；
 * - SQUARE 2×2：hero「N 项」+ 三行素清单（同三项，行色统一卡族）；
 * - BAR 4×1：三列读数（今天 / 已完成 / 下一件）+ 完成度条；
 * - TALL 4×4（v37 §2-3）：hero + meta「今天 x/y」+ 四 KPI + 近 7 天完成柱图 + footer 最近一件。
 *
 * 胶囊行色按行位取 [ROW_FAMILIES]（稿内取色：绿/蓝/琥珀三行），
 * 完成勾选框恒绿（[R.drawable.widget_v22_ck_on]）—— 与行色解耦，
 * 「勾了」这件事靠固定绿表达，不随行色漂移。
 */
// open：尺寸变体（TodoMini/TodoWide）薄子类继承，复用同一套取数与切档逻辑
open class TodoWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext.applicationContext, WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesManager()
        // 演示感知互斥：开＝只看示例，关＝只看自己的（与其他出口同一口径）
        val board = WidgetData.fetchTodoBoard(
            entryPoint.lifeItemDao(),
            entryPoint.lifeTemplateDao(),
            includeDemo = prefs.lifeDemoMode.first(),
            demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
        )

        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                bindViews(context, appWidgetId, board, width, height)
            }
        }
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        board: WidgetData.TodoBoard,
        widthDp: Int = DEFAULT_WIDTH_DP,
        heightDp: Int = DEFAULT_HEIGHT_DP
    ): RemoteViews {
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_todo_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_todo_v22_square
            WidgetSizeClass.BAR -> R.layout.widget_todo_v22_bar
            WidgetSizeClass.CHIP -> R.layout.widget_todo_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        if (size == WidgetSizeClass.TALL) {
            bindTall4x4(context, views, board)
            bindCardLink(context, views, appWidgetId)
            return views
        }
        bindChrome(context, views, board)
        // 整卡出口四档统一挂（行点击挂在行自己的 id 上，与 widget_layout 不冲突）
        bindCardLink(context, views, appWidgetId)
        when (size) {
            WidgetSizeClass.WIDE ->
                bindWide(context, views, board)
            WidgetSizeClass.SQUARE -> bindSquare(context, views, board)
            WidgetSizeClass.BAR -> bindBar(context, views, board)
            WidgetSizeClass.CHIP -> bindBar(context, views, board)
            else -> {}
        }
        return views
    }

    /** 四档共用的头部：徽章（族色 c1）+ 标题 + 「今天 N/M」。 */
    private fun bindChrome(context: Context, views: RemoteViews, board: WidgetData.TodoBoard) {
        views.setInt(R.id.widget_todo_badge, "setColorFilter", familyD(context, 0))
        views.setTextViewText(R.id.widget_todo_title, context.getString(R.string.widget_todo_title_short))
        views.setTextViewText(
            R.id.widget_todo_meta,
            context.getString(
                R.string.widget_todo_meta_today,
                board.todayTotal - board.todayDone,
                board.todayTotal
            )
        )
    }

    /** 4×2：三行可点胶囊。 */
    private fun bindWide(context: Context, views: RemoteViews, board: WidgetData.TodoBoard) {
        val rows = board.todayItems.take(MAX_ROWS)
        views.removeAllViews(R.id.widget_todo_rows_host)
        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.widget_todo_rows_host, View.GONE)
            views.setViewVisibility(R.id.widget_todo_empty, View.VISIBLE)
            return
        }
        views.setViewVisibility(R.id.widget_todo_rows_host, View.VISIBLE)
        views.setViewVisibility(R.id.widget_todo_empty, View.GONE)
        rows.forEachIndexed { index, item ->
            val row = widgetViews(context, R.layout.widget_todo_v22_row)
            val familyIdx = ROW_FAMILIES[index % ROW_FAMILIES.size]
            bindRow(context, row, item, familyIdx)
            row.setOnClickPendingIntent(
                R.id.widget_todo_row,
                TodoToggleReceiver.togglePendingIntent(context, item.id)
            )
            views.addView(R.id.widget_todo_rows_host, row)
        }
    }

    /** 2×2：hero「N 项」+ 三行素清单（时间统一卡族 ink）。 */
    private fun bindSquare(context: Context, views: RemoteViews, board: WidgetData.TodoBoard) {
        views.setTextViewText(
            R.id.widget_todo_hero,
            context.getString(R.string.widget_todo_sect_count, board.todayTotal - board.todayDone)
        )
        val rows = board.todayItems.take(MAX_ROWS)
        views.removeAllViews(R.id.widget_todo_rows_host)
        if (rows.isEmpty()) {
            views.setViewVisibility(R.id.widget_todo_rows_host, View.GONE)
            views.setViewVisibility(R.id.widget_todo_empty, View.VISIBLE)
            return
        }
        views.setViewVisibility(R.id.widget_todo_rows_host, View.VISIBLE)
        views.setViewVisibility(R.id.widget_todo_empty, View.GONE)
        rows.forEach { item ->
            val row = widgetViews(context, R.layout.widget_todo_v22_trow)
            bindRow(context, row, item, familyIdx = 0, capsule = false)
            row.setOnClickPendingIntent(
                R.id.widget_todo_trow,
                TodoToggleReceiver.togglePendingIntent(context, item.id)
            )
            views.addView(R.id.widget_todo_rows_host, row)
        }
    }

    /** 4×1：三列读数 + 完成度条。 */
    /**
     * 4×4（v37 §2-3）：hero 剩余项数 + meta「今天 x/y」+ 四 KPI（今天/本周/完成率/连续）+
     * 近 7 天完成柱图（按到期日归天的完成数，峰值橙）+ footer 最近一件（可点勾选）。
     * 今天 0 项走空白态（稿 §11）：不摆空柱图与 0 值 KPI。
     */
    internal fun bindTall4x4(context: Context, views: RemoteViews, board: WidgetData.TodoBoard) {
        val remaining = (board.todayTotal - board.todayDone).coerceAtLeast(0)
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_check_white,
            title = context.getString(R.string.widget_todo_title_short),
            meta = context.getString(R.string.widget_todo_meta_today, remaining, board.todayTotal),
            familyIdx = 0
        )
        WidgetV22.bindTallHero(context, views, context.getString(R.string.widget_todo_sect_count, remaining), null)
        if (board.todayItems.isEmpty()) {
            WidgetV22.bindEmpty(
                context, views,
                iconRes = R.drawable.widget_ic_check_white,
                title = context.getString(R.string.widget_todo_empty_today),
                hint = context.getString(R.string.widget_dash_empty_hint),
                familyIdx = 0
            )
            WidgetV22.tallSections(context, views, "empty")
            return
        }
        val series = todoDailySeries(board)
        bindTallKpis(context, views, board, series)
        bindTallChart(context, views, series.daily)
        val footerItem = board.todayItems.firstOrNull { it.status != "COMPLETED" }
        bindTallFooter(context, views, footerItem)
        WidgetV22.tallSections(
            context, views,
            "kpis",
            "chart",
            if (footerItem != null) "footer" else ""
        )
    }

    /** 4×4 KPI：今天（剩余）/ 本周完成 / 完成率 / 连续天数。 */
    private fun bindTallKpis(
        context: Context,
        views: RemoteViews,
        board: WidgetData.TodoBoard,
        series: TodoDailySeries
    ) {
        val remaining = (board.todayTotal - board.todayDone).coerceAtLeast(0)
        val percent = if (board.todayTotal == 0) 0 else board.todayDone * 100 / board.todayTotal
        WidgetV22.bindKpis(
            context, views,
            listOf(
                WidgetV22.Kpi(context.getString(R.string.widget_todos_label), "$remaining", null, 0),
                WidgetV22.Kpi(context.getString(R.string.widget_habit_label_week), "${series.weekDone}", null, 0),
                WidgetV22.Kpi(context.getString(R.string.widget_todo_kpi_rate), "$percent%", null, 1),
                WidgetV22.Kpi(context.getString(R.string.widget_habit_label_streak), "${series.streak}", null, 3)
            )
        )
    }

    /** 4×4 柱图：近 7 天完成（按到期日归天；峰值橙 + 数值标；全 0 收起）。 */
    private fun bindTallChart(context: Context, views: RemoteViews, daily: List<Int>) {
        val max = daily.maxOrNull() ?: 0
        if (max <= 0) return
        // 并列峰值只认最靠今天的一根（同打卡口径）
        val peak = daily.lastIndexOf(max)
        val bars = daily.mapIndexed { i, v ->
            WidgetV22.ChartBar(
                percent = v * 100 / max,
                familyIdx = if (i == peak) 4 else 1,
                label = if (i == peak) "$v" else null,
                axis = WidgetV22.last7Axis()[i],
                // 轴末格 = 今天
                today = i == daily.lastIndex
            )
        }
        WidgetV22.bindChart(context, views, context.getString(R.string.widget_habit_chart_recent), null, bars)
    }

    /** 4×4 footer：最近一件未完成（勾选圈 + 名称 + 时刻）；没有未完成项时不绑（调用方收起）。 */
    private fun bindTallFooter(context: Context, views: RemoteViews, item: com.palmnote.data.db.entity.LifeItem?) {
        if (item == null) return
        val time = item.dueDate?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        } ?: ""
        WidgetV22.bindFooter(
            context, views,
            left = context.getString(R.string.widget_todo_recent),
            value = item.title,
            right = time,
            checkMark = true
        )
    }

    /** 近 7 天完成序列 + 本周合计 + 连击（完成条目按到期日归天，与打卡同口径）。 */
    private data class TodoDailySeries(val daily: List<Int>, val weekDone: Int, val streak: Int)

    private fun todoDailySeries(board: WidgetData.TodoBoard): TodoDailySeries {
        val today = LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        val doneDays = board.items
            .filter { it.status == "COMPLETED" }
            .mapNotNull { item -> (item.dueDate ?: item.createdAt) }
            .map { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .filter { !it.isAfter(today) }
        val daily = (6 downTo 0).map { offset -> doneDays.count { it == today.minusDays(offset.toLong()) } }
        val weekStart = today.with(java.time.DayOfWeek.MONDAY)
        return TodoDailySeries(
            daily = daily,
            weekDone = doneDays.count { !it.isBefore(weekStart) && !it.isAfter(today) },
            streak = StreakEngine.compute(doneDays.toSet(), today).current
        )
    }

    private fun bindBar(context: Context, views: RemoteViews, board: WidgetData.TodoBoard) {
        val pending = board.todayTotal - board.todayDone
        views.setTextViewText(
            R.id.widget_todo_bar_today,
            context.getString(R.string.widget_todo_sect_count, pending)
        )
        views.setTextViewText(R.id.widget_todo_bar_done, "${board.todayDone}/${board.todayTotal}")
        val next = board.todayItems.firstOrNull { it.status != "COMPLETED" }
        if (next == null) {
            views.setTextViewText(R.id.widget_todo_bar_next_lbl, context.getString(R.string.widget_asset_dash))
            views.setTextViewText(R.id.widget_todo_bar_next, "")
        } else {
            views.setTextViewText(R.id.widget_todo_bar_next_lbl, next.title)
            views.setTextViewText(R.id.widget_todo_bar_next, timeText(next))
        }
        val percent = if (board.todayTotal == 0) 0 else board.todayDone * 100 / board.todayTotal
        views.setInt(R.id.widget_todo_prog_fill, "setColorFilter", familyD(context, 0))
        views.setInt(R.id.widget_todo_prog_fill, "setImageLevel", percent * 100)
    }

    /** 2×1：件数徽章 + 三个空点。 */


    /**
     * 一行待办：勾选框（空/已勾两种背景 + 白勾）+ 事项 + 时间。
     * `capsule` = 4×2 的胶囊行（淡彩底 + 时间着行族 ink）；素行只上时间色（卡族 ink）。
     */
    private fun bindRow(
        context: Context,
        row: RemoteViews,
        item: LifeItem,
        familyIdx: Int,
        capsule: Boolean = true
    ) {
        val done = item.status == "COMPLETED"
        val ckId = if (capsule) R.id.widget_todo_row_ck else R.id.widget_todo_trow_ck
        val nameId = if (capsule) R.id.widget_todo_row_name else R.id.widget_todo_trow_name
        val timeId = if (capsule) R.id.widget_todo_row_time else R.id.widget_todo_trow_time
        row.setInt(ckId, "setBackgroundResource", if (done) R.drawable.widget_v22_ck_on else R.drawable.widget_v22_ck_off)
        row.setTextViewText(ckId, if (done) CHECK_MARK else "")
        row.setTextViewText(nameId, item.title)
        row.setTextViewText(timeId, timeText(item))
        row.setTextColor(timeId, familyInk(context, familyIdx))
        if (capsule) {
            row.setInt(R.id.widget_todo_row_bg, "setColorFilter", familyTint(context, familyIdx))
        }
    }

    /** 事项时间：今天到期的给 HH:mm（与 App 内待办行同口径）。 */
    private fun timeText(item: LifeItem): String {
        val due = item.dueDate ?: return ""
        val time = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        return time
    }

    /** 点卡片进「今日安排」完整清单（与 v4 出口一致）。 */
    private fun bindCardLink(context: Context, views: RemoteViews, appWidgetId: Int) {
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createLifeListPendingIntent(
                context,
                WidgetDeepLink.SEG_TODO + appWidgetId,
                "AGENDA"
            )
        )
    }

    private fun familyD(context: Context, index: Int) = WidgetV22.family(context, index).d

    private fun familyTint(context: Context, index: Int) = WidgetV22.family(context, index).tint

    private fun familyInk(context: Context, index: Int) = WidgetV22.family(context, index).ink

    companion object {
        /** 今天到期的事项最多列几行（两档大卡都是三行，稿口径）。 */
        const val MAX_ROWS = 3

        /** 组件声明的默认尺寸（4×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 勾选记号：勾选框里的白勾（RemoteViews 无矢量叠加，走文本字形）。 */
        const val CHECK_MARK = "✓"

        /** 胶囊行色（稿内取色）：绿 / 蓝 / 琥珀 —— 完成勾选框恒绿，与行色解耦。 */
        val ROW_FAMILIES = intArrayOf(1, 0, 2)
    }
}
