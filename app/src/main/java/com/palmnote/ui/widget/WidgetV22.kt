package com.palmnote.ui.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R

/**
 * v22 构件绑定工具（稿 docs/mockups/widgets/v22）：色板取色 + 五种可视化原语的下发。
 *
 * 原语只覆盖「可数图形」与胶囊，刻意不含假比例条：
 * - [bindChip] 胶囊行（淡底 dt + 色点 d + 族色数值 ink）
 * - [bindTicks] 分段刻度（**分母固定**才画：今日 5 / 早起 7）
 * - [bindDpd] / [bindDotg] 点阵（打卡 2/5、计数 6/8）
 * - [bindDstrip] 日历条（一格 = 剩一天，末格 = 事件日；可数）
 *
 * RemoteViews 不能按值改宽度，所有比例/刻度一律固定槽位：
 * 用不到的槽 GONE，不缩字号不改宽度。
 *
 * context 一律传 [ScopedWidgetProvider.publish] 里色调包装后的那个（[WidgetToneContext]），
 * 这样自选深色背景/浅字模式下族色淡底与深墨会自动切夜间档。
 */
internal object WidgetV22 {

    /** 卡底按档换圆角（稿 §1：4×2=24 / 2×2=22 / 4×1=20 / 2×1=18；布局里预置的 22dp 是兜底）。 */
    fun bindCardBg(rv: RemoteViews, size: WidgetSizeClass) {
        val drawable = when (size) {
            WidgetSizeClass.WIDE, WidgetSizeClass.TALL -> R.drawable.widget_card_round_wide
            WidgetSizeClass.SQUARE -> R.drawable.widget_card_round
            WidgetSizeClass.BAR -> R.drawable.widget_card_round_bar
            WidgetSizeClass.CHIP -> R.drawable.widget_card_round_mini
        }
        rv.setInt(R.id.widget_layout, "setBackgroundResource", drawable)
    }

    /** 一族三档：d 主色（图形）、tint 淡底（胶囊底）、ink 深墨（数值）。 */
    data class Family(val d: Int, val tint: Int, val ink: Int)

    private val D = intArrayOf(
        R.color.widget_v22_c1, R.color.widget_v22_c2, R.color.widget_v22_c3, R.color.widget_v22_c4,
        R.color.widget_v22_c5, R.color.widget_v22_c6, R.color.widget_v22_c7, R.color.widget_v22_c8
    )

    private val TINT = intArrayOf(
        R.color.widget_v22_c1_tint, R.color.widget_v22_c2_tint, R.color.widget_v22_c3_tint,
        R.color.widget_v22_c4_tint, R.color.widget_v22_c5_tint, R.color.widget_v22_c6_tint,
        R.color.widget_v22_c7_tint, R.color.widget_v22_c8_tint
    )

    private val INK = intArrayOf(
        R.color.widget_v22_c1_ink, R.color.widget_v22_c2_ink, R.color.widget_v22_c3_ink,
        R.color.widget_v22_c4_ink, R.color.widget_v22_c5_ink, R.color.widget_v22_c6_ink,
        R.color.widget_v22_c7_ink, R.color.widget_v22_c8_ink
    )

    /** 8 族色索引（循环取）：c1 蓝 c2 绿 c3 琥珀 c4 紫 c5 橙 c6 粉 c7 青 c8 灰。 */
    fun family(context: Context, index: Int): Family {
        val i = ((index % 8) + 8) % 8
        return Family(context.getColor(D[i]), context.getColor(TINT[i]), context.getColor(INK[i]))
    }

    // ── v37 四档新增：圆标昼夜对 / KPI / 柱图 / footer / minichips / rows / empty ──

    private val BADGE_BG = intArrayOf(
        R.color.widget_v22_c1_badge_bg, R.color.widget_v22_c2_badge_bg, R.color.widget_v22_c3_badge_bg,
        R.color.widget_v22_c4_badge_bg, R.color.widget_v22_c5_badge_bg, R.color.widget_v22_c6_badge_bg,
        R.color.widget_v22_c7_badge_bg, R.color.widget_v22_c8_badge_bg
    )

    private val BADGE_FG = intArrayOf(
        R.color.widget_v22_c1_badge_fg, R.color.widget_v22_c2_badge_fg, R.color.widget_v22_c3_badge_fg,
        R.color.widget_v22_c4_badge_fg, R.color.widget_v22_c5_badge_fg, R.color.widget_v22_c6_badge_fg,
        R.color.widget_v22_c7_badge_fg, R.color.widget_v22_c8_badge_fg
    )

    /** 圆标昼夜对（v37 §3）：底 = 浅 d / 夜 dt；图形 = 浅白 / 夜 di。走资源切换，代码不判 UI Mode。 */
    fun bindBadgeColors(context: Context, rv: RemoteViews, bgId: Int, fgId: Int, index: Int) {
        val i = ((index % 8) + 8) % 8
        rv.setInt(bgId, "setColorFilter", context.getColor(BADGE_BG[i]))
        rv.setInt(fgId, "setColorFilter", context.getColor(BADGE_FG[i]))
    }

