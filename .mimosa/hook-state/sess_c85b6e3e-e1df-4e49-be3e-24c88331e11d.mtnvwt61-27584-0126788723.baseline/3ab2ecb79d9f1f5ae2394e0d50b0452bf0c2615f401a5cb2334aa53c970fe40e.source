package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.palmnote.app.R

// 快捷入口组件：四个直达入口（记一笔/账单/待办/密码本），纯静态无数据查询
class ShortcutsWidgetProvider : AppWidgetProvider() {

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

    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_shortcuts_unified)

            views.setOnClickPendingIntent(
                R.id.widget_sc_add,
                WidgetHelper.createPendingIntent(context, 11_000_000 + appWidgetId, "add_bill")
            )
            views.setOnClickPendingIntent(
                R.id.widget_sc_bill,
                WidgetHelper.createPendingIntent(context, 12_000_000 + appWidgetId, "bill")
            )
            views.setOnClickPendingIntent(
                R.id.widget_sc_todo,
                WidgetHelper.createPendingIntent(context, 13_000_000 + appWidgetId, "life")
            )
            views.setOnClickPendingIntent(
                R.id.widget_sc_vault,
                WidgetHelper.createPendingIntent(context, 14_000_000 + appWidgetId, "vault")
            )
            views.setOnClickPendingIntent(
                R.id.widget_layout,
                WidgetHelper.createPendingIntent(context, 650_000 + appWidgetId, "dashboard")
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
