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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * 净资产组件（v22/v37）：资产 − 负债 的净值 + 涨跌 + 资产/负债拆分。
 *
 * 数据口径（全部来自已有账单/钱包数据，无额外字段）：
 * - 资产 = 非信用卡账户余额合计（钱包页 [WalletDao.getTotalBalance] 同源）；
 * - 负债 = 信用卡余额合计的绝对值（[WalletDao.getTotalCreditCardBalance]）；
 * - 净值 = 资产 − 负债；
 * - 涨跌 = 本月净收入 ÷ 期初净值（期初 = 净值 − 本月净收入；期初 ≤ 0 隐胶囊，同 v4 口径）。
 *
 * 四档构图按 [WidgetSizeClass] 动态切档（23 形态的 CELLS 标签是选择器元数据，不动）：
 * WIDE 4×2 hero+分段条+两枚胶囊+眼睛 / SQUARE 2×2 纵向堆叠条+两行 / BAR 4×1 三列读数+条 /
 * TALL 4×4（v37 §2-5）hero+涨跌胶囊+三 KPI+近 12 个月净值柱图+资产/负债双比例行；
 * 压缩态（高 250~299dp）砍双比例行（稿 §04 压缩优先级②）。
 *
 * **近 12 个月净值序列**没有历史表，按账单月度差额重建：月末净值(m) = 当前净值 −
 * 之后各月（收入−支出）的累计（[monthlyNetSeries]）。钱包余额只随账单进出变化
 * （自有账户间转账净额为 0），重建值与真值一致；账单覆盖不到的年月只是没有明细。
 *
 * 隐私眼睛只进 WIDE/SQUARE（小档空间不够，跟随全局态）：点按切换 [WidgetPrivacy]，
 * 金额换圆点、比例条整条中性灰、涨幅去方向 —— 三样一起收，只藏数字等于没藏。
 * TALL 同口径：隐藏态 hero/KPI 打码、胶囊隐藏、柱图与双比例行整块收起（序列本身也是结构）。
 */
