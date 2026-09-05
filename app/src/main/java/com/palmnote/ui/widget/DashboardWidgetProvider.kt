package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.domain.util.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DashboardWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): com.palmnote.data.db.dao.BillDao
        fun budgetDao(): com.palmnote.data.db.dao.BudgetDao
        fun goalDao(): com.palmnote.data.db.dao.GoalDao
        fun lifeItemDao(): com.palmnote.data.db.dao.LifeItemDao
        fun lifeTemplateDao(): com.palmnote.data.db.dao.LifeTemplateDao
        fun anniversaryDao(): com.palmnote.data.db.dao.AnniversaryDao
    }

    private var scope: CoroutineScope? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope?.cancel()
        scope = null
    }

    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val coroutineScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())

        coroutineScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val snapshot = fetchSnapshot(context, entryPoint)

                for (appWidgetId in appWidgetIds) {
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                    val isLarge = width >= 250 && height >= 180

                    val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)
                    bindSnapshot(context, views, snapshot)
                    views.setViewVisibility(R.id.widget_stats_row2, if (isLarge) View.VISIBLE else View.GONE)
                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, 600_000 + appWidgetId, "dashboard")
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                AppLogger.e("DashboardWidgetProvider", "Widget update failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private data class DashboardSnapshot(
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

        val todos = WidgetData.fetchTodayTodos(entryPoint.lifeItemDao(), entryPoint.lifeTemplateDao())

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

    private fun bindSnapshot(context: Context, views: RemoteViews, snapshot: DashboardSnapshot) {
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

    private fun formatAnniversarySub(context: Context, days: Long): String {
        return if (days == 0L) {
            context.getString(R.string.widget_today)
        } else {
            context.getString(R.string.widget_days_remaining_format, days)
        }
    }
}
