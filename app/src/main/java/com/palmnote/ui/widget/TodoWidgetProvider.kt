package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
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

class TodoWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): com.palmnote.data.db.dao.LifeItemDao
        fun lifeTemplateDao(): com.palmnote.data.db.dao.LifeTemplateDao
        fun preferencesManager(): com.palmnote.data.datastore.PreferencesManager
    }

    private var scope: CoroutineScope? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        scope?.cancel()
        scope = null
    }

    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        // onEnabled 未触发的路径（进程被杀后直接 onUpdate）没有缓存作用域：
        // 用临时作用域并在收尾取消，避免孤儿 Job 泄漏（审计 #16）
        val ownedScope = scope == null
        val coroutineScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())

        coroutineScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val prefs = entryPoint.preferencesManager()
                val demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
                // 演示感知互斥：开＝只看示例，关＝只看自己的（与其他出口同一口径）
                val includeDemo = prefs.lifeDemoMode.first()
                val todos = WidgetData.fetchTodayTodos(
                    entryPoint.lifeItemDao(),
                    entryPoint.lifeTemplateDao(),
                    includeDemo = includeDemo,
                    demoMeta = demoMeta
                )
                val totalCount = todos.size
                val remainingCount = todos.count { it.status != "COMPLETED" }
                val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

                for (appWidgetId in appWidgetIds) {
                    // 按实际格子大小自适应（Apple 式缩放：同一组件，尺寸变了内容跟着变）：
                    // S(2×1 ≈ <250dp) 只留最近 1 条；M 3 条；L(≥400dp) 6 条 + 页脚统计。
                    val minWidth = appWidgetManager.getAppWidgetOptions(appWidgetId)
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val previewCount = when {
                        minWidth in 1..249 -> 1
                        minWidth < 400 -> 3
                        else -> 6
                    }
                    val views = RemoteViews(context.packageName, R.layout.widget_todo_unified)
                    views.setInt(R.id.widget_todo_add, "setBackgroundResource", accent.pillFillRes)

                    views.removeAllViews(R.id.widget_todo_list)
                    todos.take(previewCount).forEach { item ->
                        WidgetHelper.addTodoItemView(
                            context, views, R.id.widget_todo_list, item,
                            accent.circleFillRes, TodoToggleReceiver.togglePendingIntent(context, item.id)
                        )
                    }
                    views.setViewVisibility(R.id.widget_todo_empty, if (todos.isEmpty()) View.VISIBLE else View.GONE)
                    views.setViewVisibility(
                        R.id.widget_todo_footer,
                        if (todos.isEmpty() || previewCount == 1) View.GONE else View.VISIBLE
                    )
                    if (todos.isNotEmpty()) {
                        views.setTextViewText(
                            R.id.widget_todo_footer,
                            context.getString(R.string.widget_todo_footer_format, totalCount, remainingCount)
                        )
                    }

                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, 200_000 + appWidgetId, "life")
                    )
                    views.setOnClickPendingIntent(
                        R.id.widget_todo_add,
                        WidgetHelper.createPendingIntent(context, 2_000_000 + appWidgetId, "life")
                    )
                    // 页脚（共 N 条 · 剩 M）→ 直达「今日安排」完整清单
                    views.setOnClickPendingIntent(
                        R.id.widget_todo_footer,
                        WidgetHelper.createLifeListPendingIntent(context, 200_100 + appWidgetId, "AGENDA")
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                AppLogger.e("TodoWidgetProvider", "Widget update failed", e)
            } finally {
                pendingResult.finish()
                if (ownedScope) coroutineScope.cancel()
            }
        }
    }
}
