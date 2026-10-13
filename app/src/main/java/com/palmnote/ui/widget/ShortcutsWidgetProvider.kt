package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.palmnote.app.R

/**
 * 快捷入口（v4）：四个直达入口（记账 / 物品 / 目标 / 纪念日），
 * 每块一个色族的淡彩方块 + 同族饱和字形。
 * 默认 2×2 田字格；拖成横条（宽 ≥250dp 且高 <110dp）铺成 4×1 一字排开 ——
 * 两个布局复用同一套控件 id，绑定逻辑不分叉。
 */
class ShortcutsWidgetProvider : ScopedWidgetProvider() {

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, height ->
                bindViews(context, appWidgetId, wide = width >= WIDE_WIDTH_DP && height < TALL_HEIGHT_DP)
            }
        }
    }

    internal fun bindViews(context: Context, appWidgetId: Int, wide: Boolean = false): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            if (wide) R.layout.widget_shortcuts_wide else R.layout.widget_shortcuts_unified
        )
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

        /** 组件声明的默认尺寸（2×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 110
        const val DEFAULT_HEIGHT_DP = 110

        /** 宽到 4 格（≈250dp）且矮过 2 格才切横条，2×2 拉宽不算。 */
        const val WIDE_WIDTH_DP = 250
        const val TALL_HEIGHT_DP = 110
    }
}