    /** 圆标内文字（¥ / # 等）的颜色，昼夜对同上。 */
    fun badgeTextColor(context: Context, index: Int): Int {
        val i = ((index % 8) + 8) % 8
        return context.getColor(BADGE_FG[i])
    }

    /** v37 .kpis 的一块：标签 / 数值 / 副文；null 段隐藏整块。 */
    data class Kpi(val label: String, val value: String, val sub: String?, val familyIdx: Int)

    fun bindKpis(context: Context, rv: RemoteViews, kpis: List<Kpi>) {
        for (i in 1..4) {
            val root = rvId(context, "widget_v22_kpi_$i")
            val kpi = kpis.getOrNull(i - 1)
            if (kpi == null) {
                rv.setViewVisibility(root, View.GONE)
                continue
            }
            rv.setViewVisibility(root, View.VISIBLE)
            val f = family(context, kpi.familyIdx)
            // 底色打在块内底图 ImageView 上（LinearLayout 没有 setColorFilter，见 widget_v22_kpis.xml）
            rv.setInt(rvId(context, "widget_v22_kpi_${i}_bg"), "setColorFilter", f.tint)
            rv.setTextColor(rvId(context, "widget_v22_kpi_${i}_l"), f.ink)
            rv.setTextViewText(rvId(context, "widget_v22_kpi_${i}_l"), kpi.label)
            rv.setTextColor(rvId(context, "widget_v22_kpi_${i}_v"), f.ink)
            rv.setTextViewText(rvId(context, "widget_v22_kpi_${i}_v"), kpi.value)
            rv.setTextColor(rvId(context, "widget_v22_kpi_${i}_s"), f.ink)
            rv.setTextViewText(rvId(context, "widget_v22_kpi_${i}_s"), kpi.sub ?: "")
        }
    }

    /** v37 .chart 的一根柱：高度百分比 + 可选峰值标。 */
    data class ChartBar(
        val percent: Int,
        val familyIdx: Int,
        val label: String?,
        val axis: String,
        /** 轴上的「今天 / 当前月」列：轴字用族色深墨加粗（稿 `.caxis .today`）。 */
        val today: Boolean = false
    )

    /**
     * 柱图：头行（左标题 / 右注）+ 柱区（ClipDrawable level = percent×100）+ 轴行。
     * 槽位固定 12：少柱 GONE 尾槽；轴/柱色 per-bar。
     *
     * 三处与稿对齐的细节：
     * - **峰值标只画一个**：多个并列峰值时只标最左那根（稿每图只一处 `.cv`），
     *   否则「3 3 3」这种重复标把柱区顶满；
     * - **今天列轴字高亮**：族色深墨 + 粗（`.caxis .today`）；
     * - **非零小值保底 2%**：稿 `min-height:3px` 保证有数值就看得到柱头，纯 0 仍不画。
     */
    fun bindChart(context: Context, rv: RemoteViews, title: String, metric: String?, bars: List<ChartBar>) {
        rv.setTextViewText(rvId(context, "widget_v22_chart_cl"), title)
        rv.setTextViewText(rvId(context, "widget_v22_chart_cm"), metric ?: "")
        var labelShown = false
        for (i in 1..12) {
            val col = rvId(context, "widget_v22_chart_col_$i")
            val bar = bars.getOrNull(i - 1)
            if (bar == null) {
                rv.setViewVisibility(col, View.GONE)
                rv.setViewVisibility(rvId(context, "widget_v22_chart_ax_$i"), View.GONE)
                continue
            }
            rv.setViewVisibility(col, View.VISIBLE)
            rv.setViewVisibility(rvId(context, "widget_v22_chart_ax_$i"), View.VISIBLE)
            val f = family(context, bar.familyIdx)
            rv.setInt(rvId(context, "widget_v22_chart_bar_$i"), "setColorFilter", f.d)
            val level = if (bar.percent <= 0) 0 else bar.percent.coerceIn(MIN_BAR_PERCENT, 100) * 100
            rv.setInt(rvId(context, "widget_v22_chart_bar_$i"), "setImageLevel", level)
            val cv = rvId(context, "widget_v22_chart_cv_$i")
            if (bar.label != null && !labelShown) {
                labelShown = true
                rv.setViewVisibility(cv, View.VISIBLE)
                rv.setTextColor(cv, context.getColor(R.color.widget_v22_text_1))
                rv.setTextViewText(cv, bar.label)
            } else {
                // **INVISIBLE 而不是 GONE**：标签带对每列都占同样的 14dp，
                // 柱身才有共同基准（GONE 会让有标签那列的柱区变矮、跨列比例失真）
                rv.setViewVisibility(cv, View.INVISIBLE)
            }
            val axis = rvId(context, "widget_v22_chart_ax_$i")
            rv.setTextViewText(axis, bar.axis)
            // 稿 .caxis .today 是「族色 + 加粗」；RemoteViews 没有可下发的 setTextStyle
            // （setTypeface 收 Typeface 对象，setPaintFlags 会连抗锯齿一起覆盖），
            // 这里只上族色深墨 —— 10sp 灰字里换色已经足够区分，不为加粗另造视图
            rv.setTextColor(axis, if (bar.today) f.ink else context.getColor(R.color.widget_v22_text_3))
        }
    }

