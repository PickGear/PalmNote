package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.palmnote.domain.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// 走数据库查询的组件共用的协程样板：goAsync 生命周期绑定 + 缓存作用域，
// 子类只实现 onUpdateAsync（取数 + 构建 RemoteViews），异常统一按类名记日志。
// 纯静态无查询的组件（Shortcuts）不必继承本类。
abstract class ScopedWidgetProvider : AppWidgetProvider() {

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
