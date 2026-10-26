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
 * 物品分布组件（v22/v37）：头部「共 N 件」+ 占比分段条 + 图例胶囊网格。
 *
 * 数据口径：只数在用（HELD）—— 与头部总数同源，图例件数加起来就是它，分段条按它归一。
 * 四档构图按 [WidgetSizeClass] 切档（目录五档之外由桌面拖拽归档，同全族）：
 * - WIDE 4×2：分段条 + 3 列图例两行（6 格）；
 * - SQUARE 2×2：分段条 + 2 列图例三行（6 格），分类 ≤4 时末行改「最多」行；
 * - BAR 4×1：三列读数（在库 / 分类 / 最多）+ 分段条；
 * - TALL 4×4（v37 §2-4）：hero「N 件 · 最多 X n」+ 14dp 分段条（前五 + 其余灰）+
 *   3×4 共 12 格分类大格 + footer 最近添加；压缩态（高 250~299dp）砍 meta 与第 4 行。
 *
 * 分类超过 6 格时**不丢弃**：前 5 大各占一格，第 6 格是聚合槽「+N 类」——
 * ① 用第 6 色（c6 粉）与相邻橙区分，文字「+N 类」本身即表明「不是分类」；
 * ② **不复用真实「其他」分类**：预设里 OTHER 是独立分类（有自己的名字/图标/颜色），
 *    借它当聚合槽会让用户以为「其他」类真有这么多件。聚合槽由 [AssetCell.isMore] 标记，
 *    真「其他」若排进前 5 就按自己的名字显示。4×4 的 12 格同规：前 11 大 + 聚合槽，
 *    分段条里的聚合段按稿用石墨灰（c8）。
 */
