package com.palmnote.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.palmnote.domain.util.AppLogger

// 开机重排：重启后系统 AlarmManager 定时全部失效，重新排定午夜刷新
class WidgetBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            // refreshAllWidgets 末尾已经刷过概览，不再单独再发一次概览广播
            WidgetUpdateHelper.refreshAllWidgets()
            WidgetUpdateHelper.scheduleMidnightRefresh(context)
        } catch (e: Exception) {
            AppLogger.e("WidgetBootReceiver", "Boot widget refresh failed", e)
        }
    }
}
