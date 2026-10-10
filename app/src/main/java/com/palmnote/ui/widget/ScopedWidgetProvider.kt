package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.util.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 走数据库查询的组件共用的协程样板：goAsync 生命周期绑定 + 缓存作用域，
// 子类只实现 onUpdateAsync（取数 + 构建 RemoteViews），异常统一按类名记日志。
// 纯静态无查询的组件（Shortcuts）不必继承本类。
abstract class ScopedWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PreferencesEntryPoint {
        fun preferencesManager(): PreferencesManager
    }

    private var cachedScope: CoroutineScope? = null

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        cachedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cachedScope?.cancel()
        cachedScope = null
    }

    /**
     * 组件从桌面移除时清掉它的**按实例**配置（账本选择、透明度覆盖）。
     * 桌面会复用 appWidgetId，不清的话新摆的组件会继承上一个实例的设置。
     */
    final override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val preferences = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PreferencesEntryPoint::class.java
        ).preferencesManager()
        val scope = cachedScope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            appWidgetIds.forEach {
                preferences.setWidgetBook(it, null)
                preferences.setWidgetOpacityFor(it, null)
            }
            if (cachedScope == null) scope.cancel()
        }
    }

    final override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        launchUpdate { onUpdateAsync(context, appWidgetManager, appWidgetIds) }
    }

    final override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        launchUpdate { onUpdateAsync(context, appWidgetManager, intArrayOf(appWidgetId)) }
    }

    protected abstract suspend fun onUpdateAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    )

    /**
     * 统一发布出口：按**每个实例**取透明度（有覆盖用覆盖，没有跟随全局默认），
     * 套用到卡片底色后再交给桌面。各组件布局根 id 统一是 widget_layout，底色一换整张卡就变透。
     * 内部还有大块实底的组件（概览的预算/目标卡）用回调里的 opacity 自行套用 setImageAlpha。
     * 偏好读取走 prefsFlow（内部已 catch 兜底），坏值不会让组件停更。
     */
    protected suspend fun publish(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        bind: suspend (appWidgetId: Int, opacity: Float) -> RemoteViews
    ) {
        val preferences = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PreferencesEntryPoint::class.java
        ).preferencesManager()
        appWidgetIds.forEach { appWidgetId ->
            val opacity = preferences.widgetOpacityFor(appWidgetId).first()
            val views = bind(appWidgetId, opacity)
            WidgetHelper.applyWidgetOpacity(views, opacity)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun launchUpdate(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        // onEnabled 未触发的路径（进程被杀后直接 onUpdate）没有缓存作用域：
        // 用临时作用域并在收尾取消，避免孤儿 Job 泄漏（审计 #16）
        val ownedScope = cachedScope == null
        val scope = cachedScope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                AppLogger.e(this@ScopedWidgetProvider::class.java.simpleName, "Widget update failed", e)
            } finally {
                pendingResult.finish()
                if (ownedScope) scope.cancel()
            }
        }
    }
}