// open：AssetSquare/Bar/Mini 尺寸形态薄子类继承（v22 四档）
open class AssetWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun assetDao(): com.palmnote.data.db.dao.AssetDao
    }

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext.applicationContext, WidgetEntryPoint::class.java
        )
        val categories = entryPoint.assetDao().getHeldCategoryCounts().first()
        val recent = entryPoint.assetDao().getMostRecentHeldAsset()?.let { AssetRecent.from(it) }

        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                MIN_WIDTH_DP,
                MIN_HEIGHT_DP
            ) { width, height ->
                bindViews(context, appWidgetId, categories, width, height, recent)
            }
        }
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        categories: List<HeldCategoryCount>,
        widthDp: Int = MIN_WIDTH_DP,
        heightDp: Int = MIN_HEIGHT_DP,
        recent: AssetRecent? = null
    ): RemoteViews {
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_asset_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_asset_v22_square
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> R.layout.widget_asset_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_ASSET + appWidgetId,
                WidgetDeepLink.TAB_ASSET
            )
        )
        val total = categories.sumOf { it.count }
        when (size) {
            WidgetSizeClass.TALL ->
                bindTall4x4(context, views, categories, total, recent, compact = heightDp < TALL_FULL_DP)
            WidgetSizeClass.WIDE ->
                bindWide(context, views, categories, total)
            WidgetSizeClass.SQUARE -> bindSquare(context, views, categories, total)
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> bindBar(context, views, categories, total)
        }
        return views
    }

    /**
     * 4×4（v37 §2-4）：hero + 14dp 分段条（前五 + 其余灰）+ 3×4 十二格 + footer 最近添加。
     * 0 件走空白态（稿 §11）：不摆空分段条与空网格。
     */
    private fun bindTall4x4(
        context: Context,
        views: RemoteViews,
        categories: List<HeldCategoryCount>,
        total: Int,
        recent: AssetRecent?,
        compact: Boolean
    ) {
        val top = categories.firstOrNull()
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_box_white,
            title = context.getString(R.string.widget_asset_title),
            meta = if (compact) null else
                context.getString(R.string.widget_asset_meta_fmt, total, categories.size),
            familyIdx = 0
        )
        WidgetV22.bindTallHero(
            context, views,
            totalText(context, total),
            top?.let {
                context.getString(
                    R.string.widget_asset_most_fmt,
                    assetCategoryLabel(it.category, context),
                    it.count
                )
            }
        )
        if (categories.isEmpty()) {
            WidgetV22.bindEmpty(
                context, views,
                iconRes = R.drawable.widget_ic_box_white,
                title = context.getString(R.string.widget_asset_empty),
                hint = context.getString(R.string.widget_asset_empty_hint),
                familyIdx = 0
            )
            WidgetV22.tallSections(context, views, "empty")
            return
        }
        bindTallGrid(context, views, categories, compact)
        // footer：最近添加（无源时整条收起，不摆空行）
        if (recent != null) {
            WidgetV22.bindFooter(
                context, views,
                left = context.getString(R.string.widget_asset_recent_added),
                value = recent.name,
                right = recent.dateText
            )
        }
        WidgetV22.tallSections(
            context, views,
            "grid",
            if (recent != null) "footer" else ""
        )
    }

    /** 4×4 网格区：14dp 分段条（恒「前五 + 聚合灰」，稿 §2-4）+ 3×4 十二格（压缩态 3 行）。 */
    private fun bindTallGrid(
        context: Context,
        views: RemoteViews,
        categories: List<HeldCategoryCount>,
        compact: Boolean
    ) {
        // 12 格：前 11 大各占一格，第 12 格聚合槽；压缩态只排 3 行（9 格）
        val cells = buildCells(categories, CELLS_TALL)
        val rows = cells.chunked(COLUMNS_TALL).let { if (compact) it.take(3) else it }
        val segCells = buildCells(categories, SEG_CELLS)
        val seg = widgetViews(context, R.layout.widget_v22_seg_tall)
        WidgetV22.bindSeg(
            context, seg,
            counts = segCells.map { it.count },
            colors = segCells.map { if (it.isMore) familyD(context, GRAY_FAMILY) else familyD(context, it.familyIdx) }
        )
        views.removeAllViews(R.id.widget_tall_grid)
        views.addView(R.id.widget_tall_grid, seg)
        rows.forEachIndexed { index, row ->
            val rowViews = widgetViews(
                context,
                if (index == rows.lastIndex) R.layout.widget_asset_v22_gridrow_end
                else R.layout.widget_asset_v22_gridrow
            )
            row.forEach { cell ->
                val chip = widgetViews(context, R.layout.widget_v22_chip_cell)
                WidgetV22.bindChip(
                    context, chip,
                    name = cellName(context, cell, longLabel = true),
                    sub = null,
                    // 稿 §2-4 的聚合格是「+1类 / 2」：4×4 给件数（12 格里唯一一处），
                    // 小档沿用「只给 +N 类、不给件数」的既有定案
                    value = cell.count.toString(),
                    familyIdx = if (cell.isMore) GRAY_FAMILY else cell.familyIdx,
                    showDot = true
                )
                rowViews.addView(R.id.widget_asset_grid_row, chip)
            }
            repeat(COLUMNS_TALL - row.size) {
                rowViews.addView(R.id.widget_asset_grid_row, widgetViews(context, R.layout.widget_v22_spacer))
            }
            views.addView(R.id.widget_tall_grid, rowViews)
        }
    }

    /** 4×2：hero + 分段条 + 3 列图例两行。 */
    private fun bindWide(
        context: Context,
        views: RemoteViews,
        categories: List<HeldCategoryCount>,
        total: Int
    ) {
        // 稿 §2-4 的 4×2 头部是短名「物品」（2026-10-25 用户定：对稿）
        bindHeader(context, views, R.string.widget_asset_title_short, total)
        val empty = categories.isEmpty()
        views.setViewVisibility(R.id.widget_asset_seg_host, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_asset_grid_host, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_asset_empty, if (empty) View.VISIBLE else View.GONE)
        if (empty) return

        // 稿 §2-4 的 4×2 是「分段条 + **单行四类**」：四枚等宽胶囊排一行
        val cells = buildCells(categories, CELLS_WIDE)
        addSeg(context, views, cells)
        views.removeAllViews(R.id.widget_asset_grid_host)
        val rowViews = widgetViews(context, R.layout.widget_asset_v22_row3_end)
        fillRow(context, rowViews, cells, COLUMNS_WIDE, longLabel = true)
        views.addView(R.id.widget_asset_grid_host, rowViews)
    }

    /** 2×2：hero + 分段条 + 2 列图例三行；分类 ≤4 时末行让位给「最多」行。 */
    private fun bindSquare(
        context: Context,
        views: RemoteViews,
        categories: List<HeldCategoryCount>,
        total: Int
    ) {
        bindHeader(context, views, R.string.widget_asset_title_short, total)
        val empty = categories.isEmpty()
        views.setViewVisibility(R.id.widget_asset_seg_host, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_asset_grid_host, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_asset_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_asset_most_row, View.GONE)
        if (empty) return

        // 稿 §2-4 的 2×2 是「分段条 + 四行**纯文本**」：色点 + 类名 + 件数，
        // 不带胶囊底色（背景框会把长类名截断），四行把剩余高度均分
        val cells = buildCells(categories, CELLS_SQUARE)
        addSeg(context, views, cells)
        views.removeAllViews(R.id.widget_asset_grid_host)
        cells.forEach { cell ->
            val rowViews = widgetViews(context, R.layout.widget_asset_v22_textrow)
            val f = WidgetV22.family(context, cell.familyIdx)
            rowViews.setInt(R.id.widget_asset_text_dot, "setColorFilter", f.d)
            rowViews.setTextColor(R.id.widget_asset_text_name, context.getColor(R.color.widget_v22_text_1))
            rowViews.setTextViewText(R.id.widget_asset_text_name, cellName(context, cell, longLabel = false))
            rowViews.setTextColor(R.id.widget_asset_text_value, f.ink)
            rowViews.setTextViewText(R.id.widget_asset_text_value, cell.count.toString())
            views.addView(R.id.widget_asset_grid_host, rowViews)
        }
    }

    /** 4×1：三列读数（在库 / 分类 / 最多）+ 分段条。 */
    private fun bindBar(
        context: Context,
        views: RemoteViews,
        categories: List<HeldCategoryCount>,
        total: Int
    ) {
        bindHeader(context, views, R.string.widget_asset_title_short, total)
        views.setViewVisibility(R.id.widget_asset_seg_host, if (categories.isEmpty()) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_asset_bar_total, totalText(context, total))
        views.setTextViewText(R.id.widget_asset_bar_cats, categories.size.toString())
        val top = categories.firstOrNull()
        if (top == null) {
            views.setViewVisibility(R.id.widget_asset_bar_most_row, View.GONE)
            views.setTextViewText(R.id.widget_asset_bar_most, context.getString(R.string.widget_asset_dash))
        } else {
            views.setViewVisibility(R.id.widget_asset_bar_most_row, View.VISIBLE)
            views.setInt(R.id.widget_asset_bar_most_dot, "setColorFilter", familyD(context, 0))
            views.setTextViewText(
                R.id.widget_asset_bar_most,
                "${assetCategoryLabel(top.category, context)} ${top.count}"
            )
        }
        if (categories.isNotEmpty()) {
            addSeg(context, views, buildCells(categories, CELLS_SEG))
        }
    }

    /** 2×1：徽章 + hero 件数 + 四个点（装饰，恒族色）。 */


    /** 头部：徽章上族色（c1 蓝）+ 标题 + hero 总数。 */
    private fun bindHeader(context: Context, views: RemoteViews, titleRes: Int, total: Int) {
        views.setInt(R.id.widget_asset_badge, "setColorFilter", familyD(context, 0))
        views.setTextViewText(R.id.widget_asset_title, context.getString(titleRes))
        views.setTextViewText(R.id.widget_asset_total, totalText(context, total))
    }

    /** hero 文本：总数 + 单位（zh「11 件」/ en「11 items」）。 */
    private fun totalText(context: Context, total: Int): String =
        "$total ${context.getString(R.string.widget_asset_unit)}"

    /** 分段条：一格一段，段宽 = 件数占比；聚合槽着 c6 粉。 */
    private fun addSeg(context: Context, views: RemoteViews, cells: List<AssetCell>) {
        val seg = widgetViews(context, R.layout.widget_v22_seg)
        WidgetV22.bindSeg(
            context, seg,
            counts = cells.map { it.count },
            colors = cells.map { familyD(context, it.familyIdx) }
        )
        views.removeAllViews(R.id.widget_asset_seg_host)
        views.addView(R.id.widget_asset_seg_host, seg)
    }

    /**
     * 往图例行里填胶囊：真实格 = 色点 + 分类名 + 件数；聚合格 = 色点 +「+N 类」（无件数）。
     * 不足的槽位用等权占位补足，保证已填的格不被拉宽。
     */
    private fun fillRow(
        context: Context,
        row: RemoteViews,
        cells: List<AssetCell>,
        columns: Int,
        longLabel: Boolean
    ) {
        cells.forEach { cell ->
            val chip = widgetViews(
                context,
                if (columns == COLUMNS_WIDE) R.layout.widget_v22_chip else R.layout.widget_v22_chip_tight
            )
            WidgetV22.bindChip(
                context, chip,
                name = cellName(context, cell, longLabel),
                sub = null,
                value = if (cell.isMore) null else cell.count.toString(),
                familyIdx = cell.familyIdx,
                showDot = true
            )
            row.addView(R.id.widget_asset_row, chip)
        }
        repeat(columns - cells.size) {
            row.addView(R.id.widget_asset_row, widgetViews(context, R.layout.widget_v22_spacer))
        }
    }

    /** 格子显示名：聚合槽取「+N 类 / +N」，真实分类走与物品页同源的本地化。 */
    private fun cellName(context: Context, cell: AssetCell, longLabel: Boolean): String =
        if (cell.isMore) {
            context.getString(
                if (longLabel) R.string.widget_asset_more else R.string.widget_asset_more_short,
                cell.count
            )
        } else {
            assetCategoryLabel(cell.key, context)
        }

    private fun familyD(context: Context, index: Int) = WidgetV22.family(context, index).d

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

    private companion object {
        /** 4×2：单行四类（稿 §2-4）；2×2：四行纯文本列表。 */
        const val CELLS_WIDE = 4
        const val CELLS_SQUARE = 4
        const val COLUMNS_WIDE = 4

        /** 4×1 的分段条槽位（只画条，不排图例）。 */
        const val CELLS_SEG = 6

        /** 4×4：3 列 × 4 行共 12 格（前 11 大 + 聚合槽，稿 §2-4）。 */
        const val CELLS_TALL = 12
        const val COLUMNS_TALL = 3

        /** 4×4 分段条格数：前五 + 聚合段（稿 §2-4 的六段条）。 */
        const val SEG_CELLS = 6

        /** 4×4 分段条里聚合段的族色序号：c8 石墨灰（稿 §2-4「其余」）。 */
        const val GRAY_FAMILY = 7

        /** 4×4 完整排版高度；低于它（250~299dp 的压缩态）按稿 §04 压缩优先级砍。 */
        const val TALL_FULL_DP = 300

        /** 组件声明的默认尺寸（3×2），桌面没给尺寸时兜底。 */
        const val MIN_WIDTH_DP = 250
        const val MIN_HEIGHT_DP = 180
    }
}

