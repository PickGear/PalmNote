package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class TodoWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): LifeItemDao
        fun lifeTemplateDao(): LifeTemplateDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.preferencesManager()
        // 演示感知互斥：开＝只看示例，关＝只看自己的（与其他出口同一口径）
        val todos = WidgetData.fetchTodayTodos(
            entryPoint.lifeItemDao(),
            entryPoint.lifeTemplateDao(),
            includeDemo = prefs.lifeDemoMode.first(),
            demoMeta = com.palmnote.data.db.dao.LIFE_DEMO_META
        )
        val accent = WidgetData.readAccentTheme(context, prefs)

        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(
                appWidgetId,
                bindViews(context, appWidgetManager, appWidgetId, todos, accent)
            )
        }
    }

    internal fun bindViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        todos: List<LifeItem>,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_todo_unified)
        // 加号从实底 pill 改为主题色文字按钮（pill 底随主题运行时着色在 RemoteViews 下不可行，
        // 文字按钮与 Todoist 式文本动作一致，且自定义主题色也能生效）
        views.setInt(R.id.widget_todo_add, "setTextColor", accent.accent)

        // 按实际格子大小自适应（Apple 式缩放：同一组件，尺寸变了内容跟着变）：
        // S(2×1 ≈ <250dp) 只留最近 1 条；M 3 条；L(≥400dp) 6 条 + 页脚统计。
        val minWidth = appWidgetManager.getAppWidgetOptions(appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val previewCount = when {
            minWidth in 1..249 -> 1
            minWidth < 400 -> 3
            else -> 6
        }

        views.removeAllViews(R.id.widget_todo_list)
        todos.take(previewCount).forEach { item ->
            WidgetHelper.addTodoItemView(
                context,
                views,
                R.id.widget_todo_list,
                item,
                accent.accent,
                TodoToggleReceiver.togglePendingIntent(context, item.id)
            )
        }
        views.setViewVisibility(R.id.widget_todo_empty, if (todos.isEmpty()) View.VISIBLE else View.GONE)
        views.setViewVisibility(
            R.id.widget_todo_footer,
            if (todos.isEmpty() || previewCount == 1) View.GONE else View.VISIBLE
        )
        if (todos.isNotEmpty()) {
            val remainingCount = todos.count { it.status != "COMPLETED" }
            views.setTextViewText(
                R.id.widget_todo_footer,
                context.getString(R.string.widget_todo_footer_format, todos.size, remainingCount)
            )
        }

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_TODO + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_todo_add,
            WidgetHelper.createLifeListPendingIntent(context, WidgetDeepLink.SEG_TODO_ADD + appWidgetId, "AGENDA")
        )
        // 页脚（共 N 条 · 剩 M）→ 直达「今日安排」完整清单
        views.setOnClickPendingIntent(
            R.id.widget_todo_footer,
            WidgetHelper.createLifeListPendingIntent(context, WidgetDeepLink.SEG_TODO + 100 + appWidgetId, "AGENDA")
        )
        return views
    }
}
