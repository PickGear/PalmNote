package com.palmnote.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R

/**
 * 金额隐私开关（桌面防窥）：全局一个状态，点任意一个眼睛图标切换，**带眼睛的组件**一起收/放。
 *
 * 目前只有净资产组件带眼睛（[WidgetPrivacyReceiver] 也只刷新净资产族）；记账/订阅/概览等
 * 金额族尚未铺开。这里记录的是全局态，铺开时各 provider 读 [isHidden] 即可，不用改存储口径。
 *
 * 隐藏三样（只藏数字是表面功夫，2026-10-23 稿内实测）：
 * 金额换圆点、比例条整条中性灰、涨幅去掉 ▲ 方向 —— 留着条的比例和涨跌颜色都能反推出财务状况。
 *
 * 状态存 SharedPreferences（widget_privacy）：接收器与 provider 都在无 DI 的进程里跑，
 * 不走 PreferencesManager 以免把 Hilt 带进广播链。防的是「余光扫到」，不防刻意点开。
 */
internal object WidgetPrivacy {

    private const val PREFS = "widget_privacy"
    private const val KEY_HIDDEN = "amount_hidden"

    /** 金额被隐藏时返回 true（默认显示）。 */
    fun isHidden(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDDEN, false)

    /** 切换隐藏态，返回新的状态。 */
    fun toggle(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = !prefs.getBoolean(KEY_HIDDEN, false)
        prefs.edit().putBoolean(KEY_HIDDEN, next).apply()
        return next
    }

    /** 眼睛按钮的点击意图：发给 [WidgetPrivacyReceiver]，谁点都切换全局态。 */
    fun eyePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetPrivacyReceiver::class.java)
            .setAction(WidgetPrivacyReceiver.ACTION_TOGGLE)
            .putExtra(WidgetPrivacyReceiver.EXTRA_WIDGET_ID, appWidgetId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, WidgetDeepLink.SEG_NET_WORTH + appWidgetId, intent, flags)
    }

    /** 隐藏态的金额占位（稿 ¥•••••）与涨幅占位（•••，方向也不给）。 */
    const val MASK_AMOUNT = "¥•••••"
    const val MASK_DELTA = "•••"
}
