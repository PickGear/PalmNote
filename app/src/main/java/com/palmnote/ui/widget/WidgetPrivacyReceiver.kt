package com.palmnote.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.palmnote.domain.util.AppLogger

/**
 * 金额隐私开关的点击接收器：切换 [WidgetPrivacy] 全局态并刷新含金额的组件。
 *
 * 薄壳：动作本身在 [WidgetPrivacy.toggle]，这里只接广播、刷新、收尾（与 TodoToggleReceiver 同风格）。
 * 目前只有净资产组件带眼睛；其余金额组件（记账/预算/订阅）要铺开时在下面补对应族的刷新即可。
 */
class WidgetPrivacyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val pendingResult = goAsync()
        try {
            WidgetPrivacy.toggle(context)
            WidgetUpdateHelper.refreshNetWorthWidgets()
        } catch (e: Exception) {
            AppLogger.e("WidgetPrivacyReceiver", "Widget privacy toggle failed", e)
        } finally {
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.palmnote.widget.action.AMOUNT_PRIVACY_TOGGLE"

        /** 保留字段：点的眼睛来自哪个实例（目前不区分，全局切换；留着便于日后按实例隔离）。 */
        const val EXTRA_WIDGET_ID = "extra_widget_id"
    }
}