    /** v37 .footer：勾选圈（可选）+ 左标签 + 值 + 右注。 */
    fun bindFooter(
        context: Context,
        rv: RemoteViews,
        left: String,
        value: String,
        right: String?,
        checkMark: Boolean = false
    ) {
        val ck = rvId(context, "widget_v22_footer_ck")
        rv.setViewVisibility(ck, if (checkMark) View.VISIBLE else View.GONE)
        if (checkMark) {
            rv.setInt(ck, "setBackgroundResource", R.drawable.widget_shape_circle)
            rv.setInt(ck, "setColorFilter", context.getColor(R.color.widget_v22_green))
        }
        rv.setTextViewText(rvId(context, "widget_v22_footer_l"), left)
        rv.setTextColor(rvId(context, "widget_v22_footer_l"), context.getColor(R.color.widget_v22_text_3))
        rv.setTextViewText(rvId(context, "widget_v22_footer_v"), value)
        rv.setTextColor(rvId(context, "widget_v22_footer_v"), context.getColor(R.color.widget_v22_text_1))
        rv.setTextViewText(rvId(context, "widget_v22_footer_r"), right ?: "")
        rv.setTextColor(rvId(context, "widget_v22_footer_r"), context.getColor(R.color.widget_v22_text_2))
    }

    /** v37 .minichips：四块分类小胶囊；不足四块 GONE。 */
    data class MiniChip(val name: String, val value: String, val familyIdx: Int)

    fun bindMiniChips(context: Context, rv: RemoteViews, chips: List<MiniChip>) {
        for (i in 1..4) {
            val root = rvId(context, "widget_v22_mc_$i")
            val chip = chips.getOrNull(i - 1)
            if (chip == null) {
                rv.setViewVisibility(root, View.GONE)
                continue
            }
            rv.setViewVisibility(root, View.VISIBLE)
            val f = family(context, chip.familyIdx)
            // 底色打在块内底图 ImageView 上（LinearLayout 没有 setColorFilter，见 widget_v22_minichips.xml）
            rv.setInt(rvId(context, "widget_v22_mc_${i}_bg"), "setColorFilter", f.tint)
            rv.setTextColor(rvId(context, "widget_v22_mc_${i}_n"), f.ink)
            rv.setTextViewText(rvId(context, "widget_v22_mc_${i}_n"), chip.name)
            rv.setTextColor(rvId(context, "widget_v22_mc_${i}_v"), f.ink)
            rv.setTextViewText(rvId(context, "widget_v22_mc_${i}_v"), chip.value)
        }
    }

    /** v37 明细行（日期 + 色点 + 名 + 值）。动态渲染进 [hostId]（widget_tall_rows）。 */
    data class DualItem(val date: String, val name: String, val value: String, val familyIdx: Int)

    /**
     * 明细行：逐行 addView 进宿主（旧静态四行版（已删）.xml 无布局引用，已废）。
     * 行高/字号按 widget_v22_row；不足的行直接不进（不摆空行）。
     */
    fun bindRows(context: Context, rv: RemoteViews, hostId: Int, items: List<DualItem>) {
        rv.removeAllViews(hostId)
        items.forEach { item ->
            val row = widgetViews(context, R.layout.widget_v22_row)
            val f = family(context, item.familyIdx)
            row.setTextViewText(R.id.widget_v22_row_t, item.date)
            row.setTextColor(R.id.widget_v22_row_t, context.getColor(R.color.widget_v22_text_2))
            row.setInt(R.id.widget_v22_row_d, "setColorFilter", f.d)
            row.setTextViewText(R.id.widget_v22_row_n, item.name)
            row.setTextColor(R.id.widget_v22_row_n, context.getColor(R.color.widget_v22_text_1))
            row.setTextViewText(R.id.widget_v22_row_v, item.value)
            row.setTextColor(R.id.widget_v22_row_v, f.ink)
            rv.addView(hostId, row)
        }
        rv.setViewVisibility(hostId, if (items.isEmpty()) View.GONE else View.VISIBLE)
    }

    /** v37 净资产 4×4 的双比例行（标签 + 5px 条 + 百分比）。 */
    data class RatioRow(val label: String, val percent: Int, val familyIdx: Int)

