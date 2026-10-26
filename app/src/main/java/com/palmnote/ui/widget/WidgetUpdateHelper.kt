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

    /**
     * 恢复备份期间抑制一切 Widget 更新广播：Widget 的 onUpdate/onReceive 会读数据库，
     * 在备份文件被替换的窗口里打开数据库会把恢复写坏。恢复完成后进程重启，
     * 标志随进程消亡，无需复位。
     */
    @Volatile
    var restoring: Boolean = false
        private set

    fun setRestoring(value: Boolean) { restoring = value }

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

    // 各 provider 只能收到自己的 widgetId：框架不校验广播里的 id 属于哪个 provider，
    // 把 Dashboard 的 id 拼进别的 provider 的更新广播会把 Dashboard 界面覆盖成账单/待办布局

    /**
     * 一族 = 目录条目里以 [base] 为基类的全部类（基类本尊 + 同族的尺寸薄子类，
     * 如 TodoWidgetProvider / TodoMini / TodoWide）。从 WidgetPin.entries 派生，
     * 新增尺寸形态时刷新链路自动纳入，不再逐类硬编码。
     */
    private fun familyOf(base: Class<*>): List<Class<*>> =
        WidgetPin.entries.map { it.providerClass }.filter { base.isAssignableFrom(it) }

    /** 给一族的每个类各发一条只含自己实例 id 的更新广播（见上方 id 归属注释）。 */
    private fun sendFamily(base: Class<*>) {
        if (!::appContext.isInitialized) return  // 单测环境（Application.onCreate 未跑）静默跳过
        val manager = AppWidgetManager.getInstance(appContext)
        familyOf(base).forEach { provider ->
            sendUpdate(provider, manager.getAppWidgetIds(ComponentName(appContext, provider)))
        }
    }

    fun refreshBillWidgets() {
        sendFamily(BillWidgetProvider::class.java)
        refreshDashboardWidgets()
    }

    fun refreshTodoWidgets() {
        sendFamily(TodoWidgetProvider::class.java)
        refreshDashboardWidgets()
    }

    fun refreshCounterWidgets() {
        sendFamily(LifeCounterWidgetProvider::class.java)
        refreshDashboardWidgets()
    }

    fun refreshAssetWidgets() {
        sendFamily(AssetWidgetProvider::class.java)
        refreshDashboardWidgets()
    }

    /** 净资产（含隐私眼睛切换后的刷新）。 */
    fun refreshNetWorthWidgets() {
        sendFamily(NetWorthWidgetProvider::class.java)
    }

    fun refreshNextUpWidgets() {
        sendFamily(NextUpWidgetProvider::class.java)
    }

    fun refreshDashboardWidgets() {
        sendFamily(DashboardWidgetProvider::class.java)
    }

    /** 订阅提醒（生活条目 kind=SUBSCRIPTION 变化后跟刷；与待办同属生活数据）。 */
    fun refreshSubscriptionWidgets() {
        sendFamily(SubscriptionWidgetProvider::class.java)
    }

    fun refreshHabitWidgets() {
        sendFamily(HabitWidgetProvider::class.java)
    }

    fun refreshAllWidgets() {
        if (!::appContext.isInitialized) return  // 单测环境静默跳过

        // 直接遍历目录条目（40 类 = 10 基类 + 30 尺寸形态薄子类），每类只发自己实例的 id。
        // 此前逐类硬编码时漏过 NetWorth/Subscription/NextUp/Shortcuts，
        // 改按目录派生后「全量」与目录天然一致，新增组件不会再漏。
        val manager = AppWidgetManager.getInstance(appContext)
        WidgetPin.entries.map { it.providerClass }.distinct().forEach { provider ->
            sendUpdate(provider, manager.getAppWidgetIds(ComponentName(appContext, provider)))
        }
    }

    // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND 是隐藏 API（0x01000000），只能以字面量使用
    @Suppress("WrongConstant")
    private fun sendUpdate(providerClass: Class<*>, ids: IntArray) {
        // 单测环境（Application.onCreate 未跑）appContext 未初始化：静默跳过，别把测试炸了
        if (!::appContext.isInitialized) return
        if (restoring) return  // 恢复窗口内静默丢弃：见 restoring 注释
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
