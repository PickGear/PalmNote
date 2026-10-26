package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.palmnote.app.R

/**
 * 快捷入口组件（v22）：八格真图标方块（记账 / 待办 / 打卡 / 物品 / 净资产 / 订阅 / 计数 / 日历）。
 *
 * 与 v4 的「四格（记账/物品/目标/纪念日）」是两代设计：v22 按稿扩到八格，
 * 每格 = 族色淡彩方块 + 同族色字形 + 标签，整格一个深链热区。
 * 四档构图按 [WidgetSizeClass] 切档：
 * - WIDE 4×2：八格（4 列 × 2 行，带头部）；
 * - TALL 4×4（v37 §2-9）：九宫格（3 列 × 3 行，八格 + 「接下来」，带头部）；
 * - SQUARE 2×2：四格（2 列 × 2 行，带头部）；
 * - BAR 4×1：四格一字排开（无头部）。
 * 窄过 2 格（<110dp，1×1）保留 v4 的纯「记一笔」入口（[bindSmallViews]）。
 *
 * 色系按稿内取色（八格依次 c5/c1/c2/c4/c6/c3/c7/c8）：同屏不撞色，
 * 与各组件族的标识色一致（记账 c5、待办 c1、打卡 c2、物品 c4、净资产 c6、订阅 c3、计数 c7）。
 */