    /** 比例行动态渲染进 [hostId]（widget_tall_rows）；percent 0..100。 */
    fun bindRatioRows(context: Context, rv: RemoteViews, hostId: Int, rows: List<RatioRow>) {
        rv.removeAllViews(hostId)
        rows.forEach { item ->
            val row = widgetViews(context, R.layout.widget_v22_ratio_row)
            val f = family(context, item.familyIdx)
            row.setInt(R.id.widget_v22_ratio_dot, "setColorFilter", f.d)
            row.setTextViewText(R.id.widget_v22_ratio_lbl, item.label)
            row.setTextColor(R.id.widget_v22_ratio_lbl, context.getColor(R.color.widget_v22_text_2))
            row.setInt(R.id.widget_v22_ratio_fill, "setColorFilter", f.d)
            row.setInt(R.id.widget_v22_ratio_fill, "setImageLevel", item.percent.coerceIn(0, 100) * 100)
            row.setTextViewText(R.id.widget_v22_ratio_val, "${item.percent.coerceIn(0, 100)}%")
            row.setTextColor(R.id.widget_v22_ratio_val, f.ink)
            rv.addView(hostId, row)
        }
        rv.setViewVisibility(hostId, if (rows.isEmpty()) View.GONE else View.VISIBLE)
    }

    /** v37 .empty：圆底图标 + 标题 + 引导；icon 用本族主色底、提亮图形。 */
    fun bindEmpty(context: Context, rv: RemoteViews, iconRes: Int, title: String, hint: String, familyIdx: Int) {
        val f = family(context, familyIdx)
        rv.setInt(rvId(context, "widget_v22_empty_circle"), "setColorFilter", f.tint)
        rv.setInt(rvId(context, "widget_v22_empty_ico"), "setImageResource", iconRes)
        rv.setInt(rvId(context, "widget_v22_empty_ico"), "setColorFilter", f.ink)
        rv.setTextViewText(rvId(context, "widget_v22_empty_t"), title)
        rv.setTextColor(rvId(context, "widget_v22_empty_t"), context.getColor(R.color.widget_v22_text_1))
        rv.setTextViewText(rvId(context, "widget_v22_empty_d"), hint)
        rv.setTextColor(rvId(context, "widget_v22_empty_d"), context.getColor(R.color.widget_v22_text_2))
    }

    private fun rvId(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "id", context.packageName)

    // ── v37 4×4 通用仪表盘（widget_v22_tall）的区块助手 ──

    /** 近 7 天轴标签（日 一 二 … 六，末列 = 今天；样本口径：今天在最后）。 */
    fun last7Axis(today: java.time.LocalDate = java.time.LocalDate.now()): List<String> {
        val dow = today.dayOfWeek.value % 7 // 周日=0
        val names = arrayOf("日", "一", "二", "三", "四", "五", "六")
        return (0..6).map { offset -> names[(dow - 6 + offset + 14) % 7] }
    }

    /** 头部：圆标（昼夜对）+ 图形 + 标题 + 右侧 meta。glyph 传 0 = 无图形。 */
    fun bindTallHeader(
        context: Context,
        rv: RemoteViews,
        glyphRes: Int,
        title: String,
        meta: String?,
        familyIdx: Int
    ) {
        rv.setInt(rvId(context, "widget_tall_badge"), "setColorFilter", context.getColor(BADGE_BG[((familyIdx % 8) + 8) % 8]))
        val glyph = rvId(context, "widget_tall_glyph")
        if (glyphRes != 0) {
            rv.setViewVisibility(glyph, View.VISIBLE)
            rv.setInt(glyph, "setImageResource", glyphRes)
            rv.setInt(glyph, "setColorFilter", context.getColor(BADGE_FG[((familyIdx % 8) + 8) % 8]))
        } else {
            rv.setViewVisibility(glyph, View.GONE)
        }
        rv.setTextViewText(rvId(context, "widget_tall_title"), title)
        rv.setTextColor(rvId(context, "widget_tall_title"), context.getColor(R.color.widget_v22_text_1))
        rv.setTextViewText(rvId(context, "widget_tall_meta"), meta ?: "")
        rv.setTextColor(rvId(context, "widget_tall_meta"), context.getColor(R.color.widget_v22_text_2))
    }

    /** 主数字行：40sp 主数字 + 右侧说明（可空）。 */
    fun bindTallHero(context: Context, rv: RemoteViews, hero: String, label: String?) {
        rv.setTextViewText(rvId(context, "widget_tall_hero"), hero)
        rv.setTextColor(rvId(context, "widget_tall_hero"), context.getColor(R.color.widget_v22_text_1))
        rv.setTextViewText(rvId(context, "widget_tall_hero_lb"), label ?: "")
        rv.setTextColor(rvId(context, "widget_tall_hero_lb"), context.getColor(R.color.widget_v22_text_2))
    }

