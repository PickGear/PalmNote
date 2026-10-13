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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * 待办 4×3（v4 设计稿）：左侧竖进度轴给「今日完成 N/M」与完成度条，右侧「分段议程」
 * （今天/逾期/明天/之后，由 [TodoWidgetService] 分段提供），底部「本周已完成」。
 * 这里只管轴、逾期 pill 与页脚的绑定。
 */
class TodoWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesManager()
        // 演示感知互斥：开＝只看示例，关＝只看自己的（与其他出口同一口径）
        val board = WidgetData.fetchTodoBoard(
            entryPoint.lifeItemDao(),
            entryPoint.lifeTemplateDao(),
            includeDemo = prefs.lifeDemoMode.first(),
            demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
        )

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                when {
                    height < STRIP_HEIGHT_DP && width >= WIDE_WIDTH_DP ->
                        bindWideViews(context, appWidgetId, board, java.time.LocalDate.now())
                    height < STRIP_HEIGHT_DP ->
                        bindMinimalViews(context, appWidgetId, board, java.time.LocalDate.now())
                    else -> bindViews(context, appWidgetId, board)
                }
            }
        }
    }

    /** 2×1 极简档：「今日待办 + 逾期胶囊 / 2 / 4 / 下一项」。 */
    private fun bindMinimalViews(
        context: Context,
        appWidgetId: Int,
        board: WidgetData.TodoBoard,
        today: java.time.LocalDate
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_todo_minimal)
        bindStripCommon(context, views, board, today, R.id.widget_todo_min_done, R.id.widget_todo_min_total, R.id.widget_todo_min_overdue)
        val next = nextPendingText(context, board, today)
        views.setViewVisibility(R.id.widget_todo_min_next, if (next == null) View.GONE else View.VISIBLE)
        if (next != null) views.setTextViewText(R.id.widget_todo_min_next, next)
        bindStripLink(context, views, appWidgetId)
        return views
    }

    /** 4×1 横条：左「今日待办 · 周N + 2 / 4」，中「完成度条 + 下一项」，右「逾期胶囊」。 */
    private fun bindWideViews(
        context: Context,
        appWidgetId: Int,
        board: WidgetData.TodoBoard,
        today: java.time.LocalDate
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_todo_wide)
        bindStripCommon(
            context, views, board, today,
            R.id.widget_todo_wide_done, R.id.widget_todo_wide_total, R.id.widget_todo_wide_overdue
        )
        // 左栏标签带星期（zh=周一 / en=Mon，跟系统 locale 走）
        views.setTextViewText(
            R.id.widget_todo_wide_label,
            context.getString(
                R.string.widget_todo_today_week,
                today.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            )
        )
        // 完成度条与 4×3 左轴同一根（track + clip 填充，level 定宽度）
        val progressFamily = WidgetData.colorFamily(context, 1)
        val percent = if (board.todayTotal == 0) 0 else board.todayDone * 100 / board.todayTotal
        views.setInt(R.id.widget_todo_wide_bar, "setColorFilter", progressFamily.hue)
        views.setInt(R.id.widget_todo_wide_bar, "setImageLevel", percent * 100)
        val next = nextPendingText(context, board, today)
        views.setViewVisibility(R.id.widget_todo_wide_next, if (next == null) View.GONE else View.VISIBLE)
        if (next != null) views.setTextViewText(R.id.widget_todo_wide_next, next)
        bindStripLink(context, views, appWidgetId)
        return views
    }

    /** 极简/横条共用的「N / M + 逾期胶囊」：逾期 = 到期早于今天且未完成。 */
    private fun bindStripCommon(
        context: Context,
        views: RemoteViews,
        board: WidgetData.TodoBoard,
        today: java.time.LocalDate,
        doneId: Int,
        totalId: Int,
        overdueId: Int
    ) {
        views.setTextViewText(doneId, "${board.todayDone}")
        views.setTextViewText(totalId, "/${board.todayTotal}")
        val todayStart = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val overdue = board.items.count { item ->
            val due = item.dueDate
            due != null && due < todayStart && item.status != "COMPLETED"
        }
        views.setViewVisibility(overdueId, if (overdue > 0) View.VISIBLE else View.GONE)
        if (overdue > 0) {
            views.setTextViewText(overdueId, context.getString(R.string.widget_overdue_count, overdue))
            views.setTextColor(overdueId, WidgetData.colorFamily(context, 0).hue)
        }
    }

    /** 「下一项 标题 · 时间」：今天到期的给 HH:mm，其余给 M/d；没有待办返回 null。 */
    private fun nextPendingText(context: Context, board: WidgetData.TodoBoard, today: java.time.LocalDate): String? {
        val next = board.items.firstOrNull { it.status != "COMPLETED" } ?: return null
        val zone = java.time.ZoneId.systemDefault()
        val due = next.dueDate
        val suffix = if (due != null) {
            val dateTime = java.time.Instant.ofEpochMilli(due).atZone(zone)
            val pattern = if (dateTime.toLocalDate() == today) "HH:mm" else "M/d"
            " · ${dateTime.format(java.time.format.DateTimeFormatter.ofPattern(pattern))}"
        } else {
            ""
        }
        return context.getString(R.string.widget_todo_next, "${next.title}$suffix")
    }

    /** 极简/横条共用出口：点卡片进今日安排清单。 */
    private fun bindStripLink(context: Context, views: RemoteViews, appWidgetId: Int) {
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createLifeListPendingIntent(context, WidgetDeepLink.SEG_TODO + appWidgetId, "AGENDA")
        )
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        board: WidgetData.TodoBoard
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_todo_unified)
        // 装饰色走色族（不占全局强调色）：徽章用绿，进度条用蓝（稿子如此）
        val badgeFamily = WidgetData.colorFamily(context, 2)
        val progressFamily = WidgetData.colorFamily(context, 1)
        views.setInt(R.id.widget_todo_badge, "setColorFilter", badgeFamily.hue)

        // 进度轴是「今日完成」（清单本身是全部活跃待办，两者口径不同，与稿子一致）
        val done = board.todayDone
        val total = board.todayTotal
        val percent = if (total == 0) 0 else done * 100 / total
        views.setTextViewText(R.id.widget_todo_done, "$done")
        views.setTextViewText(R.id.widget_todo_total, "/$total")
        // 完成度条：填充是白底 clip，宽度由 level 定（RemoteViews 改不了控件尺寸）
        views.setInt(R.id.widget_todo_progress_fill, "setColorFilter", progressFamily.hue)
        views.setInt(R.id.widget_todo_progress_fill, "setImageLevel", percent * 100)
        bindDonePreview(context, views, board)
        views.setTextColor(R.id.widget_todo_done_more, progressFamily.hue)
        // v4 设计稿：明细上方给「已完成 N 项」计数
        views.setTextViewText(
            R.id.widget_todo_done_count,
            context.getString(R.string.widget_todo_done_count, done)
        )
        // 没有待办就不摆这根轴，空态由列表区的空文案说
        val hasTodos = board.items.isNotEmpty()
        views.setViewVisibility(R.id.widget_todo_progress_col, if (hasTodos) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_todo_divider, if (hasTodos) View.VISIBLE else View.GONE)
        bindAgendaChrome(context, views, board)

        // 滚动列表：数据由 TodoWidgetService 提供，显示几行交给桌面自己决定。
        // 别对 ListView 调 removeAllViews —— AdapterView 不支持，会抛 UnsupportedOperationException

        views.setRemoteAdapter(
            R.id.widget_todo_list,
            android.content.Intent(context, TodoWidgetService::class.java)
                .putExtra(TodoWidgetService.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        views.setEmptyView(R.id.widget_todo_list, R.id.widget_todo_empty)
        // 行内点击的条目 id 由行的 fill-in intent 带过来，模板只出 action 与组件
        views.setPendingIntentTemplate(R.id.widget_todo_list, TodoToggleReceiver.toggleTemplatePendingIntent(context))

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_TODO + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        // 进度轴 → 直达「今日安排」完整清单
        views.setOnClickPendingIntent(
            R.id.widget_todo_progress_col,
            WidgetHelper.createLifeListPendingIntent(context, WidgetDeepLink.SEG_TODO + 100 + appWidgetId, "AGENDA")
        )
        // 「查看全部」与进度轴同目标（今日安排），号段错开防覆盖
        views.setOnClickPendingIntent(
            R.id.widget_todo_done_more,
            WidgetHelper.createLifeListPendingIntent(context, WidgetDeepLink.SEG_TODO + 200 + appWidgetId, "AGENDA")
        )
        return views
    }

    /**
     * v4 议程外围：头部「逾期 N」警示 pill + 底部「本周已完成 N 项」页脚。
     * 本周完成的口径与 App 内一致（LifeDetailViewModel.todoMetricsOf）：
     * COMPLETED 且 updatedAt 落在本周一以来。
     */
    private fun bindAgendaChrome(
        context: Context,
        views: RemoteViews,
        board: WidgetData.TodoBoard
    ) {
        val today = java.time.LocalDate.now()
        val todayStart = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val overdue = board.items.count { item ->
            val due = item.dueDate
            due != null && due < todayStart && item.status != "COMPLETED"
        }
        if (overdue > 0) {
            views.setViewVisibility(R.id.widget_todo_overdue_pill, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_todo_overdue_pill,
                context.getString(R.string.widget_overdue_short, overdue)
            )
        } else {
            views.setViewVisibility(R.id.widget_todo_overdue_pill, View.GONE)
        }
        val weekStart = today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val weekDone = board.items.count { item ->
            item.status == "COMPLETED" && item.updatedAt >= weekStart
        }
        views.setTextViewText(
            R.id.widget_todo_week_done,
            context.getString(R.string.widget_todo_week_done, weekDone)
        )
    }

    /**
     * v4：已完成明细重新可见（✓ 前缀 + 三级色，与滚动清单同款状态语言），
     * 最多 [DONE_PREVIEW_ROWS] 行；放不下时给「查看全部」入口。
     */
    private fun bindDonePreview(
        context: Context,
        views: RemoteViews,
        board: WidgetData.TodoBoard
    ) {
        val doneItems = board.items.filter { it.status == "COMPLETED" }.take(DONE_PREVIEW_ROWS)
        views.removeAllViews(R.id.widget_todo_done_list)
        doneItems.forEach { item ->
            val row = RemoteViews(context.packageName, R.layout.widget_todo_done_row)
            row.setTextViewText(
                R.id.widget_todo_done_text,
                context.getString(R.string.widget_done_prefix, item.title)
            )
            views.addView(R.id.widget_todo_done_list, row)
        }
        views.setViewVisibility(
            R.id.widget_todo_done_list,
            if (doneItems.isEmpty()) View.GONE else View.VISIBLE
        )
        views.setViewVisibility(
            R.id.widget_todo_done_more,
            if (board.todayDone > doneItems.size) View.VISIBLE else View.GONE
        )
    }

    companion object {
        /** 左栏已完成明细最多几行（4×3 空间预算）。 */
        const val DONE_PREVIEW_ROWS = 2

        /** 组件声明的默认尺寸（4×3），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 高度 <100dp 切横条/极简档；横条再要求宽度 ≥200dp（4 格）。 */
        const val STRIP_HEIGHT_DP = 100
        const val WIDE_WIDTH_DP = 200
    }
}
