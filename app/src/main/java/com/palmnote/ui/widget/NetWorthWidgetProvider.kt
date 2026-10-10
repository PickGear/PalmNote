package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.palmnote.app.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * 总资产 2×1 组件：只显示一个数字 —— 各账户余额合计（与应用内钱包页同一口径）。
 * 点卡片进记账页看明细；尺寸变化只影响字号，不做分档。
 */
class NetWorthWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun walletDao(): com.palmnote.data.db.dao.WalletDao
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        // 余额合计口径：与钱包页 getTotalBalance() 同源；没人建过账户时是 null，按 0 显示
        val total = entryPoint.walletDao().getTotalBalance().first() ?: 0L

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            sizedRemoteViews(
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                MIN_WIDTH_DP,
                MIN_HEIGHT_DP
            ) { width, _ -> bindViews(context, appWidgetId, total, width) }
        }
    }

    /** 2 格宽起字号大一号；再宽也不换布局，只放大数字。 */
    internal fun bindViews(context: Context, appWidgetId: Int, totalBalance: Long, widthDp: Int): RemoteViews {
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

    private companion object {
        /** 组件声明的默认尺寸（2×1），桌面没给尺寸时兜底。 */
        const val MIN_WIDTH_DP = 110
        const val MIN_HEIGHT_DP = 40

        /** 4 格宽起数字放大一号。 */
        const val WIDE_WIDTH_DP = 250
    }
}