    /** 区块显隐（压缩优先级的执行点）。 */
    fun tallSection(context: Context, rv: RemoteViews, name: String, visible: Boolean) {
        rv.setViewVisibility(rvId(context, "widget_tall_$name"), if (visible) View.VISIBLE else View.GONE)
    }

    /** 4×4 布局的全部区块名（[tallSections] 的批量版用）。 */
    val TALL_SECTIONS = listOf("kpis", "chart", "grid", "minichips", "strip_host", "rows", "footer", "empty")

    /** 批量收起/显示区块：只让 [visible] 里的区块可见，其余一律 GONE（各族收尾用）。 */
    fun tallSections(context: Context, rv: RemoteViews, vararg visible: String) {
        val keep = visible.toSet()
        TALL_SECTIONS.forEach { name -> tallSection(context, rv, name, name in keep) }
    }

    /** 粗进度条（widget_tall_bt_fill，ClipDrawable level = percent×100）。 */
    fun bindTallProgress(context: Context, rv: RemoteViews, percent: Int, familyIdx: Int) {
        val f = family(context, familyIdx)
        rv.setInt(rvId(context, "widget_tall_bt_fill"), "setColorFilter", f.d)
        rv.setInt(rvId(context, "widget_tall_bt_fill"), "setImageLevel", percent.coerceIn(0, 100) * 100)
    }

    /**
     * 头部涨跌胶囊（v37 净资产 4×4，稿 §2-5 .pill）：文字 + 涨/跌底色 + 同色字。
     * [text] 传 null = 整枚隐藏（本月没有进出时不给空胶囊）。
     */
    fun bindTallPill(context: Context, rv: RemoteViews, text: String?, up: Boolean) {
        val pill = rvId(context, "widget_tall_pill")
        if (text == null) {
            rv.setViewVisibility(pill, View.GONE)
            return
        }
        rv.setViewVisibility(pill, View.VISIBLE)
        rv.setInt(
            pill,
            "setBackgroundResource",
            if (up) R.drawable.widget_delta_pill_up else R.drawable.widget_delta_pill_down
        )
        rv.setViewPadding(pill, dp2px(context, 9), dp2px(context, 3), dp2px(context, 9), dp2px(context, 3))
        rv.setTextViewText(pill, text)
        rv.setTextColor(
            pill,
            context.getColor(if (up) R.color.widget_delta_up else R.color.widget_delta_down)
        )
    }

    private fun dp2px(context: Context, dp: Int): Int =
        Math.round(dp * context.resources.displayMetrics.density)

    /** 空槽灰：点阵空点用 outline、刻度/格子空槽用 line（稿 --outline / --line）。 */
    private fun emptySlot(context: Context, dots: Boolean): Int =
        context.getColor(if (dots) R.color.widget_v22_outline else R.color.widget_v22_line)

    /**
     * 胶囊行：`sub` / `value` 传 null 会 GONE；`showDot` 控制色点。
     * 数值着 ink、底着 tint、点着 d —— 与稿 .ch 三档一一对应。
     */
    fun bindChip(
        context: Context,
        rv: RemoteViews,
        name: CharSequence,
        sub: CharSequence?,
        value: CharSequence?,
        familyIdx: Int,
        showDot: Boolean = true
    ) {
        val f = family(context, familyIdx)
        rv.setInt(R.id.widget_v22_chip_bg, "setColorFilter", f.tint)
        rv.setInt(R.id.widget_v22_chip_dot, "setColorFilter", f.d)
        rv.setViewVisibility(R.id.widget_v22_chip_dot, if (showDot) View.VISIBLE else View.GONE)
        rv.setTextViewText(R.id.widget_v22_chip_name, name)
        if (sub == null) {
            rv.setViewVisibility(R.id.widget_v22_chip_sub, View.GONE)
        } else {
            rv.setTextViewText(R.id.widget_v22_chip_sub, sub)
        }
        if (value == null) {
            rv.setViewVisibility(R.id.widget_v22_chip_value, View.GONE)
        } else {
            rv.setTextViewText(R.id.widget_v22_chip_value, value)
            rv.setTextColor(R.id.widget_v22_chip_value, f.ink)
        }
    }

    private val TICK_IDS = intArrayOf(
        R.id.widget_v22_tick_1, R.id.widget_v22_tick_2, R.id.widget_v22_tick_3,
        R.id.widget_v22_tick_4, R.id.widget_v22_tick_5, R.id.widget_v22_tick_6,
        R.id.widget_v22_tick_7
    )

