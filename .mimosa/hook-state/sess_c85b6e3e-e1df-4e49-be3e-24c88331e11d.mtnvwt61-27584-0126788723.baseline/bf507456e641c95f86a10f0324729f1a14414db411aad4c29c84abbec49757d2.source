package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.palmnote.app.R

class QuickBillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_bill)

            views.setOnClickPendingIntent(
                R.id.widget_add_bill,
                WidgetHelper.createPendingIntent(context, 700_000 + appWidgetId, "add_bill")
            )
            views.setOnClickPendingIntent(
                R.id.widget_layout,
                WidgetHelper.createPendingIntent(context, 700_000 + appWidgetId, "add_bill")
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}