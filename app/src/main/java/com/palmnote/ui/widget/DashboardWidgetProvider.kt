package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DashboardWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): com.palmnote.data.db.dao.BillDao
        fun budgetDao(): com.palmnote.data.db.dao.BudgetDao
        fun goalDao(): com.palmnote.data.db.dao.GoalDao
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
        val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val isLarge = width >= 250 && height >= 180

            val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
            bindSnapshot(context, views, snapshot)
            // 实底卡改运行时着色：预算卡=主题色，目标卡=语义 Coral（实底白字，深浅共用）
            views.setInt(R.id.widget_dashboard_budget_bg, "setColorFilter", accent.accent)
            views.setInt(R.id.widget_dashboard_goal_bg, "setColorFilter", WidgetData.SEMANTIC_GOAL)
            views.setViewVisibility(R.id.widget_stats_row2, if (isLarge) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(
                R.id.widget_layout,
                WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_DASHBOARD + appWidgetId, WidgetDeepLink.TAB_DASHBOARD)
            )
            bindCardClicks(context, views, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * 分区热区：四张卡各自深链，子卡点击不再整块落回首页。
     * 预算卡 → 预算页；待办卡 → 生活页的今日清单；目标/纪念卡 → 生活页。
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

    /**
     * 卡片 viewId → 深链号段 + 目标页（可选生活清单模式）。
     * 号段必须互不相同，否则 FLAG_UPDATE_CURRENT 会让点击互相覆盖。
     */
    internal data class CardTarget(
        val viewId: Int,
        val segment: Int,
        val tab: String,
        val listMode: String? = null
    )

    internal fun cardTargets(): List<CardTarget> = listOf(
        CardTarget(R.id.widget_dashboard_budget_card, WidgetDeepLink.SEG_DASHBOARD_BUDGET, WidgetDeepLink.TAB_BUDGET),
        CardTarget(R.id.widget_dashboard_goal_card, WidgetDeepLink.SEG_DASHBOARD_GOAL, WidgetDeepLink.TAB_LIFE),
        // 待办卡看的是「今日还有几项」→ 直接落到今日清单（LifeFullListMode.AGENDA），不落首页
        CardTarget(
            R.id.widget_dashboard_todo_card,
            WidgetDeepLink.SEG_DASHBOARD_TODO,
            WidgetDeepLink.TAB_LIFE,
            listMode = "AGENDA"
        ),
        CardTarget(R.id.widget_dashboard_anniversary_card, WidgetDeepLink.SEG_DASHBOARD_ANNIVERSARY, WidgetDeepLink.TAB_LIFE)
    )

    internal data class DashboardSnapshot(
        val dateText: String,
        val budgetCardAmount: String,
        val budgetCardSub: String,
        val goalPct: String,
        val goalName: String,
        val todoRemaining: Int,
        val anniversaryTitle: String?,
        val anniversaryDays: Long
    )

    private suspend fun fetchSnapshot(context: Context, entryPoint: WidgetEntryPoint): DashboardSnapshot {
        val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
        val monthlyExpense = entryPoint.billDao().getMonthlyExpense(yearMonth).first() ?: 0L
        val monthlyIncome = entryPoint.billDao().getMonthlyIncome(yearMonth).first() ?: 0L
        val budget = entryPoint.budgetDao().getBudgetByMonth(yearMonth)
            ?: entryPoint.budgetDao().getLatestBudget().first()

        // 预算卡：有预算显示剩余，无预算显示结余
        val budgetCard = if (budget != null && budget.totalBudget > 0) {
            WidgetData.formatMoneyCompact(context, budget.totalBudget - monthlyExpense) to R.string.widget_budget_remaining
        } else {
            WidgetData.formatMoneyCompact(context, monthlyIncome - monthlyExpense) to R.string.widget_net_income
        }

        val goals = entryPoint.goalDao().getAllGoals().first()
        val activeGoal = goals.firstOrNull { it.currentCount < it.totalCount }
        val goalPct: String
        val goalName: String
        when {
            activeGoal != null -> {
                val pct = if (activeGoal.totalCount > 0) activeGoal.currentCount * 100 / activeGoal.totalCount else 0
                goalPct = "$pct%"
                goalName = activeGoal.title
            }
            goals.isNotEmpty() -> {
                goalPct = "100%"
                goalName = context.getString(R.string.widget_goal_all_done)
            }
            else -> {
                goalPct = "--"
                goalName = context.getString(R.string.widget_no_goals)
            }
        }

        val todos = WidgetData.fetchTodayTodos(
            entryPoint.lifeItemDao(),
            entryPoint.lifeTemplateDao(),
            includeDemo = entryPoint.preferencesManager().lifeDemoMode.first(),
            demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
        )

        val nextAnniversary = entryPoint.anniversaryDao().getAllAnniversaries().first()
            .mapNotNull { ann ->
                WidgetData.nextOccurrenceDaysIn(ann.solarDate, ann.isYearly)?.let { days -> ann.title to days }
            }
            .minByOrNull { it.second }

        return DashboardSnapshot(
            dateText = LocalDate.now().format(DateTimeFormatter.ofPattern(context.getString(R.string.widget_date_format_cn))),
            budgetCardAmount = budgetCard.first,
            budgetCardSub = context.getString(budgetCard.second),
            goalPct = goalPct,
            goalName = goalName,
            todoRemaining = todos.count { it.status != "COMPLETED" },
            anniversaryTitle = nextAnniversary?.first,
            anniversaryDays = nextAnniversary?.second ?: -1L
        )
    }

    internal fun bindSnapshot(context: Context, views: RemoteViews, snapshot: DashboardSnapshot) {
        views.setTextViewText(R.id.widget_dashboard_date, snapshot.dateText)
        views.setTextViewText(R.id.widget_dashboard_budget, snapshot.budgetCardAmount)
        views.setTextViewText(R.id.widget_dashboard_budget_sub, snapshot.budgetCardSub)
        views.setTextViewText(R.id.widget_dashboard_goal_pct, snapshot.goalPct)
        views.setTextViewText(R.id.widget_dashboard_goal_name, snapshot.goalName)

        views.setTextViewText(R.id.widget_dashboard_todo, "${snapshot.todoRemaining}")
        views.setTextViewText(
            R.id.widget_dashboard_todo_sub,
            context.getString(R.string.widget_todo_remaining_format, snapshot.todoRemaining)
        )

        if (snapshot.anniversaryTitle != null) {
            views.setViewVisibility(R.id.widget_dashboard_days_title, View.VISIBLE)
            views.setTextViewText(R.id.widget_dashboard_days_title, snapshot.anniversaryTitle)
            views.setTextViewText(R.id.widget_dashboard_days, "${snapshot.anniversaryDays}")
            views.setTextViewText(R.id.widget_dashboard_days_sub, formatAnniversarySub(context, snapshot.anniversaryDays))
        } else {
            views.setViewVisibility(R.id.widget_dashboard_days_title, View.GONE)
            views.setTextViewText(R.id.widget_dashboard_days, "--")
            views.setTextViewText(R.id.widget_dashboard_days_sub, context.getString(R.string.widget_no_anniversary))
        }
    }

    private fun formatAnniversarySub(context: Context, days: Long): String = if (days == 0L) {
        context.getString(R.string.widget_today)
    } else {
        // 复数走 getQuantityString（quantity 要 Int，格式化参数仍用 Long）
        context.resources.getQuantityString(
            R.plurals.widget_days_remaining_format,
            days.toInt(),
            days
        )
    }
}