    /** 分段刻度：`total` ∈ 1..7（超出只画 7 格），`on` 为亮段数（= 已完成）。 */
    fun bindTicks(context: Context, rv: RemoteViews, total: Int, on: Int, familyIdx: Int) {
        val f = family(context, familyIdx)
        val empty = emptySlot(context, dots = false)
        val n = total.coerceIn(1, TICK_IDS.size)
        TICK_IDS.forEachIndexed { i, id ->
            if (i >= n) {
                rv.setViewVisibility(id, View.GONE)
            } else {
                rv.setInt(id, "setColorFilter", if (i < on) f.d else empty)
            }
        }
    }

    private val DPD_IDS = intArrayOf(
        R.id.widget_v22_dpd_1, R.id.widget_v22_dpd_2, R.id.widget_v22_dpd_3,
        R.id.widget_v22_dpd_4, R.id.widget_v22_dpd_5
    )

    /** 点阵（≤5）：`on` 亮点数 = 完成数。分母大于 5 的指标不要用点阵。 */
    fun bindDpd(context: Context, rv: RemoteViews, on: Int, familyIdx: Int) {
        val f = family(context, familyIdx)
        val empty = emptySlot(context, dots = true)
        DPD_IDS.forEachIndexed { i, id ->
            rv.setInt(id, "setColorFilter", if (i < on) f.d else empty)
        }
    }

    private val DOTG_IDS = intArrayOf(
        R.id.widget_v22_dotg_1, R.id.widget_v22_dotg_2, R.id.widget_v22_dotg_3, R.id.widget_v22_dotg_4,
        R.id.widget_v22_dotg_5, R.id.widget_v22_dotg_6, R.id.widget_v22_dotg_7, R.id.widget_v22_dotg_8
    )

    /** 大点阵 2×4（≤8）：生活计数用。 */
    fun bindDotg(context: Context, rv: RemoteViews, on: Int, familyIdx: Int) {
        val f = family(context, familyIdx)
        val empty = emptySlot(context, dots = true)
        DOTG_IDS.forEachIndexed { i, id ->
            rv.setInt(id, "setColorFilter", if (i < on) f.d else empty)
        }
    }

    private val DCELL_IDS = intArrayOf(
        R.id.widget_v22_dcell_1, R.id.widget_v22_dcell_2, R.id.widget_v22_dcell_3,
        R.id.widget_v22_dcell_4, R.id.widget_v22_dcell_5, R.id.widget_v22_dcell_6,
        R.id.widget_v22_dcell_7, R.id.widget_v22_dcell_8, R.id.widget_v22_dcell_9,
        R.id.widget_v22_dcell_10
    )

    private val DCELL_BG_IDS = intArrayOf(
        R.id.widget_v22_dcell_bg_1, R.id.widget_v22_dcell_bg_2, R.id.widget_v22_dcell_bg_3,
        R.id.widget_v22_dcell_bg_4, R.id.widget_v22_dcell_bg_5, R.id.widget_v22_dcell_bg_6,
        R.id.widget_v22_dcell_bg_7, R.id.widget_v22_dcell_bg_8, R.id.widget_v22_dcell_bg_9,
        R.id.widget_v22_dcell_bg_10
    )

    private val DCELL_TX_IDS = intArrayOf(
        R.id.widget_v22_dcell_tx_1, R.id.widget_v22_dcell_tx_2, R.id.widget_v22_dcell_tx_3,
        R.id.widget_v22_dcell_tx_4, R.id.widget_v22_dcell_tx_5, R.id.widget_v22_dcell_tx_6,
        R.id.widget_v22_dcell_tx_7, R.id.widget_v22_dcell_tx_8, R.id.widget_v22_dcell_tx_9,
        R.id.widget_v22_dcell_tx_10
    )

    /**
     * 日历条：`labels` 逐格日期文本（≤10），`eventIdx` 为事件日格（0 基，通常 = 末格）。
     * 事件格：族色底 + 白字；今天格（[todayIdx]）：族色描边 + ink 字（稿 .dcell.today）；
     * 其余：line 灰底 + 次文色。不足的格 GONE。
     */
    fun bindDstrip(
        context: Context,
        rv: RemoteViews,
        labels: List<String>,
        eventIdx: Int,
        familyIdx: Int,
        todayIdx: Int? = null
    ) {
        val f = family(context, familyIdx)
        val empty = emptySlot(context, dots = false)
        val sub = context.getColor(R.color.widget_v22_text_2)
        val n = labels.size.coerceAtMost(DCELL_IDS.size)
        DCELL_IDS.forEachIndexed { i, id ->
            if (i >= n) {
                rv.setViewVisibility(id, View.GONE)
            } else {
                rv.setTextViewText(DCELL_TX_IDS[i], labels[i])
                val isEvent = i == eventIdx
                val isToday = todayIdx != null && i == todayIdx
                when {
                    isEvent -> {
                        rv.setImageViewResource(DCELL_BG_IDS[i], R.drawable.widget_v22_round5)
                        rv.setInt(DCELL_BG_IDS[i], "setColorFilter", f.d)
                        rv.setTextColor(DCELL_TX_IDS[i], WHITE)
                    }
                    isToday -> {
                        rv.setImageViewResource(DCELL_BG_IDS[i], R.drawable.widget_v22_dcell_today)
                        rv.setInt(DCELL_BG_IDS[i], "setColorFilter", f.d)
                        rv.setTextColor(DCELL_TX_IDS[i], f.ink)
                    }
                    else -> {
                        rv.setImageViewResource(DCELL_BG_IDS[i], R.drawable.widget_v22_round5)
                        rv.setInt(DCELL_BG_IDS[i], "setColorFilter", empty)
                        rv.setTextColor(DCELL_TX_IDS[i], sub)
                    }
                }
            }
        }
    }

