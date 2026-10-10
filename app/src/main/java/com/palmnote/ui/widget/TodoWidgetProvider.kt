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

        publish(context, appWidgetManager, appWidgetIds) { appWidgetId, _ ->
            bindViews(context, appWidgetId, todos, accent)
        }
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        todos: List<LifeItem>,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_todo_unified)
        // 加号用主题色文字按钮而不是实底 pill：pill 底随主题运行时着色在 RemoteViews 下不可行，
        // 文字按钮既能跟自定义主题色，也更贴近「文本动作」的语义
        views.setInt(R.id.widget_todo_add, "setTextColor", accent.accent)
        // 今日完成度环：主角图形（RemoteViews 没有 setImageLevel 专用 API，反射调）
        views.setInt(R.id.widget_todo_ring, "setColorFilter", accent.accent)
        val done = todos.count { it.status == "COMPLETED" }
        val percent = if (todos.isEmpty()) 0 else done * 100 / todos.size
        views.setInt(R.id.widget_todo_ring, "setImageLevel", percent * 100)
        views.setTextViewText(R.id.widget_todo_ring_text, "$percent%")

        views.removeAllViews(R.id.widget_todo_list)
        // 滚动列表：数据由 TodoWidgetService 提供，显示几行交给桌面自己决定
        views.setRemoteAdapter(
            R.id.widget_todo_list,
            android.content.Intent(context, TodoWidgetService::class.java)
                .putExtra(TodoWidgetService.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        views.setEmptyView(R.id.widget_todo_list, R.id.widget_todo_empty)
        // 行内点击的条目 id 由行的 fill-in intent 带过来，模板只出 action 与组件
        views.setPendingIntentTemplate(R.id.widget_todo_list, TodoToggleReceiver.toggleTemplatePendingIntent(context))
        views.setViewVisibility(R.id.widget_todo_footer, if (todos.isEmpty()) View.GONE else View.VISIBLE)
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