// open：窄宽走 1×1 纯入口缩档（bindSmallViews），其余由 WidgetSizeClass 归档
open class ShortcutsWidgetProvider : ScopedWidgetProvider() {

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { width, _ ->
                if (width < SMALL_WIDTH_DP) {
                    bindSmallViews(context, appWidgetId)
                } else {
                    bindViews(context, appWidgetId, width, DEFAULT_HEIGHT_DP)
                }
            }
        }
    }

    /** 1×1 缩档：纯入口「记一笔」（稿子 s11：淡彩圆底 + 加号 + 名称，不放计数）。 */
    internal fun bindSmallViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = widgetViews(context, R.layout.widget_shortcuts_small)
        val family = WidgetV22.family(context, 0)
        views.setInt(R.id.widget_sc_small_bg, "setColorFilter", family.tint)
        views.setInt(R.id.widget_sc_small_icon, "setColorFilter", family.d)
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_ADD + appWidgetId, WidgetDeepLink.TAB_ADD_BILL)
        )
        return views
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        widthDp: Int = DEFAULT_WIDTH_DP,
        heightDp: Int = DEFAULT_HEIGHT_DP
    ): RemoteViews {
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_sc_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_sc_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_sc_v22_square
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> R.layout.widget_sc_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        views.setInt(R.id.widget_sc_badge, "setColorFilter", WidgetV22.family(context, 0).d)
        views.setTextViewText(R.id.widget_sc_title, context.getString(R.string.widget_shortcuts_title))
        val cells = cellsOf(size)
        val columns = columnsOf(size, cells)
        views.removeAllViews(R.id.widget_sc_grid_host)
        cells.chunked(columns).forEachIndexed { index, row ->
            val isLast = (index + 1) * columns >= cells.size
            val rowViews = widgetViews(
                context,
                if (isLast) R.layout.widget_sc_v22_row_end else R.layout.widget_sc_v22_row
            )
            row.forEach { entry ->
                val tile = widgetViews(context, R.layout.widget_sc_v22_tile)
                val family = WidgetV22.family(context, entry.familyIdx)
                tile.setInt(R.id.widget_sc_tile_bg, "setColorFilter", family.tint)
                tile.setImageViewResource(R.id.widget_sc_tile_icon, entry.iconRes)
                tile.setInt(R.id.widget_sc_tile_icon, "setColorFilter", family.d)
                tile.setTextViewText(R.id.widget_sc_tile_label, context.getString(entry.labelRes))
                tile.setOnClickPendingIntent(
                    R.id.widget_sc_tile,
                    entryIntent(context, appWidgetId, entry)
                )
                rowViews.addView(R.id.widget_sc_row, tile)
            }
            views.addView(R.id.widget_sc_grid_host, rowViews)
        }
        // 点空白处（非格子）进仪表盘
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_SHORTCUT_ROOT + appWidgetId, WidgetDeepLink.TAB_DASHBOARD)
        )
        return views
    }

    /** 格子深链：待办走「今日安排」清单模式，其余直接落 tab。 */
    private fun entryIntent(context: Context, appWidgetId: Int, entry: Entry) = if (entry.listMode != null) {
        WidgetHelper.createLifeListPendingIntent(
            context,
            WidgetDeepLink.SEG_SHORTCUT_ROOT + entry.segment + appWidgetId,
            entry.listMode
        )
    } else {
        WidgetHelper.createPendingIntent(
            context,
            WidgetDeepLink.SEG_SHORTCUT_ROOT + entry.segment + appWidgetId,
            entry.tab
        )
    }

    /** 各档的格子集合：4×4 九格 / 4×2 八格 / 2×2·4×1 四格 / 2×1 三格。 */
    private fun cellsOf(size: WidgetSizeClass): List<Entry> = when (size) {
        WidgetSizeClass.TALL -> ENTRIES_TALL
        WidgetSizeClass.WIDE -> ENTRIES
        WidgetSizeClass.SQUARE, WidgetSizeClass.BAR -> ENTRIES.take(4)
        WidgetSizeClass.CHIP -> ENTRIES.take(3)
    }

    /** 各档的列数：4×4 三列 / 4×2 四列 / 2×2 两列 / 单行档一字排开。 */
    private fun columnsOf(size: WidgetSizeClass, cells: List<Entry>): Int = when (size) {
        WidgetSizeClass.TALL -> 3
        WidgetSizeClass.WIDE -> 4
        WidgetSizeClass.SQUARE -> 2
        else -> cells.size
    }

    /** 一个入口格：字形 + 标签 + 族色序号 + 深链目标。 */
    private data class Entry(
        val iconRes: Int,
        val labelRes: Int,
        val familyIdx: Int,
        val segment: Int,
        val tab: String,
        val listMode: String? = null
    )

    private companion object {
        /** 组件声明的默认尺寸（2×2），桌面没给尺寸时兜底。 */
        const val DEFAULT_WIDTH_DP = 110
        const val DEFAULT_HEIGHT_DP = 110

        /** 窄过 2 格（2×2 放不下）即缩成 1×1 纯入口，只留「记一笔」主按钮。 */
        const val SMALL_WIDTH_DP = 110

        /**
         * 八格（稿内顺序与取色）：记账 c5 / 待办 c1 / 打卡 c2 / 物品 c4 /
         * 净资产 c6 / 订阅 c3 / 计数 c7 / 日历 c8。
         * segment 是深链请求码的错开量（同一 PendingIntent 家族里互不覆盖）。
         */
        val ENTRIES = listOf(
            Entry(R.drawable.widget_ic_wallet_white, R.string.widget_shortcut_bill, 4, 0, WidgetDeepLink.TAB_ADD_BILL),
            Entry(R.drawable.widget_ic_check_white, R.string.widget_shortcut_todo, 0, 1, WidgetDeepLink.TAB_LIFE, listMode = "AGENDA"),
            Entry(R.drawable.widget_ic_calendar_white, R.string.widget_shortcut_habit, 1, 2, WidgetDeepLink.TAB_LIFE),
            Entry(R.drawable.widget_ic_box_white, R.string.widget_shortcut_asset, 3, 3, WidgetDeepLink.TAB_ASSET),
            Entry(R.drawable.widget_ic_yuan_white, R.string.widget_shortcut_net, 5, 4, WidgetDeepLink.TAB_BILL),
            Entry(R.drawable.widget_ic_refresh_white, R.string.widget_shortcut_sub, 2, 5, WidgetDeepLink.TAB_LIFE),
            Entry(R.drawable.widget_ic_hash_white, R.string.widget_shortcut_counter, 6, 6, WidgetDeepLink.TAB_LIFE),
            Entry(R.drawable.widget_ic_cal_white, R.string.widget_shortcut_calendar, 7, 7, WidgetDeepLink.TAB_BILL)
        )

        /**
         * 4×4 九宫格（v37 §2-9）：八格 + 第 9 格「接下来」（时钟字形 c5，落生活页）。
         * 稿内顺序即此序；深链 segment 8（前八格已占 0..7）。
         */
        val ENTRIES_TALL = ENTRIES + Entry(
            R.drawable.widget_ic_clock_white, R.string.widget_shortcut_next, 4, 8, WidgetDeepLink.TAB_LIFE
        )
    }
}
