package com.palmnote.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.palmnote.domain.util.AppLogger

// 跨天刷新：午夜刷新日期敏感的组件（今日待办/打卡/倒数/记账/概览），
// 避免"今日"数据在用户不打开应用的情况下停留到第二天
class WidgetMidnightReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MIDNIGHT_REFRESH) return
        try {
            WidgetUpdateHelper.refreshAllWidgets()
            WidgetUpdateHelper.refreshDashboardWidgets()
        } catch (e: Exception) {
            AppLogger.e("WidgetMidnightReceiver", "Midnight widget refresh failed", e)
        } finally {
            // 滚动到下一个午夜
            WidgetUpdateHelper.scheduleMidnightRefresh(context)
        }
    }

    companion object {
        const val ACTION_MIDNIGHT_REFRESH = "com.palmnote.widget.action.MIDNIGHT_REFRESH"
    }
}
