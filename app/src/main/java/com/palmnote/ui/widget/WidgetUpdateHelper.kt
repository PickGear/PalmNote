package com.palmnote.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.palmnote.domain.util.AppLogger

/**
 * Widget 刷新工具：在数据变更时主动触发 Widget 更新，
 * 而不是等待系统 30 分钟轮询。
 */
object WidgetUpdateHelper {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        scheduleMidnightRefresh(appContext)
    }

    // 每日午夜刷新日期敏感的组件（今日待办/打卡/倒数），由 WidgetMidnightReceiver 滚动重排
    fun scheduleMidnightRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = java.util.Calendar.getInstance()
        val next = (now.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            MIDNIGHT_REQUEST_CODE,
            Intent(context, WidgetMidnightReceiver::class.java)
                .setAction(WidgetMidnightReceiver.ACTION_MIDNIGHT_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            // 非精确闹钟：不需要用户授权，允许系统在维护窗口内合并触发
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pendingIntent)
        } catch (e: Exception) {
            AppLogger.e("WidgetUpdateHelper", "Schedule midnight refresh failed", e)
        }
    }

    fun refreshBillWidgets() {
        val manager = AppWidgetManager.getInstance(appContext)
        val billIds = manager.getAppWidgetIds(ComponentName(appContext, BillWidgetProvider::class.java))
        val quickIds = manager.getAppWidgetIds(ComponentName(appContext, QuickBillWidgetProvider::class.java))
        val dashIds = manager.getAppWidgetIds(ComponentName(appContext, DashboardWidgetProvider::class.java))
        sendUpdate(BillWidgetProvider::class.java, billIds + quickIds + dashIds)
    }

    fun refreshTodoWidgets() {
        val manager = AppWidgetManager.getInstance(appContext)
        val todoIds = manager.getAppWidgetIds(ComponentName(appContext, TodoWidgetProvider::class.java))
        val dashIds = manager.getAppWidgetIds(ComponentName(appContext, DashboardWidgetProvider::class.java))
        sendUpdate(TodoWidgetProvider::class.java, todoIds + dashIds)
    }

    fun refreshCounterWidgets() {
        val manager = AppWidgetManager.getInstance(appContext)
        val counterIds = manager.getAppWidgetIds(ComponentName(appContext, LifeCounterWidgetProvider::class.java))
        val dashIds = manager.getAppWidgetIds(ComponentName(appContext, DashboardWidgetProvider::class.java))
        sendUpdate(LifeCounterWidgetProvider::class.java, counterIds + dashIds)
    }

    fun refreshAssetWidgets() {
        val manager = AppWidgetManager.getInstance(appContext)
        val assetIds = manager.getAppWidgetIds(ComponentName(appContext, AssetWidgetProvider::class.java))
        val dashIds = manager.getAppWidgetIds(ComponentName(appContext, DashboardWidgetProvider::class.java))
        sendUpdate(AssetWidgetProvider::class.java, assetIds + dashIds)
    }

    fun refreshVaultWidgets() {
        val manager = AppWidgetManager.getInstance(appContext)
        val vaultIds = manager.getAppWidgetIds(ComponentName(appContext, VaultWidgetProvider::class.java))
        val dashIds = manager.getAppWidgetIds(ComponentName(appContext, DashboardWidgetProvider::class.java))
        sendUpdate(VaultWidgetProvider::class.java, vaultIds + dashIds)
    }

    fun refreshDashboardWidgets() {
        val manager = AppWidgetManager.getInstance(appContext)
        val dashIds = manager.getAppWidgetIds(ComponentName(appContext, DashboardWidgetProvider::class.java))
        sendUpdate(DashboardWidgetProvider::class.java, dashIds)
    }

    fun refreshHabitWidgets() {
        HabitWidgetProvider.requestUpdateAll(appContext)
    }

    fun refreshAllWidgets() {
        refreshBillWidgets()
        refreshTodoWidgets()
        refreshCounterWidgets()
        refreshAssetWidgets()
        refreshVaultWidgets()
        refreshHabitWidgets()
    }

    // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND 是隐藏 API（0x01000000），只能以字面量使用
    @Suppress("WrongConstant")
    private fun sendUpdate(providerClass: Class<*>, ids: IntArray) {
        if (ids.isEmpty()) return
        val intent = Intent(appContext, providerClass).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.addFlags(0x01000000) // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND
        }
        appContext.sendBroadcast(intent)
    }

    private const val MIDNIGHT_REQUEST_CODE = 424_242
}