    private val SEG_IDS = (1..24).map { i ->
        // 24 槽 id 是 widget_v22_seg_1..24，反射拿不到常量名就硬表登记
        when (i) {
            1 -> R.id.widget_v22_seg_1
            2 -> R.id.widget_v22_seg_2
            3 -> R.id.widget_v22_seg_3
            4 -> R.id.widget_v22_seg_4
            5 -> R.id.widget_v22_seg_5
            6 -> R.id.widget_v22_seg_6
            7 -> R.id.widget_v22_seg_7
            8 -> R.id.widget_v22_seg_8
            9 -> R.id.widget_v22_seg_9
            10 -> R.id.widget_v22_seg_10
            11 -> R.id.widget_v22_seg_11
            12 -> R.id.widget_v22_seg_12
            13 -> R.id.widget_v22_seg_13
            14 -> R.id.widget_v22_seg_14
            15 -> R.id.widget_v22_seg_15
            16 -> R.id.widget_v22_seg_16
            17 -> R.id.widget_v22_seg_17
            18 -> R.id.widget_v22_seg_18
            19 -> R.id.widget_v22_seg_19
            20 -> R.id.widget_v22_seg_20
            21 -> R.id.widget_v22_seg_21
            22 -> R.id.widget_v22_seg_22
            23 -> R.id.widget_v22_seg_23
            else -> R.id.widget_v22_seg_24
        }
    }

    /**
     * 24 段等宽槽位各归哪一段：按量占比切分，累计边界四舍五入。
     * 纯函数（单测直接验「段宽 = 比例」，渲染层比较 ColorFilter 不可靠）。
     * 原在 AssetWidgetProvider，v22 分段条全族通用后搬到这里；那边留同签名委托。
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
     * 分段条：`counts` 各段的量（段宽 = 占比），`colors` 各段颜色（与 counts 同长）。
     * 溢出聚合段由调用方排在末位并给 c6 粉或中性色 —— 颜色是调用方语义，这里只画。
     * 分组首/末段换圆头、组间 3dp 缝：与 AssetWidgetProvider 的占比条同一套画法。
     */
    fun bindSeg(context: Context, rv: RemoteViews, counts: List<Int>, colors: List<Int>) {
        if (colors.isEmpty()) return
        val slots = SEG_IDS.size
        val owners = slotOwners(counts, slots)
        val gapPx = Math.round(2 * context.resources.displayMetrics.density)
        SEG_IDS.forEachIndexed { slot, id ->
            rv.setViewVisibility(id, View.VISIBLE)
            val isGroupStart = slot == 0 || owners[slot] != owners[slot - 1]
            val isGroupEnd = slot == slots - 1 || owners[slot] != owners[slot + 1]
            rv.setImageViewResource(
                id,
                when {
                    isGroupStart && isGroupEnd -> R.drawable.widget_seg_slot
                    isGroupStart -> R.drawable.widget_seg_slot_start
                    isGroupEnd -> R.drawable.widget_seg_slot_end
                    else -> R.drawable.widget_seg_slot_flat
                }
            )
            rv.setViewPadding(id, if (isGroupStart && slot > 0) gapPx else 0, 0, 0, 0)
            val owner = owners[slot].coerceIn(0, colors.size - 1)
            rv.setInt(id, "setColorFilter", colors[owner])
        }
    }

    private val VST_IDS = (1..24).map { i ->
        when (i) {
            1 -> R.id.widget_v22_vst_1
            2 -> R.id.widget_v22_vst_2
            3 -> R.id.widget_v22_vst_3
            4 -> R.id.widget_v22_vst_4
            5 -> R.id.widget_v22_vst_5
            6 -> R.id.widget_v22_vst_6
            7 -> R.id.widget_v22_vst_7
            8 -> R.id.widget_v22_vst_8
            9 -> R.id.widget_v22_vst_9
            10 -> R.id.widget_v22_vst_10
            11 -> R.id.widget_v22_vst_11
            12 -> R.id.widget_v22_vst_12
            13 -> R.id.widget_v22_vst_13
            14 -> R.id.widget_v22_vst_14
            15 -> R.id.widget_v22_vst_15
            16 -> R.id.widget_v22_vst_16
            17 -> R.id.widget_v22_vst_17
            18 -> R.id.widget_v22_vst_18
            19 -> R.id.widget_v22_vst_19
            20 -> R.id.widget_v22_vst_20
            21 -> R.id.widget_v22_vst_21
            22 -> R.id.widget_v22_vst_22
            23 -> R.id.widget_v22_vst_23
            else -> R.id.widget_v22_vst_24
        }
    }

