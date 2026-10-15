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
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                DEFAULT_WIDTH_DP,
                DEFAULT_HEIGHT_DP
            ) { _, height ->
                bindViews(
                    context, appWidgetId, categories,
                    // 高度不足 2 格（160dp）时收起次要元素：占比条藏掉、色片只留一行
                    compactHeight = height < COMPACT_HEIGHT_DP
                )
            }
        }
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        categories: List<HeldCategoryCount>,
        compactHeight: Boolean = false
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_asset_unified)
        val maxCategories = if (compactHeight) 2 else MAX_CATEGORIES
        val shown = collapseToSlots(categories, maxCategories)
        val total = categories.sumOf { it.count }

        views.setInt(R.id.widget_asset_badge, "setColorFilter", WidgetData.colorFamily(context, BADGE_FAMILY).hue)
        // 头部 Hero Number：总数大字，单位「件」在布局里写死，不用 plurals
        views.setTextViewText(R.id.widget_asset_total, total.toString())

        // 占比条：24 段等宽槽位，按各分类件数占比分配段数 —— 段的宽度于是就是比例
        // （RemoteViews 改不了子控件宽度，只能靠「占几段」表达比例）
        bindShareBar(context, views, shown, compactHeight)

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
        views.setViewVisibility(R.id.widget_asset_bar, if (empty || compactHeight) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_asset_grid, if (empty) View.GONE else View.VISIBLE)

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_ASSET + appWidgetId, WidgetDeepLink.TAB_ASSET)
        )
        return views
    }

    /**
     * 顶部占比条：组的首/末段换圆头 drawable（单段组用四角圆的整段），非首组首段左内缩 3dp
     * 形成组间的缝 —— 容器无底色，缝里露出卡底，对应设计稿 `.seg` 的 `gap:3px`。
     * 矮卡（compactHeight）整条藏起。
     */
    private fun bindShareBar(
        context: Context,
        views: RemoteViews,
        shown: List<HeldCategoryCount>,
        compactHeight: Boolean
    ) {
        if (compactHeight) {
            views.setViewVisibility(R.id.widget_asset_bar, View.GONE)
            return
        }
        val owners = slotOwners(shown.map { it.count }, BAR_SLOTS)
        val gapPx = Math.round(3 * context.resources.displayMetrics.density)
        SEGMENT_IDS.forEachIndexed { slot, id ->
            views.setViewVisibility(id, View.VISIBLE)
            val isGroupStart = slot == 0 || owners[slot] != owners[slot - 1]
            val isGroupEnd = slot == BAR_SLOTS - 1 || owners[slot] != owners[slot + 1]
            views.setImageViewResource(
                id,
                when {
                    isGroupStart && isGroupEnd -> R.drawable.widget_seg_slot
                    isGroupStart -> R.drawable.widget_seg_slot_start
                    isGroupEnd -> R.drawable.widget_seg_slot_end
                    else -> R.drawable.widget_seg_slot_flat
                }
            )
            views.setViewPadding(id, if (isGroupStart && slot > 0) gapPx else 0, 0, 0, 0)
            views.setInt(id, "setColorFilter", WidgetData.colorFamily(context, owners[slot]).hue)
        }
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

    /**
     * 分类数超过组件格位（3×2 = 6）时**不丢弃**：位次靠后的并成一项「其他」。
     *
     * 直接 `take(6)` 会有两处错：① 第 7 名往后的分类在桌面上彻底消失；
     * ② 占比条按「留下的 6 项」归一化，等于把第 6 名拉伸到填满剩余槽位，比例失真、
     * 与头部「共 N 件」也对不上。合并后列出的件数之和仍等于全部件数，两条同时成立。
     *
     * 结果恰好 `slots` 项；若原「其他」本来就在前 5，按件数一并合进来，不会出现两个「其他」。
     */
    internal fun collapseToSlots(categories: List<HeldCategoryCount>, slots: Int): List<HeldCategoryCount> {
        if (categories.size <= slots) return categories
        val kept = categories.filter { it.category != OTHER_CATEGORY }.take(slots - 1)
        val keptKeys = kept.map { it.category }
        val mergedCount = categories.filter { it.category !in keptKeys }.sumOf { it.count }
        return kept + HeldCategoryCount(OTHER_CATEGORY, mergedCount)
    }

    /**
     * 分类名的组件版解析（对齐 `AssetScreen.getCategoryDisplayName`）：
     * 预设分类库里存的是代码（DIGITAL…），走 `getCategoryName` 本地化；**自定义分类直接以名字入库**，
     * 必须原样显示 —— `getCategoryName` 认不出就落 else 分支，会让所有自定义分类在桌面上都变成「其他」。
     */
    internal fun assetCategoryLabel(key: String, context: Context): String =
        if (com.palmnote.ui.asset.assetCategoryItems.any { it.name == key }) {
            com.palmnote.ui.components.getCategoryName(key, context)
        } else {
            key
        }

    /** 一个分类色片：淡彩胶囊底 + 同族色点 + 分类名 + 纯数字件数（着同族色）。 */
    private fun chipViews(context: Context, category: HeldCategoryCount, index: Int): RemoteViews {
        val chip = RemoteViews(context.packageName, R.layout.widget_asset_chip)
        val family = WidgetData.colorFamily(context, index)
        chip.setInt(R.id.widget_asset_chip_bg, "setColorFilter", family.tint)
        chip.setInt(R.id.widget_asset_chip_dot, "setColorFilter", family.hue)
        // DB 里存的是 DIGITAL/BOOKS 这类代码值，应用里会用 getCategoryName 翻成中文，
        // 组件必须走同一套翻译，否则桌面上显示的是英文代码
        chip.setTextViewText(
            R.id.widget_asset_chip_name,
            assetCategoryLabel(category.category, context)
        )
        chip.setTextViewText(R.id.widget_asset_chip_count, "${category.count}")
        chip.setTextColor(R.id.widget_asset_chip_count, family.hue)
        return chip
    }

    private companion object {
        /** 3×2 放得下 6 个分类（3 行色片），占比条也最多 6 段。超出部分并成「其他」，见 [collapseToSlots]。 */
        const val MAX_CATEGORIES = 6

        /** 默认摆上去的尺寸（res/xml info 里的 minWidth/minHeight 口径）。 */
        const val DEFAULT_WIDTH_DP = 250
        const val DEFAULT_HEIGHT_DP = 180

        /** 高度不足 2 格（160dp）时收起占比条、色片只留一行（与计数器组件同一档位）。 */
        const val COMPACT_HEIGHT_DP = 160

        /** 聚合项用的分类键：预设里就有 OTHER（名字/图标/颜色都齐），直接借用。 */
        const val OTHER_CATEGORY = "OTHER"

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
