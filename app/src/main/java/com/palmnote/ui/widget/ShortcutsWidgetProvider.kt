package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// 快捷入口组件：四个直达入口（记一笔/账单/待办/密码本）；记一笔格主题色运行时着色
class ShortcutsWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId, accent)
        }
    }

    private fun bindViews(context: Context, appWidgetId: Int, accent: WidgetData.AccentTheme): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_shortcuts_unified)
        views.setInt(R.id.widget_sc_add_bg, "setColorFilter", accent.accent)

        views.setOnClickPendingIntent(
            R.id.widget_sc_add,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_ADD + appWidgetId, WidgetDeepLink.TAB_ADD_BILL)
        )
        views.setOnClickPendingIntent(
            R.id.widget_sc_bill,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_BILL + appWidgetId, WidgetDeepLink.TAB_BILL)
        )
        views.setOnClickPendingIntent(
            R.id.widget_sc_todo,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_TODO + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_sc_vault,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_VAULT + appWidgetId, WidgetDeepLink.TAB_VAULT)
        )
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_ROOT + appWidgetId, WidgetDeepLink.TAB_DASHBOARD)
        )
        return views
    }
}