// open：NetWorthWide/Bar/Mini 尺寸形态薄子类继承（v22 四档）
open class NetWorthWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun walletDao(): com.palmnote.data.db.dao.WalletDao
        fun billDao(): com.palmnote.data.db.dao.BillDao
    }

    override suspend fun onUpdateAsync(appContext: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext.applicationContext,
            WidgetEntryPoint::class.java
        )
        // 资产 = 非信用卡合计；负债 = 信用卡余额绝对值（库里正负号两种记法都认）
        val assets = entryPoint.walletDao().getTotalBalance().first() ?: 0L
        val liabs = kotlin.math.abs(entryPoint.walletDao().getTotalCreditCardBalance().first() ?: 0L)
        val delta = fetchMonthDelta(entryPoint)
        // 近 12 个月净值序列：月度收支差额倒推（见类 KDoc）
        val fromMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now().minusMonths(11))
        val flows = entryPoint.billDao().getMonthlyFlowsSince(fromMonth).first()
        val monthlyNet = monthlyNetSeries(assets - liabs, flows, LocalDate.now())

        publish(appContext, appWidgetManager, appWidgetIds) { context, appWidgetId, _ ->
            val hidden = WidgetPrivacy.isHidden(context)
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                MIN_WIDTH_DP,
                MIN_HEIGHT_DP
            ) { width, height ->
                bindViews(
                    context, appWidgetId,
                    totalBalance = assets - liabs,
                    widthDp = width,
                    monthDelta = delta,
                    heightDp = height,
                    liabs = liabs,
                    hidden = hidden,
                    monthlyNet = monthlyNet
                )
            }
        }
    }

    /** 本月净收入（收入 − 支出）；账单表没有账本维度，这里就是全账本口径。 */
    private suspend fun fetchMonthDelta(entryPoint: WidgetEntryPoint): Long {
        val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
        val income = entryPoint.billDao().getMonthlyIncome(yearMonth).first() ?: 0L
        val expense = entryPoint.billDao().getMonthlyExpense(yearMonth).first() ?: 0L
        return income - expense
    }

    /**
     * 按档渲染（[WidgetSizeClass]）。`hidden` 为金额隐私态（见 [WidgetPrivacy]）。
     * 金额单位是分，格式化走 [WidgetData.formatMoneyCompact]（万/k 折叠与 App 同源）。
     *
     * 参数名 `totalBalance` 保留 v4 口径（既有单测的命名实参），语义是**净值**；
     * 资产 = 净值 + 负债。`liabs` 缺省 0 = 不拆分（渲染上就是整条单段）。
     * [monthlyNet] 是近 12 个月月末净值（旧→新，末位 = 当前净值；见 [monthlyNetSeries]），
     * 只有 4×4 柱图用。
     */
    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        totalBalance: Long,
        widthDp: Int,
        monthDelta: Long = 0L,
        heightDp: Int = MIN_HEIGHT_DP,
        liabs: Long = 0L,
        hidden: Boolean = false,
        monthlyNet: List<Long> = emptyList()
    ): RemoteViews {
        val net = totalBalance
        val assets = totalBalance + liabs
        val size = WidgetSizeClass.of(widthDp, heightDp)
        val layout = when (size) {
            WidgetSizeClass.TALL -> R.layout.widget_v22_tall
            WidgetSizeClass.WIDE -> R.layout.widget_net_v22_wide
            WidgetSizeClass.SQUARE -> R.layout.widget_net_v22_square
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> R.layout.widget_net_v22_bar
        }
        val views = widgetViews(context, layout)
        WidgetV22.bindCardBg(views, size)
        // 点卡片进记账页看明细（v4 既有交互保留）
        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_NET_WORTH + appWidgetId,
                WidgetDeepLink.TAB_BILL
            )
        )
        when (size) {
            WidgetSizeClass.TALL ->
                bindTall4x4(
                    context, views, assets, liabs, net, monthDelta, hidden, monthlyNet,
                    compact = heightDp < TALL_FULL_DP
                )
            WidgetSizeClass.WIDE -> bindWide(context, views, appWidgetId, assets, liabs, net, monthDelta, hidden)
            WidgetSizeClass.SQUARE -> bindSquare(context, views, appWidgetId, assets, liabs, net, monthDelta, hidden)
            WidgetSizeClass.BAR, WidgetSizeClass.CHIP -> bindBar(context, views, assets, liabs, net, hidden)
        }
        return views
    }

    /**
     * 4×4（v37 §2-5）：hero + 涨跌胶囊 + 三 KPI（资产/负债/年化）+ 近 12 个月净值柱图 +
     * 资产/负债双比例行。压缩态砍双比例行（稿 §04 优先级②）；隐私态见类 KDoc。
     */
    private fun bindTall4x4(
        context: Context,
        views: RemoteViews,
        assets: Long,
        liabs: Long,
        net: Long,
        monthDelta: Long,
        hidden: Boolean,
        monthlyNet: List<Long>,
        compact: Boolean
    ) {
        // 徽章由 bindTallHeader 统一上色（tall 布局没有 widget_net_badge，不能再设）
        val opening = net - monthDelta
        // 涨跌胶囊：与 WIDE 同一口径（本月净收入 ÷ 期初净值）；隐藏态整枚收起（方向也藏）
        WidgetV22.bindTallPill(
            context, views,
            text = if (hidden) null else deltaPercentText(monthDelta, opening),
            up = monthDelta > 0
        )
        WidgetV22.bindTallHeader(
            context, views,
            glyphRes = R.drawable.widget_ic_yuan_white,
            title = context.getString(R.string.widget_net_worth_title),
            meta = null,
            familyIdx = 1
        )
        WidgetV22.bindTallHero(context, views, amountText(context, net, hidden), null)
        // KPI：资产 / 负债 / 年化（年化要期初 > 0 才算得出来）
        val annualized = annualizedText(monthDelta, opening)
        WidgetV22.bindKpis(
            context, views,
            listOfNotNull(
                WidgetV22.Kpi(
                    context.getString(R.string.widget_net_label_assets),
                    amountText(context, assets, hidden), null, 0
                ),
                WidgetV22.Kpi(
                    context.getString(R.string.widget_net_label_liab),
                    amountText(context, liabs, hidden), null, 4
                ),
                annualized?.let {
                    WidgetV22.Kpi(context.getString(R.string.widget_net_label_annualized), it, null, 1)
                }
            )
        )
        bindTallChart(context, views, monthlyNet, hidden)
        // 双比例行：资产/负债占比；隐私态或压缩态收起
        val total = assets + liabs
        val showRatio = !hidden && !compact && total > 0L
        if (showRatio) {
            val assetsPct = Math.round(assets * 100.0 / total).toInt()
            WidgetV22.bindRatioRows(
                context, views,
                R.id.widget_tall_rows,
                listOf(
                    WidgetV22.RatioRow(context.getString(R.string.widget_net_label_assets), assetsPct, 0),
                    WidgetV22.RatioRow(
                        context.getString(R.string.widget_net_label_liab),
                        100 - assetsPct,
                        4
                    )
                )
            )
        }
        WidgetV22.tallSections(context, views, "kpis", "chart", if (showRatio) "rows" else "")
    }

    /** 4×4 柱图：近 12 个月净值（旧→新，末柱青 + 金额标）；隐私态或数据不足整块收起。 */
    private fun bindTallChart(context: Context, views: RemoteViews, monthlyNet: List<Long>, hidden: Boolean) {
        if (hidden || monthlyNet.size < 2) return
        val max = monthlyNet.max().coerceAtLeast(1L)
        val today = LocalDate.now()
        val bars = monthlyNet.mapIndexed { i, v ->
            val month = today.minusMonths((monthlyNet.size - 1 - i).toLong())
            WidgetV22.ChartBar(
                percent = ((v.toDouble() / max) * 100).toInt().coerceAtLeast(0),
                familyIdx = if (i == monthlyNet.lastIndex) 6 else 1,
                label = if (i == monthlyNet.lastIndex) {
                    WidgetData.formatChartValue(v)
                } else {
                    null
                },
                // 轴末格 = 本月
                today = i == monthlyNet.lastIndex,
                axis = month.format(DateTimeFormatter.ofPattern("M"))
            )
        }
        WidgetV22.bindChart(context, views, context.getString(R.string.widget_net_chart_title), null, bars)
    }

    /** 涨跌百分比文本（一位小数）；期初 ≤ 0 或没有进出返回 null（整枚隐藏）。 */
    internal fun deltaPercentText(monthDelta: Long, opening: Long): String? {
        if (monthDelta == 0L || opening <= 0L) return null
        val up = monthDelta > 0
        val permille = monthDelta * PERCENT_MILLI / opening
        return (if (up) "▲" else "▼") + String.format(
            Locale.getDefault(),
            "%.1f%%",
            kotlin.math.abs(permille) / PERCENT_DIVISOR
        )
    }

    /** 年化收益率（月净收入 ×12 ÷ 期初净值）；期初 ≤ 0 或没有进出返回 null。 */
    internal fun annualizedText(monthDelta: Long, opening: Long): String? {
        if (monthDelta == 0L || opening <= 0L) return null
        val up = monthDelta > 0
        val permille = monthDelta * 12 * PERCENT_MILLI / opening
        return (if (up) "+" else "−") + String.format(
            Locale.getDefault(),
            "%.1f%%",
            kotlin.math.abs(permille) / PERCENT_DIVISOR
        )
    }

    /** 4×2：hero + 涨跌胶囊 + 分段条 + 资产/负债两枚胶囊 + 眼睛。 */
    private fun bindWide(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        assets: Long,
        liabs: Long,
        net: Long,
        monthDelta: Long,
        hidden: Boolean
    ) {
        bindBadgeAndTitle(context, views)
        bindAmount(context, views, net, hidden)
        bindDelta(context, views, monthDelta, net - monthDelta, hidden)
        bindEye(context, views, appWidgetId, hidden)
        // 条：隐藏态整条中性灰（比例也藏，86/14 一样能反推资产负债结构）
        if (hidden) {
            addSeg(context, views, counts = listOf(1), colors = listOf(gray(context)))
        } else {
            addSeg(context, views, counts = listOf(assets, liabs), colors = listOf(c1(context), c5(context)))
        }
        val assetsChip = widgetViews(context, R.layout.widget_v22_chip)
        WidgetV22.bindChip(
            context, assetsChip,
            chipName(context, R.string.widget_net_label_assets),
            null, amountText(context, assets, hidden), 0
        )
        views.addView(R.id.widget_net_chip_host, assetsChip)
        val liabChip = widgetViews(context, R.layout.widget_v22_chip)
        WidgetV22.bindChip(
            context, liabChip,
            chipName(context, R.string.widget_net_label_liab),
            null, amountText(context, liabs, hidden), 4
        )
        views.addView(R.id.widget_net_chip_host, liabChip)
    }

    /** 2×2：hero + 眼睛 + 横向分段条（稿 §5 画廊封面口径）+ 资产/负债两行（行点与金额同色，稿口径）。 */
    private fun bindSquare(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        assets: Long,
        liabs: Long,
        net: Long,
        monthDelta: Long,
        hidden: Boolean
    ) {
        bindBadgeAndTitle(context, views)
        bindAmount(context, views, net, hidden)
        bindDelta(context, views, monthDelta, net - monthDelta, hidden)
        bindEye(context, views, appWidgetId, hidden)
        if (hidden) {
            addSeg(context, views, counts = listOf(1), colors = listOf(gray(context)))
        } else {
            addSeg(context, views, counts = listOf(assets, liabs), colors = listOf(c1(context), c5(context)))
        }
        views.setInt(R.id.widget_net_assets_dot, "setColorFilter", c1(context))
        views.setInt(R.id.widget_net_liab_dot, "setColorFilter", c5(context))
        views.setTextViewText(R.id.widget_net_assets_val, amountText(context, assets, hidden))
        views.setTextViewText(R.id.widget_net_liab_val, amountText(context, liabs, hidden))
    }

    /** 4×1：三列读数（净值/资产/负债）+ 下方分段条。 */
    private fun bindBar(
        context: Context,
        views: RemoteViews,
        assets: Long,
        liabs: Long,
        net: Long,
        hidden: Boolean
    ) {
        bindBadgeAndTitle(context, views)
        views.setTextViewText(R.id.widget_net_amount, amountText(context, net, hidden))
        views.setTextViewText(R.id.widget_net_assets_val, amountText(context, assets, hidden))
        views.setTextViewText(R.id.widget_net_liab_val, amountText(context, liabs, hidden))
        if (hidden) {
            addSeg(context, views, counts = listOf(1), colors = listOf(gray(context)))
        } else {
            addSeg(context, views, counts = listOf(assets, liabs), colors = listOf(c1(context), c5(context)))
        }
    }

    /** 2×1：净值大数字 + 右侧微型纵向堆叠条。 */


    private fun bindBadgeAndTitle(context: Context, views: RemoteViews) {
        // 徽章用族色 c2 绿（稿净资产的标识色；family 索引 0 基，c2 = 1）
        views.setInt(R.id.widget_net_badge, "setColorFilter", WidgetV22.family(context, 1).d)
    }

    private fun bindAmount(context: Context, views: RemoteViews, net: Long, hidden: Boolean) {
        views.setTextViewText(R.id.widget_net_amount, amountText(context, net, hidden))
    }

    /**
     * 涨跌胶囊：本月净收入 ÷ 期初净值，一位小数；期初 ≤ 0 或没有进出整枚隐藏。
     * 隐藏态：文字换 •••（方向也不给），胶囊底换中性灰（绿/红底会泄露「在涨」）。
     */
    private fun bindDelta(context: Context, views: RemoteViews, monthDelta: Long, opening: Long, hidden: Boolean) {
        if (monthDelta == 0L || opening <= 0L) {
            views.setViewVisibility(R.id.widget_net_delta, View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_net_delta, View.VISIBLE)
        if (hidden) {
            views.setInt(R.id.widget_net_delta, "setBackgroundResource", R.drawable.widget_v22_pill_neutral)
            views.setTextViewText(R.id.widget_net_delta, WidgetPrivacy.MASK_DELTA)
            views.setTextColor(R.id.widget_net_delta, context.getColor(R.color.widget_v22_text_3))
            return
        }
        val up = monthDelta > 0
        // 胶囊底走 setBackgroundResource（RemoteViews 不能给背景 drawable 上色，换图最稳）
        views.setInt(
            R.id.widget_net_delta,
            "setBackgroundResource",
            if (up) R.drawable.widget_delta_pill_up else R.drawable.widget_delta_pill_down
        )
        // 先按千分之一取整再除 10 得到一位小数，避免浮点误差（金额格式函数返回绝对值）；
        // 箭头本身已带方向，不再加 +/- 号（稿 §5「▲2.0%」口径）
        val permille = monthDelta * PERCENT_MILLI / opening
        views.setTextViewText(
            R.id.widget_net_delta,
            (if (up) "▲" else "▼") + String.format(
                Locale.getDefault(),
                "%.1f%%",
                kotlin.math.abs(permille) / PERCENT_DIVISOR
            )
        )
        views.setTextColor(
            R.id.widget_net_delta,
            context.getColor(if (up) R.color.widget_delta_up else R.color.widget_delta_down)
        )
    }

    /** 眼睛（只在带 id 的档存在）：图标随隐藏态换，点击发 [WidgetPrivacyReceiver]。 */
    private fun bindEye(context: Context, views: RemoteViews, appWidgetId: Int, hidden: Boolean) {
        views.setImageViewResource(
            R.id.widget_net_eye,
            if (hidden) R.drawable.widget_v22_eye_off else R.drawable.widget_v22_eye
        )
        views.setOnClickPendingIntent(
            R.id.widget_net_eye,
            WidgetPrivacy.eyePendingIntent(context, appWidgetId)
        )
    }

    private fun addSeg(context: Context, views: RemoteViews, counts: List<Long>, colors: List<Int>) {
        val seg = widgetViews(context, R.layout.widget_v22_seg)
        WidgetV22.bindSeg(context, seg, scaleCounts(counts), colors)
        views.addView(R.id.widget_net_seg_host, seg)
    }

    /**
     * 金额（分）→ 分段权重：bindSeg/slotOwners 只吃相对值,统一缩到 0..1e6 的 Int,
     * 比例无损（1e-6 精度）也不怕大额溢出。全 0 时给等权 1（画满底色）。
     */
    private fun scaleCounts(counts: List<Long>): List<Int> {
        val total = counts.sum().takeIf { it > 0 } ?: return counts.map { 1 }
        return counts.map { ((it.toDouble() / total) * 1_000_000.0).toInt().coerceAtLeast(0) }
    }

    private fun amountText(context: Context, value: Long, hidden: Boolean): String =
        if (hidden) WidgetPrivacy.MASK_AMOUNT else WidgetData.formatMoneyCompact(context, value)

    private fun chipName(context: Context, res: Int): String = context.getString(res)

    private fun c1(context: Context) = WidgetV22.family(context, 0).d

    private fun c5(context: Context) = WidgetV22.family(context, 4).d

    /** 隐藏态比例条的中性灰（c8 灰：不是分类色，也不带语义；夜间档自动切）。 */
    private fun gray(context: Context) = WidgetV22.family(context, 7).d

    private companion object {
        /** 组件声明的默认尺寸（2×1），桌面没给尺寸时兜底。 */
        const val MIN_WIDTH_DP = 110
        const val MIN_HEIGHT_DP = 40

        /** 4×4 完整排版高度；低于它（250~299dp 的压缩态）砍双比例行。 */
        const val TALL_FULL_DP = 300

        /** 百分比取一位小数：千分之一取整后除以 10。 */
        const val PERCENT_MILLI = 1000L
        const val PERCENT_DIVISOR = 10.0
    }
}

/**
 * 近 [months] 个月的月末净值序列（旧→新，末位 = [currentNet]）。
 *
 * 库里没有净值历史表，按账单月度差额倒推：月末净值(m) = 当前净值 − m 之后各月
 * （收入−支出）的累计。钱包余额只随账单进出变化（自有账户间转账净额为 0），
 * 重建值与真值一致；没有账单的月份按 0 计（净值不变）。
 *
 * 纯函数（单测直接验「逐月回填」），[today] 可注入固定日期。
 */
internal fun monthlyNetSeries(
    currentNet: Long,
    flows: List<com.palmnote.data.db.dao.MonthlyFlow>,
    today: LocalDate,
    months: Int = 12
): List<Long> {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM")
    val deltaByMonth = flows.associate { it.yearMonth to (it.income - it.expense) }
    val series = ArrayList<Long>(months)
    var suffix = 0L
    for (i in 0 until months) {
        series.add(currentNet - suffix)
        suffix += deltaByMonth[today.minusMonths(i.toLong()).format(fmt)] ?: 0L
    }
    series.reverse()
    return series
}
