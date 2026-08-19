package com.palmnote.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.appwidget.AppWidgetManager

/**
 * Widget 刷新工具：在数据变更时主动触发 Widget 更新，
 * 而不是等待系统 30 分钟轮询。
 */
object WidgetUpdateHelper {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
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

    fun refreshAllWidgets() {
        refreshBillWidgets()
        refreshTodoWidgets()
        refreshCounterWidgets()
        refreshAssetWidgets()
        refreshVaultWidgets()
    }

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
}
