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
import kotlinx.coroutines.flow.first

/**
 * 总资产 2×1 组件：各账户余额合计（与应用内钱包页同一口径）+ 一枚涨跌胶囊。
 * 点卡片进记账页看明细；尺寸变化只影响字号，不做分档。
 *
 * 胶囊口径：**本月净收入（本月收入 − 支出）的金额**，涨绿跌红。
 * 一开始按稿子做的是百分比（净收入 ÷ 期初总资产），但真机上算出来是 +175.8% —— 基数是
 * 期初总资产，收入远大于余额时这个比例毫无意义；应用也不存总资产历史，做不了真正的环比。
 * 金额是现有数据能如实表达的，本月没有进出就整枚隐藏。
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
            ) { width, _ -> bindViews(context, appWidgetId, total, width, delta) }
        }
    }

    /** 本月净收入（收入 − 支出）；账单表没有账本维度，这里就是全账本口径。 */
    private suspend fun fetchMonthDelta(entryPoint: WidgetEntryPoint): Long {
        val yearMonth = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now())
        val income = entryPoint.billDao().getMonthlyIncome(yearMonth).first() ?: 0L
        val expense = entryPoint.billDao().getMonthlyExpense(yearMonth).first() ?: 0L
        return income - expense
    }

    /** 2 格宽起字号大一号；再宽也不换布局，只放大数字。 */
    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        totalBalance: Long,
        widthDp: Int,
        monthDelta: Long = 0L
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
        bindDelta(context, views, monthDelta)

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

    /** 涨跌胶囊：本月没有进出就整枚隐藏，否则给带符号的净收入金额。 */
    private fun bindDelta(context: Context, views: RemoteViews, monthDelta: Long) {
        if (monthDelta == 0L) {
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
        // 金额格式函数返回绝对值，符号在这里补（涨 +、跌 -）
        views.setTextViewText(
            R.id.widget_net_worth_delta,
            (if (up) "+" else "-") + "¥" + WidgetData.formatAmountCompact(context, monthDelta)
        )
        views.setTextColor(
            R.id.widget_net_worth_delta,
            context.getColor(if (up) R.color.widget_delta_up else R.color.widget_delta_down)
        )
    }

    private companion object {
        /** 组件声明的默认尺寸（2×1），桌面没给尺寸时兜底。 */
        const val MIN_WIDTH_DP = 110
        const val MIN_HEIGHT_DP = 40

        /** 4 格宽起数字放大一号。 */
        const val WIDE_WIDTH_DP = 250
    }
}
