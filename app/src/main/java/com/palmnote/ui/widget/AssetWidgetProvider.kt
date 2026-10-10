package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.db.dao.HeldCategoryCount
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * 物品 3×2（定稿）：头部给「共 N 件」，顶部占比条按分类上色族色，下面两列色片网格写清每类几件。
 * 只数在用（HELD）—— 与头部总数同源，色片件数加起来就是它。点卡片进物品页。
 */
class AssetWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun assetDao(): com.palmnote.data.db.dao.AssetDao
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val categories = entryPoint.assetDao().getHeldCategoryCounts().first()

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId, categories)
        }
    }

    internal fun bindViews(context: Context, appWidgetId: Int, categories: List<HeldCategoryCount>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_asset_unified)
        val shown = categories.take(MAX_CATEGORIES)
        val total = categories.sumOf { it.count }

        views.setInt(R.id.widget_asset_badge, "setColorFilter", WidgetData.colorFamily(context, BADGE_FAMILY).hue)
        views.setTextViewText(
            R.id.widget_asset_total,
            context.resources.getQuantityString(R.plurals.widget_asset_total_count, total, total)
        )

        // 占比条：24 段等宽槽位，按各分类件数占比分配段数 —— 段的宽度于是就是比例
        // （RemoteViews 改不了子控件宽度，只能靠「占几段」表达比例）
        val owners = slotOwners(shown.map { it.count }, BAR_SLOTS)
        SEGMENT_IDS.forEachIndexed { slot, id ->
            views.setViewVisibility(id, View.VISIBLE)
            views.setInt(id, "setColorFilter", WidgetData.colorFamily(context, owners[slot]).hue)
        }

        // 两列色片网格：每行两个，条目数为奇数时补一个等宽占位
        views.removeAllViews(R.id.widget_asset_grid)
        shown.chunked(2).forEachIndexed { pairIndex, pair ->
            val pairRow = RemoteViews(context.packageName, R.layout.widget_asset_pair)
            pair.forEachIndexed { column, category ->
                pairRow.addView(R.id.widget_asset_pair_row, chipViews(context, category, pairIndex * 2 + column))
            }
            if (pair.size == 1) {
                pairRow.addView(
                    R.id.widget_asset_pair_row,
                    RemoteViews(context.packageName, R.layout.widget_asset_spacer)
                )
            }
            views.addView(R.id.widget_asset_grid, pairRow)
        }

        val empty = categories.isEmpty()
        views.setViewVisibility(R.id.widget_asset_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_asset_bar, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_asset_grid, if (empty) View.GONE else View.VISIBLE)

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_ASSET + appWidgetId, WidgetDeepLink.TAB_ASSET)
        )
        return views
    }

    /**
     * 24 段等宽槽位各归哪个分类：按件数占比切分，累计边界四舍五入。
     * 抽成纯函数是为了单测「段宽 = 比例」这件事（渲染层比较 ColorFilter 不可靠）。
     */
    internal fun slotOwners(counts: List<Int>, slots: Int): IntArray {
        val owners = IntArray(slots)
        if (counts.isEmpty()) return owners
        val total = counts.sum().coerceAtLeast(1)
        var cumulative = 0
        var cursor = 0
        counts.forEachIndexed { index, count ->
            cumulative += count
            val edge = if (index == counts.lastIndex) {
                slots
            } else {
                Math.round(cumulative * slots.toFloat() / total).toInt()
            }
            for (slot in cursor until edge.coerceIn(cursor, slots)) owners[slot] = index
            cursor = edge.coerceIn(cursor, slots)
        }
        return owners
    }

    /** 一个分类色片：淡彩胶囊底 + 同族色点 + 分类名 + 件数。 */
    private fun chipViews(context: Context, category: HeldCategoryCount, index: Int): RemoteViews {
        val chip = RemoteViews(context.packageName, R.layout.widget_asset_chip)
        val family = WidgetData.colorFamily(context, index)
        chip.setInt(R.id.widget_asset_chip_bg, "setColorFilter", family.tint)
        chip.setInt(R.id.widget_asset_chip_dot, "setColorFilter", family.hue)
        // DB 里存的是 DIGITAL/BOOKS 这类代码值，应用里会用 getCategoryName 翻成中文，
        // 组件必须走同一套翻译，否则桌面上显示的是英文代码
        chip.setTextViewText(
            R.id.widget_asset_chip_name,
            com.palmnote.ui.components.getCategoryName(category.category, context)
        )
        chip.setTextViewText(
            R.id.widget_asset_chip_count,
            context.resources.getQuantityString(R.plurals.widget_asset_item_count, category.count, category.count)
        )
        return chip
    }

    private companion object {
        /** 3×2 放得下 6 个分类（3 行色片），占比条也最多 6 段。 */
        const val MAX_CATEGORIES = 6

        /** 头部徽章取的色族（稿子里物品是蓝色）—— 装饰色不占全局强调色。 */
        const val BADGE_FAMILY = 1

        /** 占比条的槽位数：段越细，比例越准（24 段 ≈ 4% 一档）。 */
        const val BAR_SLOTS = 24

        val SEGMENT_IDS = intArrayOf(
            R.id.widget_asset_seg_1, R.id.widget_asset_seg_2, R.id.widget_asset_seg_3,
            R.id.widget_asset_seg_4, R.id.widget_asset_seg_5, R.id.widget_asset_seg_6,
            R.id.widget_asset_seg_7, R.id.widget_asset_seg_8, R.id.widget_asset_seg_9,
            R.id.widget_asset_seg_10, R.id.widget_asset_seg_11, R.id.widget_asset_seg_12,
            R.id.widget_asset_seg_13, R.id.widget_asset_seg_14, R.id.widget_asset_seg_15,
            R.id.widget_asset_seg_16, R.id.widget_asset_seg_17, R.id.widget_asset_seg_18,
            R.id.widget_asset_seg_19, R.id.widget_asset_seg_20, R.id.widget_asset_seg_21,
            R.id.widget_asset_seg_22, R.id.widget_asset_seg_23, R.id.widget_asset_seg_24
        )
    }
}
