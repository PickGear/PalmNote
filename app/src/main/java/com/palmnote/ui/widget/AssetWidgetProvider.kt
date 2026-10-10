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

        // 占比条：段数 = 分类数，多出来的段隐藏（GONE 不占权重，剩下的段自动等分整条）
        SEGMENT_IDS.forEachIndexed { index, id ->
            if (index < shown.size) {
                views.setViewVisibility(id, View.VISIBLE)
                views.setInt(id, "setColorFilter", WidgetData.colorFamily(context, index).hue)
            } else {
                views.setViewVisibility(id, View.GONE)
            }
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

    /** 一个分类色片：淡彩胶囊底 + 同族色点 + 分类名 + 件数。 */
    private fun chipViews(context: Context, category: HeldCategoryCount, index: Int): RemoteViews {
        val chip = RemoteViews(context.packageName, R.layout.widget_asset_chip)
        val family = WidgetData.colorFamily(context, index)
        chip.setInt(R.id.widget_asset_chip_bg, "setColorFilter", family.tint)
        chip.setInt(R.id.widget_asset_chip_dot, "setColorFilter", family.hue)
        chip.setTextViewText(R.id.widget_asset_chip_name, category.category)
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

        val SEGMENT_IDS = intArrayOf(
            R.id.widget_asset_seg_1, R.id.widget_asset_seg_2, R.id.widget_asset_seg_3,
            R.id.widget_asset_seg_4, R.id.widget_asset_seg_5, R.id.widget_asset_seg_6
        )
    }
}
