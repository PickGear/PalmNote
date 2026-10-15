package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.domain.util.LifeTemplateKind
import com.palmnote.domain.util.getKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 概览 4×3（v4 定稿）：2×2 大色块 tile + 每块一行语义化二级信息——
 * 预算=迷你用量条+本月已用百分比、目标=进度计数、待办=逾期红字（无逾期给今日已完成）、
 * 纪念日=目标日期与星期。右上角给「本周待办 N 项」（近 7 天内到期未完成）。
 */
class DashboardWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): com.palmnote.data.db.dao.BillDao
        fun budgetDao(): com.palmnote.data.db.dao.BudgetDao
        fun lifeItemDao(): com.palmnote.data.db.dao.LifeItemDao
        fun lifeTemplateDao(): com.palmnote.data.db.dao.LifeTemplateDao
        fun anniversaryDao(): com.palmnote.data.db.dao.AnniversaryDao
        fun preferencesManager(): com.palmnote.data.datastore.PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val snapshot = fetchSnapshot(context, entryPoint)

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, opacity ->
            // 强调色逐实例：配置了配色覆盖就用它，否则跟随应用主题包
            val accent = WidgetData.readAccentTheme(
                context, entryPoint.preferencesManager(),
                entryPoint.preferencesManager().widgetAccentThemeFor(appWidgetId).first()
            )
            bindDashboard(context, appWidgetId, snapshot, accent)
        }
    }

    /** 构建 2×2 tile 概览。 */
    private fun bindDashboard(
        context: Context,
        appWidgetId: Int,
        snapshot: DashboardSnapshot,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
        bindSnapshot(context, views, snapshot)
        val families = (0..3).map { WidgetData.colorFamily(context, it) }
        views.setInt(R.id.widget_dashboard_icon_bg, "setColorFilter", accent.accent)
        // 右上角「本周待办 N 项」用第一色族前景
        views.setTextColor(R.id.widget_dashboard_week_todo, families[0].hue)
        // 四块 tile：淡底 + 同族色点 + 同族数值
        val chips = listOf(
            Triple(R.id.widget_dashboard_budget_tint, R.id.widget_dashboard_budget_dot, R.id.widget_dashboard_budget),
            Triple(R.id.widget_dashboard_goal_tint, R.id.widget_dashboard_goal_dot, R.id.widget_dashboard_goal_pct),
            Triple(R.id.widget_dashboard_todo_tint, R.id.widget_dashboard_todo_dot, R.id.widget_dashboard_todo),
            Triple(R.id.widget_dashboard_anniversary_tint, R.id.widget_dashboard_anniversary_dot, R.id.widget_dashboard_days)
        )
        chips.forEachIndexed { index, (tintId, dotId, valueId) ->
            views.setInt(tintId, "setColorFilter", families[index].tint)
            views.setInt(dotId, "setColorFilter", families[index].hue)
            views.setTextColor(valueId, families[index].hue)
        }
        // 预算 tile 的迷你用量条：填充随用量走（无预算时条隐藏）
        if (snapshot.budgetPercent != null) {
            views.setInt(R.id.widget_dashboard_budget_bar_fill, "setColorFilter", families[0].hue)
            views.setInt(R.id.widget_dashboard_budget_bar_fill, "setImageLevel", snapshot.budgetPercent * 100)
            // track 保持 widget_v2_ring 灰色，不随色族染色（设计稿 --track 是中性灰底）
        } else {
            views.setViewVisibility(R.id.widget_dashboard_budget_bar_wrap, View.GONE)
        }
        // 目标 tile 的迷你进度条：与预算 tile 同一根。
        // pct 在 snapshot 里是展示文本（"64%"），这里回解析成 level；解析不出就当 0（空条不误导）
        views.setInt(R.id.widget_dashboard_goal_bar_fill, "setColorFilter", families[1].hue)
        views.setInt(
            R.id.widget_dashboard_goal_bar_fill,
            "setImageLevel",
            snapshot.goalPct.removeSuffix("%").toIntOrNull()?.times(100) ?: 0
        )
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_DASHBOARD + appWidgetId, WidgetDeepLink.TAB_DASHBOARD)
        )
        bindCardClicks(context, views, appWidgetId)
        return views
    }

    /**
     * 分区热区：四块 tile 各自深链。预算 → 预算页；待办 → 今日清单；目标/纪念 → 生活页。
     * 号段必须互不相同，否则 FLAG_UPDATE_CURRENT 会让点击互相覆盖。
     */
    internal fun bindCardClicks(context: Context, views: RemoteViews, appWidgetId: Int) {
        for (target in cardTargets()) {
            val requestCode = target.segment + appWidgetId
            val pendingIntent = if (target.listMode != null) {
                WidgetHelper.createLifeListPendingIntent(context, requestCode, target.listMode)
            } else {
                WidgetHelper.createPendingIntent(context, requestCode, target.tab)
            }
            views.setOnClickPendingIntent(target.viewId, pendingIntent)
        }
    }

    internal data class CardTarget(
        val viewId: Int,
        val segment: Int,
        val tab: String,
        val listMode: String? = null
    )

    internal fun cardTargets(): List<CardTarget> = listOf(
        CardTarget(R.id.widget_dashboard_budget_card, WidgetDeepLink.SEG_DASHBOARD_BUDGET, WidgetDeepLink.TAB_BUDGET),
        CardTarget(R.id.widget_dashboard_goal_card, WidgetDeepLink.SEG_DASHBOARD_GOAL, WidgetDeepLink.TAB_LIFE),
        // 待办 tile 看的是「今日还有几项」→ 直接落到今日清单（LifeFullListMode.AGENDA），不落首页
        CardTarget(
            R.id.widget_dashboard_todo_card,
            WidgetDeepLink.SEG_DASHBOARD_TODO,
            WidgetDeepLink.TAB_LIFE,
            listMode = "AGENDA"
        ),
        // 纪念日 tile 单独热区 → 生活页
        CardTarget(R.id.widget_dashboard_anniversary_card, WidgetDeepLink.SEG_DASHBOARD_ANNIVERSARY, WidgetDeepLink.TAB_LIFE)
    )

    internal data class DashboardSnapshot(
        /** 右上角：近 7 天内到期未完成的待办数。 */
        val weekTodoCount: Int,
        val budgetCardAmount: String,
        val budgetCardSub: String,
        val goalPct: String,
        val goalName: String,
        /** 目标进度计数文案（如 32/50）；无目标为 "--"。 */
        val goalCounts: String,
        /** 今日未完成待办数（tile 数值）。 */
        val todoRemaining: Int,
        /** 今日已完成待办数（无逾期时的二级信息）。 */
        val todoDoneToday: Int,
        /** 逾期未完成数（二级信息红字）。 */
        val overdueCount: Int,
        /** 纪念日 tile：目标日期与星期（如 11/1 · 周日）。 */
        val anniversaryDateText: String?,
        val anniversaryTitle: String?,
        val anniversaryDays: Long,
        /** 预算用量百分比；无预算为 null（用量条隐藏）。 */
        val budgetPercent: Int? = null
    )

    private suspend fun fetchSnapshot(context: Context, entryPoint: WidgetEntryPoint): DashboardSnapshot {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekEnd = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

        val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(today)
        val budgetTile = fetchBudget(context, entryPoint, yearMonth)
        val goals = fetchGoalProgress(context, entryPoint, entryPoint.preferencesManager().lifeDemoMode.first())
        val todoStats = fetchTodoStats(entryPoint, todayStart, weekEnd)
        val anniversary = fetchAnniversary(context, entryPoint, today)

        return DashboardSnapshot(
            weekTodoCount = todoStats.weekTodo,
            budgetCardAmount = budgetTile.amount,
            budgetCardSub = context.getString(budgetTile.labelRes),
            goalPct = goals.pctText,
            goalName = goals.name,
            goalCounts = goals.countsText,
            todoRemaining = todoStats.remaining,
            todoDoneToday = todoStats.doneToday,
            overdueCount = todoStats.overdue,
            anniversaryTitle = anniversary?.first,
            anniversaryDays = anniversary?.second ?: -1L,
            anniversaryDateText = anniversary?.third,
            budgetPercent = budgetTile.percent
        )
    }

    private data class BudgetTile(val amount: String, val labelRes: Int, val percent: Int?)

    /** 预算 tile：有预算显示剩余，无预算显示结余；迷你用量条只在真有预算时给值。 */
    private suspend fun fetchBudget(
        context: Context,
        entryPoint: WidgetEntryPoint,
        yearMonth: String
    ): BudgetTile {
        val monthlyExpense = entryPoint.billDao().getMonthlyExpense(yearMonth).first() ?: 0L
        val monthlyIncome = entryPoint.billDao().getMonthlyIncome(yearMonth).first() ?: 0L
        val budget = entryPoint.budgetDao().getBudgetByMonth(yearMonth)
            ?: entryPoint.budgetDao().getLatestBudget().first()
        return if (budget != null && budget.totalBudget > 0) {
            BudgetTile(
                WidgetData.formatMoneyCompact(context, budget.totalBudget - monthlyExpense),
                R.string.widget_budget_remaining,
                (monthlyExpense * 100 / budget.totalBudget).toInt().coerceIn(0, 100)
            )
        } else {
            BudgetTile(
                WidgetData.formatMoneyCompact(context, monthlyIncome - monthlyExpense),
                R.string.widget_net_income,
                null
            )
        }
    }

    private data class TodoStats(
        val remaining: Int,
        val doneToday: Int,
        val overdue: Int,
        val weekTodo: Int
    )

    /** 待办口径全走 fetchTodoBoard（与待办组件同源）：items=全部活跃+今日已完成。 */
    private suspend fun fetchTodoStats(
        entryPoint: WidgetEntryPoint,
        todayStart: Long,
        weekEnd: Long
    ): TodoStats {
        val board = WidgetData.fetchTodoBoard(
            entryPoint.lifeItemDao(),
            entryPoint.lifeTemplateDao(),
            includeDemo = entryPoint.preferencesManager().lifeDemoMode.first(),
            demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
        )
        val active = board.items
        // dueDate 是 core 模块的 public 属性，跨模块不能智能转换，先落局部 val
        val overdue = active.count {
            val due = it.dueDate
            due != null && due < todayStart && it.status != "COMPLETED"
        }
        val weekTodo = active.count {
            val due = it.dueDate
            it.status != "COMPLETED" && due != null && due < weekEnd
        }
        return TodoStats(
            remaining = (board.todayTotal - board.todayDone).coerceAtLeast(0),
            doneToday = board.todayDone,
            overdue = overdue,
            weekTodo = weekTodo
        )
    }

    /** 最近的一个纪念日 → (标题, 距今天数, 目标日期与星期)。 */
    private suspend fun fetchAnniversary(
        context: Context,
        entryPoint: WidgetEntryPoint,
        today: LocalDate
    ): Triple<String, Long, String>? {
        val next = entryPoint.anniversaryDao().getAllAnniversaries().first()
            .mapNotNull { ann ->
                WidgetData.nextOccurrenceDaysIn(ann.solarDate, ann.isYearly)?.let { days -> ann.title to days }
            }
            .minByOrNull { it.second } ?: return null
        val dateText = today.plusDays(next.second)
            .format(DateTimeFormatter.ofPattern(context.getString(R.string.widget_date_md_week)))
        return Triple(next.first, next.second, dateText)
    }

    /**
     * 目标进度：百分比文案、名称、进度计数（无目标时计数为 "--"）。
     * 选卡与文案在 [WidgetData.goalTileData]；演示互斥（`includeDemo`）与待办 tile 同口径。
     */
    private suspend fun fetchGoalProgress(
        context: Context,
        entryPoint: WidgetEntryPoint,
        includeDemo: Boolean
    ): WidgetData.GoalTileData {
        val noGoals = WidgetData.GoalTileData("--", context.getString(R.string.widget_no_goals), "--")
        // kind = GOAL 可能不止一张（自建模板也能选 flag 图标）：优先内置那张，取数才不会落到别人的模板上
        val templates = entryPoint.lifeTemplateDao().getAllVisibleTemplates().first()
        val template = (templates.firstOrNull { it.isBuiltin && it.getKind() == LifeTemplateKind.GOAL }
            ?: templates.firstOrNull { it.getKind() == LifeTemplateKind.GOAL }) ?: return noGoals
        val items = entryPoint.lifeItemDao()
            .getWidgetItemsByTemplate(template.id, includeDemo, com.palmnote.data.db.dao.LIFE_DEMO_META)
            .first()
        return WidgetData.goalTileData(
            template.fieldsConfig,
            items,
            context.getString(R.string.widget_goal_all_done)
        ) ?: noGoals
    }

    internal fun bindSnapshot(context: Context, views: RemoteViews, snapshot: DashboardSnapshot) {
        // 右上角：本周待办（近 7 天内到期未完成）
        views.setTextViewText(
            R.id.widget_dashboard_week_todo,
            context.getString(R.string.widget_week_todo_count, snapshot.weekTodoCount)
        )
        views.setTextViewText(R.id.widget_dashboard_budget, snapshot.budgetCardAmount)
        views.setTextViewText(R.id.widget_dashboard_budget_sub, snapshot.budgetCardSub)
        // 预算二级信息：本月已用 82%；无预算时用量条整块隐藏（数据层决定可见性）
        if (snapshot.budgetPercent != null) {
            views.setTextViewText(
                R.id.widget_dashboard_budget_used,
                context.getString(R.string.widget_budget_used_pct, snapshot.budgetPercent)
            )
        } else {
            views.setViewVisibility(R.id.widget_dashboard_budget_bar_wrap, View.GONE)
        }
        views.setTextViewText(R.id.widget_dashboard_goal_pct, snapshot.goalPct)
        views.setTextViewText(R.id.widget_dashboard_goal_name, snapshot.goalName)
        views.setTextViewText(R.id.widget_dashboard_goal_counts, snapshot.goalCounts)

        views.setTextViewText(
            R.id.widget_dashboard_todo,
            context.getString(R.string.widget_todo_sect_count, snapshot.todoRemaining)
        )
        // 待办二级信息：有逾期给红字逾期数，否则给今日已完成数
        if (snapshot.overdueCount > 0) {
            views.setTextViewText(
                R.id.widget_dashboard_todo_overdue,
                context.getString(R.string.widget_overdue_count, snapshot.overdueCount)
            )
            views.setTextColor(R.id.widget_dashboard_todo_overdue, context.getColor(R.color.widget_risk_red))
        } else {
            views.setTextViewText(
                R.id.widget_dashboard_todo_overdue,
                context.getString(R.string.widget_todo_done_today, snapshot.todoDoneToday)
            )
            views.setTextColor(R.id.widget_dashboard_todo_overdue, context.getColor(R.color.widget_v2_text_tertiary))
        }

        // 纪念日 tile：数值 = N 天，二级信息 = 目标日期与星期
        if (snapshot.anniversaryTitle != null) {
            views.setTextViewText(R.id.widget_dashboard_days_title, snapshot.anniversaryTitle)
            views.setTextViewText(
                R.id.widget_dashboard_days,
                "${snapshot.anniversaryDays} ${formatAnniversarySub(context, snapshot.anniversaryDays)}"
            )
            views.setTextViewText(R.id.widget_dashboard_anniv_date, snapshot.anniversaryDateText ?: "")
        } else {
            views.setTextViewText(R.id.widget_dashboard_days_title, context.getString(R.string.widget_no_anniversary))
            views.setTextViewText(R.id.widget_dashboard_days, "--")
            views.setTextViewText(R.id.widget_dashboard_anniv_date, "")
        }
    }

    /** 砖里的单位词：数字在主角位置，这里只给「天后 / days」这种量词。 */
    private fun formatAnniversarySub(context: Context, days: Long): String = if (days == 0L) {
        context.getString(R.string.widget_today)
    } else {
        // 复数走 getQuantityString（en 要区分 day/days）
        context.resources.getQuantityString(R.plurals.widget_days_unit, days.toInt(), days)
    }
}
