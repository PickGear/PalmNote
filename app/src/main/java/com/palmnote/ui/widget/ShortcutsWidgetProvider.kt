package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.palmnote.app.R

/**
 * 快捷入口 2×2（定稿）：四个直达入口（记账 / 物品 / 目标 / 纪念日），
 * 每块一个色族的淡彩方块 + 同族饱和字形。
 */
class ShortcutsWidgetProvider : ScopedWidgetProvider() {

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId)
        }
    }

    internal fun bindViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_shortcuts_unified)
        // 一个入口一族色：淡彩方块底 + 同族饱和字形（装饰色不占全局强调色）
        SHORTCUT_CELLS.forEachIndexed { index, (bgId, iconId) ->
            val family = WidgetData.colorFamily(context, index)
            views.setInt(bgId, "setColorFilter", family.tint)
            views.setInt(iconId, "setColorFilter", family.hue)
        }

        views.setOnClickPendingIntent(
            R.id.widget_sc_add,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_ADD + appWidgetId, WidgetDeepLink.TAB_ADD_BILL)
        )
        views.setOnClickPendingIntent(
            R.id.widget_sc_asset,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_ASSET + appWidgetId, WidgetDeepLink.TAB_ASSET)
        )
        views.setOnClickPendingIntent(
            R.id.widget_sc_goal,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_GOAL + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_sc_anniversary,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_ANNIVERSARY + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_ROOT + appWidgetId, WidgetDeepLink.TAB_DASHBOARD)
        )
        return views
    }

    private companion object {
        /** 四个入口的（方块底, 字形）资源 id，顺序即色族顺序。 */
        val SHORTCUT_CELLS = listOf(
            R.id.widget_sc_add_bg to R.id.widget_sc_add_icon,
            R.id.widget_sc_asset_bg to R.id.widget_sc_asset_icon,
            R.id.widget_sc_goal_bg to R.id.widget_sc_goal_icon,
            R.id.widget_sc_anniversary_bg to R.id.widget_sc_anniversary_icon
        )
    }
}