/**
 * 最近添加的一件在用物品（4×4 footer 用）。[dateText] 是添加日的「M/d」。
 */
internal data class AssetRecent(val name: String, val dateText: String) {
    companion object {
        fun from(asset: com.palmnote.data.db.entity.Asset): AssetRecent = AssetRecent(
            name = asset.name,
            dateText = java.time.Instant.ofEpochMilli(asset.createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("M/d"))
        )
    }
}

/**
 * 一个图例格：件数格（真实分类）或聚合格（[isMore] 为真，件数是落选分类的件数合计）。
 * [familyIdx] 是族色序号：前 5 大顺位取 c1..c5，聚合格取 [MORE_FAMILY]（c6 粉）。
 */
internal data class AssetCell(val key: String, val count: Int, val familyIdx: Int, val isMore: Boolean)

/**
 * 分类 → 6 格：不超就原样；超出则前 5 大各占一格，第 6 格是聚合槽。
 * 列出的件数之和恒等于全部件数 —— 与头部「共 N 件」对得上。
 */
internal fun buildCells(categories: List<HeldCategoryCount>, slots: Int): List<AssetCell> {
    // 槽位全满时也要留一格给聚合槽：稿 §2-4 的 12 类样例是「11 个具名 + +1类」，
    // 而不是把 12 个分类平铺（平铺后件数排序看着是乱的，且没有「还有别的类」的出口）
    if (categories.size < slots) {
        return categories.mapIndexed { index, item ->
            AssetCell(item.category, item.count, index, false)
        }
    }
    val kept = categories.take(slots - 1)
    val tail = categories.drop(slots - 1)
    return kept.mapIndexed { index, item ->
        AssetCell(item.category, item.count, index, false)
    } + AssetCell(key = "", count = tail.sumOf { it.count }, familyIdx = MORE_FAMILY, isMore = true)
}

/** 聚合槽的族色序号：第 6 色（c6 粉）—— 前 5 大已占 c1..c5，与相邻橙区分明显。 */
internal const val MORE_FAMILY = 5
