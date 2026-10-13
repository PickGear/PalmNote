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
 * 总资产 2×1 组件：各账户余额合计（与应用内钱包页同一口径）+ 涨跌胶囊 + 本月净收入。
 * 点卡片进记账页看明细；尺寸变化只影响字号，不做分档。
 *
 * 三段口径（全部来自已有账单数据，无额外字段）：
 * - Hero 数字：各账户余额合计（钱包页 getTotalBalance() 同源）；
 * - 胶囊：本月净收入 ÷ 期初总资产，一位小数百分比，涨绿跌红。期初 = 当前合计 − 本月净收入；
 *   期初 ≤ 0（新账户第一个月没有基数）算不出有意义比率，整枚隐藏；
 * - 底行：同一笔本月净收入的**金额**（设计稿「本月 +¥680」），本月没有进出时给中性的「本月 ¥0」。
 */
class NetWorthWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun walletDao(): com.palmnote.data.db.dao.WalletDao
        fun billDao(): com.palmnote.data.db.dao.BillDao
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        // 余额合计口径：与钱包页 getTotalBalance() 同源；没人建过账户时是 null，按 0 显示
        val total = entryPoint.walletDao().getTotalBalance().first() ?: 0L
        val delta = fetchMonthDelta(entryPoint)

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                MIN_WIDTH_DP,
                MIN_HEIGHT_DP
            ) { width, height -> bindViews(context, appWidgetId, total, width, delta, height) }
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
     * 2 格宽起字号大一号；再宽也不换布局，只放大数字。
     * [heightDp] 是桌面给的实际高度：矮到放不下第三行时收起「本月 ±¥X」
     * （设计稿三段式只在 2×1 的完整高度下成立，缩到最小格还硬塞会截断）。
     */
    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        totalBalance: Long,
        widthDp: Int,
        monthDelta: Long = 0L,
        heightDp: Int = MIN_HEIGHT_DP
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_net_worth_unified)
        views.setTextViewText(
            R.id.widget_net_worth_amount,
            WidgetData.formatMoneyCompact(context, totalBalance)
        )
        val wide = widthDp >= WIDE_WIDTH_DP
        views.setTextViewTextSize(
            R.id.widget_net_worth_amount,
            android.util.TypedValue.COMPLEX_UNIT_SP,
            if (wide) 26f else 22f
        )
        // 期初总资产 = 当前合计 − 本月净收入：胶囊吃这个基数算比率
        bindDelta(context, views, monthDelta, totalBalance - monthDelta)
        bindMonthSub(context, views, monthDelta)
        views.setViewVisibility(
            R.id.widget_net_worth_sub,
            if (heightDp < SUB_MIN_HEIGHT_DP) View.GONE else View.VISIBLE
        )

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(
                context,
                WidgetDeepLink.SEG_NET_WORTH + appWidgetId,
                WidgetDeepLink.TAB_BILL
            )
        )
        return views
    }

    /** 涨跌胶囊：本月净收入 ÷ 期初总资产，一位小数百分比；期初 ≤ 0 或没有进出就整枚隐藏。 */
    private fun bindDelta(context: Context, views: RemoteViews, monthDelta: Long, opening: Long) {
        if (monthDelta == 0L || opening <= 0L) {
            views.setViewVisibility(R.id.widget_net_worth_delta, View.GONE)
            return
        }
        val up = monthDelta > 0
        views.setViewVisibility(R.id.widget_net_worth_delta, View.VISIBLE)
        // 胶囊底走 setBackgroundResource（RemoteViews 不能给背景 drawable 上色，换图最稳）
        views.setInt(
            R.id.widget_net_worth_delta,
            "setBackgroundResource",
            if (up) R.drawable.widget_delta_pill_up else R.drawable.widget_delta_pill_down
        )
        // 先按千分之一取整再除 10 得到一位小数，避免浮点误差（金额格式函数返回绝对值）
        val permille = monthDelta * PERCENT_MILLI / opening
        views.setTextViewText(
            R.id.widget_net_worth_delta,
            (if (up) "+" else "-") + String.format(
                Locale.getDefault(),
                "%.1f%%",
                kotlin.math.abs(permille) / PERCENT_DIVISOR
            )
        )
        views.setTextColor(
            R.id.widget_net_worth_delta,
            context.getColor(if (up) R.color.widget_delta_up else R.color.widget_delta_down)
        )
    }

    /** 第三行：本月净收入金额（设计稿「本月 +¥680」），涨跌同色；没有进出给中性色的「本月 ¥0」。 */
    private fun bindMonthSub(context: Context, views: RemoteViews, monthDelta: Long) {
        val sign = when {
            monthDelta > 0 -> "+"
            monthDelta < 0 -> "-"
            else -> ""
        }
        views.setTextViewText(
            R.id.widget_net_worth_sub,
            context.getString(
                R.string.widget_net_worth_month_sub,
                "$sign¥" + WidgetData.formatAmountCompact(context, monthDelta)
            )
        )
        val colorRes = when {
            monthDelta > 0 -> R.color.widget_delta_up
            monthDelta < 0 -> R.color.widget_delta_down
            else -> R.color.widget_v2_text_secondary
        }
        views.setTextColor(R.id.widget_net_worth_sub, context.getColor(colorRes))
    }

    private companion object {
        /** 组件声明的默认尺寸（2×1），桌面没给尺寸时兜底。 */
        const val MIN_WIDTH_DP = 110
        const val MIN_HEIGHT_DP = 40

        /** 4 格宽起数字放大一号。 */
        const val WIDE_WIDTH_DP = 250

        /** 矮于这个高度放不下三段，收起第三行。 */
        const val SUB_MIN_HEIGHT_DP = 60

        /** 百分比取一位小数：千分之一取整后除以 10。 */
        const val PERCENT_MILLI = 1000L
        const val PERCENT_DIVISOR = 10.0
    }
}
