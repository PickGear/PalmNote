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

class BillWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): com.palmnote.data.db.dao.BillDao
        fun budgetDao(): com.palmnote.data.db.dao.BudgetDao
        fun preferencesManager(): com.palmnote.data.datastore.PreferencesManager
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
                val billDao = entryPoint.billDao()
                val budgetDao = entryPoint.budgetDao()

                val now = LocalDate.now()
                val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(now)
                val monthlyExpense = billDao.getMonthlyExpense(yearMonth).first() ?: 0L
                val monthlyIncome = billDao.getMonthlyIncome(yearMonth).first() ?: 0L
                val budget = budgetDao.getBudgetByMonth(yearMonth)
                    ?: budgetDao.getLatestBudget().first()
                val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_bill_unified)

                    views.setTextViewText(R.id.widget_income_amount, "+${WidgetData.formatMoneyCompact(context, monthlyIncome)}")
                    views.setTextViewText(R.id.widget_expense_amount, "-${WidgetData.formatMoneyCompact(context, monthlyExpense)}")
                    views.setTextColor(R.id.widget_add_btn, accent.accentText)

                    val bannerText: String
                    if (budget != null && budget.totalBudget > 0) {
                        views.setTextViewText(R.id.widget_budget_card_label, context.getString(R.string.widget_budget_remaining))
                        views.setTextViewText(R.id.widget_budget_amount, WidgetData.formatMoneyCompact(context, budget.totalBudget - monthlyExpense))
                        val usedPercent = (monthlyExpense * 100 / budget.totalBudget).toInt()
                        // 有消费记录时横幅带上最高消费分类（对应设计稿「餐饮预算已使用 82%」）
                        val topCategory = billDao.getTopExpenseCategory(yearMonth)
                        bannerText = if (topCategory != null && topCategory.total > 0) {
                            context.getString(R.string.widget_budget_top_format, topCategory.category, usedPercent)
                        } else {
                            context.getString(R.string.widget_budget_used_format, usedPercent)
                        }
                        // 横幅内嵌用量进度条（超支按 100% 封顶显示）
                        views.setViewVisibility(R.id.widget_budget_progress, View.VISIBLE)
                        views.setInt(R.id.widget_budget_progress, "setMax", 100)
                        views.setInt(R.id.widget_budget_progress, "setProgress", usedPercent.coerceIn(0, 100))
                    } else {
                        val balance = monthlyIncome - monthlyExpense
                        views.setTextViewText(R.id.widget_budget_card_label, context.getString(R.string.widget_net_income))
                        views.setTextViewText(R.id.widget_budget_amount, WidgetData.formatMoneyCompact(context, balance))
                        val sign = if (balance >= 0) "+" else ""
                        bannerText = context.getString(R.string.widget_month_balance_format, "$sign${WidgetData.formatMoneyCompact(context, balance)}")
                        views.setViewVisibility(R.id.widget_budget_progress, View.GONE)
                    }
                    views.setTextViewText(R.id.widget_budget_banner_text, bannerText)

                    val hasData = monthlyExpense > 0 || monthlyIncome > 0
                    views.setViewVisibility(R.id.widget_content_with_data, if (hasData) View.VISIBLE else View.GONE)
                    views.setViewVisibility(R.id.widget_empty_state, if (hasData) View.GONE else View.VISIBLE)

                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, 100_000 + appWidgetId, "bill")
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_add_btn,
                        WidgetHelper.createPendingIntent(context, 1_000_000 + appWidgetId, "add_bill")
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                AppLogger.e("BillWidgetProvider", "Widget update failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
