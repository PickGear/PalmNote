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
        val coroutineScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())

        coroutineScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val todos = WidgetData.fetchTodayTodos(entryPoint.lifeItemDao(), entryPoint.lifeTemplateDao())
                val totalCount = todos.size
                val remainingCount = todos.count { it.status != "COMPLETED" }
                val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_todo_unified)
                    views.setInt(R.id.widget_todo_add, "setBackgroundResource", accent.pillFillRes)

                    views.removeAllViews(R.id.widget_todo_list)
                    todos.take(3).forEach { item ->
                        WidgetHelper.addTodoItemView(
                            context, views, R.id.widget_todo_list, item,
                            accent.circleFillRes, TodoToggleReceiver.togglePendingIntent(context, item.id)
                        )
                    }
                    views.setViewVisibility(R.id.widget_todo_empty, if (todos.isEmpty()) View.VISIBLE else View.GONE)
                    views.setViewVisibility(R.id.widget_todo_footer, if (todos.isEmpty()) View.GONE else View.VISIBLE)
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
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                AppLogger.e("TodoWidgetProvider", "Widget update failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