    /**
     * 纵向堆叠条（稿 .vstack）：与 [bindSeg] 同语义，上段先占（counts[0] 从最上铺下来）。
     * 端头圆角只在最上/最下槽给整段形状，中间平头相接。
     */
    fun bindVStack(context: Context, rv: RemoteViews, counts: List<Int>, colors: List<Int>) {
        if (colors.isEmpty()) return
        val slots = VST_IDS.size
        val owners = slotOwners(counts, slots)
        val gapPx = Math.round(2 * context.resources.displayMetrics.density)
        VST_IDS.forEachIndexed { slot, id ->
            rv.setViewVisibility(id, View.VISIBLE)
            val isGroupStart = slot == 0 || owners[slot] != owners[slot - 1]
            val isGroupEnd = slot == slots - 1 || owners[slot] != owners[slot + 1]
            rv.setImageViewResource(
                id,
                when {
                    isGroupStart && isGroupEnd -> R.drawable.widget_seg_slot
                    isGroupStart -> R.drawable.widget_seg_slot_start
                    isGroupEnd -> R.drawable.widget_seg_slot_end
                    else -> R.drawable.widget_seg_slot_flat
                }
            )
            rv.setViewPadding(id, 0, if (isGroupStart && slot > 0) gapPx else 0, 0, 0)
            val owner = owners[slot].coerceIn(0, colors.size - 1)
            rv.setInt(id, "setColorFilter", colors[owner])
        }
    }

    private val BAR7_IDS = intArrayOf(
        R.id.widget_v22_bar_0, R.id.widget_v22_bar_1, R.id.widget_v22_bar_2,
        R.id.widget_v22_bar_3, R.id.widget_v22_bar_4, R.id.widget_v22_bar_5,
        R.id.widget_v22_bar_6
    )

    /** 竖向 clip 的两档：level 0..10000 对应 0..100% 高。 */
    private const val BAR_LEVEL_FULL = 10000
    private const val BAR_LEVEL_MIN = 600

    /** 12 槽柱图的非零保底高度（%）：稿 `.cbars i{min-height:3px}`，避免小值完全看不见。 */
    private const val MIN_BAR_PERCENT = 2

    /**
     * 近 7 天柱阵（稿 .spark）：柱高 = 当天 ÷ 七天最大值。
     * v22 配色：柱 = `color`（账单跟用户配色走，默认即稿内橙）；**末柱（今天）恒全不透明**，
     * 其余 32%（稿 opacity .32）；空柱 line 灰基线（SRC_ATOP 只换色，半透必须走 imageAlpha ——
     * 视图树复用不写回会残留）。`shown` < 7 时收尾槽 GONE（2×1 微柱档用 5 / 趋势 2×1 用 4）。
     */
    /**
     * 七天柱（v22 稿口径）：全部实色多色 —— 最高一天橙（c5）、次高紫（c4）、其余族蓝；
     * 空柱走轨道色。旧「单色 + 非今天 32% 淡化」随稿改版退场。
     */
    fun bindBars7(context: Context, rv: RemoteViews, daily: List<Long>, color: Int, shown: Int = 7) {
        val track = context.getColor(R.color.widget_v22_line)
        val purple = context.getColor(R.color.widget_v22_c4)
        val orange = context.getColor(R.color.widget_v22_c5)
        val max = daily.maxOrNull() ?: 0L
        val second = daily.filter { it < max }.maxOrNull() ?: 0L
        val n = shown.coerceIn(1, BAR7_IDS.size)
        BAR7_IDS.forEachIndexed { index, id ->
            if (index >= n) {
                rv.setViewVisibility(id, View.GONE)
                return@forEachIndexed
            }
            rv.setViewVisibility(id, View.VISIBLE)
            val value = daily.getOrElse(index) { 0L }
            val isEmpty = max <= 0L || value <= 0L
            val level = if (isEmpty) {
                BAR_LEVEL_MIN
            } else {
                (value * BAR_LEVEL_FULL / max).toInt().coerceIn(BAR_LEVEL_MIN, BAR_LEVEL_FULL)
            }
            rv.setInt(id, "setColorFilter", when {
                isEmpty -> track
                value >= max && max > 0 -> orange
                second > 0 && value >= second -> purple
                else -> color
            })
            rv.setInt(id, "setImageAlpha", 255)
            rv.setInt(id, "setImageLevel", level)
        }
    }

    private const val WHITE = -0x1
}
