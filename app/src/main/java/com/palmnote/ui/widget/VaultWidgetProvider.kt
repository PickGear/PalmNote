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

class VaultWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun vaultDao(): com.palmnote.feature.vault.VaultDao
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val totalCount = entryPoint.vaultDao().countEntriesFlow().first() ?: 0
        val hasEntries = totalCount > 0

        for (appWidgetId in appWidgetIds) {
            // 1×1 缩档：只显示锁 + 条数（拖大到 ≥110dp 恢复完整布局）
            val minWidth = appWidgetManager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val isSmall = minWidth in 1..109
            val views = if (isSmall) {
                bindSmallViews(context, appWidgetId, totalCount)
            } else {
                bindViews(context, appWidgetId, totalCount, hasEntries)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    internal fun bindViews(context: Context, appWidgetId: Int, totalCount: Int, hasEntries: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vault)

        views.setTextViewText(R.id.widget_vault_count, "$totalCount")

        // 隐私：桌面组件不展示任何条目标题/分类，仅显示已加密状态提示
        if (hasEntries) {
            views.setTextViewText(R.id.widget_entry_title, "••••••")
            views.setTextViewText(R.id.widget_entry_category, context.getString(R.string.widget_vault_hint))
            views.setViewVisibility(R.id.widget_first_entry, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_state, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_first_entry, View.GONE)
            views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
        }

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_VAULT + appWidgetId, WidgetDeepLink.TAB_VAULT)
        )
        return views
    }

    internal fun bindSmallViews(context: Context, appWidgetId: Int, totalCount: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vault_small)
        views.setTextViewText(R.id.widget_vault_small_count, "$totalCount")
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_VAULT + appWidgetId, WidgetDeepLink.TAB_VAULT)
        )
        return views
    }
}
