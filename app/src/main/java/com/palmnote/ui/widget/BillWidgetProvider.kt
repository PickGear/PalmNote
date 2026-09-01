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
import java.time.temporal.ChronoUnit

class BillWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun billDao(): com.palmnote.data.db.dao.BillDao
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

                val now = LocalDate.now()
                val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(now)
                val monthlyExpense = billDao.getMonthlyExpense(yearMonth).first() ?: 0L
                val monthlyIncome = billDao.getMonthlyIncome(yearMonth).first() ?: 0L
                val dayOfMonth = now.dayOfMonth
                val daysInMonth = now.lengthOfMonth()

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_bill_unified)

                    views.setTextViewText(R.id.widget_bill_month, context.getString(R.string.widget_month_format, now.monthValue))
                    views.setTextViewText(R.id.widget_expense_amount, WidgetHelper.formatMoney(monthlyExpense))
                    views.setTextViewText(R.id.widget_income_amount, WidgetHelper.formatMoney(monthlyIncome))
                    views.setTextViewText(R.id.widget_days_in_month, context.getString(R.string.widget_days_elapsed, dayOfMonth, daysInMonth))

                    val hasData = monthlyExpense > 0 || monthlyIncome > 0
                    views.setViewVisibility(R.id.widget_content_with_data, if (hasData) View.VISIBLE else View.GONE)
                    views.setViewVisibility(R.id.widget_empty_state, if (hasData) View.GONE else View.VISIBLE)

                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, appWidgetId, "bill")
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
