package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.domain.model.AssetStatus
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
        fun assetDao(): com.palmnote.data.db.dao.AssetDao
        fun goalDao(): com.palmnote.data.db.dao.GoalDao
        fun vaultDao(): com.palmnote.feature.vault.VaultDao
    }

    private var scope: CoroutineScope? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
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
                val assetDao = entryPoint.assetDao()
                val goalDao = entryPoint.goalDao()
                val vaultDao = entryPoint.vaultDao()

                val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
                val monthlyExpense = billDao.getMonthlyExpense(yearMonth).first() ?: 0L
                val monthlyIncome = billDao.getMonthlyIncome(yearMonth).first() ?: 0L
                val activeAssetCount = assetDao.getAssetCountByStatus(AssetStatus.HELD).first() ?: 0
                val vaultCount = vaultDao.countEntriesFlow().first() ?: 0

                val goals = goalDao.getAllGoals().first()
                val goalCount = goals.size
                val completedGoalCount = goals.count { it.currentCount >= it.totalCount }

                for (appWidgetId in appWidgetIds) {
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

                    val views = RemoteViews(context.packageName, R.layout.widget_dashboard_unified)

                    val datePattern = context.getString(R.string.widget_date_format_cn)
                    views.setTextViewText(R.id.widget_dashboard_date, LocalDate.now().format(DateTimeFormatter.ofPattern(datePattern)))
                    views.setTextViewText(R.id.widget_dashboard_expense, WidgetHelper.formatMoney(monthlyExpense))
                    views.setTextViewText(R.id.widget_dashboard_income, WidgetHelper.formatMoney(monthlyIncome))
                    views.setTextViewText(R.id.widget_dashboard_assets, "$activeAssetCount")
                    views.setTextViewText(R.id.widget_dashboard_goals, "$completedGoalCount/$goalCount")
                    views.setTextViewText(R.id.widget_dashboard_vault, "$vaultCount")

                    val isLarge = width >= 380 && height >= 180

                    views.setViewVisibility(R.id.widget_stats_row2, if (isLarge) View.VISIBLE else View.GONE)
                    views.setViewVisibility(R.id.widget_quick_actions, if (isLarge) View.VISIBLE else View.GONE)

                    if (isLarge) {
                        views.setOnClickPendingIntent(
                            R.id.widget_action_bill,
                            WidgetHelper.createPendingIntent(context, 2001 + appWidgetId, "bill")
                        )
                        views.setOnClickPendingIntent(
                            R.id.widget_action_asset,
                            WidgetHelper.createPendingIntent(context, 3001 + appWidgetId, "asset")
                        )
                        views.setOnClickPendingIntent(
                            R.id.widget_action_todo,
                            WidgetHelper.createPendingIntent(context, 4001 + appWidgetId, "life")
                        )
                    }

                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, appWidgetId, "dashboard")
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
}
