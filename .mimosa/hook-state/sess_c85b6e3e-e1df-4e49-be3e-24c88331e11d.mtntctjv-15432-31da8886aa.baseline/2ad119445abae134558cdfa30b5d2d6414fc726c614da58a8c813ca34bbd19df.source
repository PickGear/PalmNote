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
import java.time.LocalDate
import java.time.ZoneId

class TodoWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lifeItemDao(): com.palmnote.data.db.dao.LifeItemDao
        fun lifeTemplateDao(): com.palmnote.data.db.dao.LifeTemplateDao
    }

    private var scope: CoroutineScope? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
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
                val lifeItemDao = entryPoint.lifeItemDao()
                val lifeTemplateDao = entryPoint.lifeTemplateDao()

                val templates = lifeTemplateDao.getAllVisibleTemplates().first()
                val todoTemplate = templates.firstOrNull { it.icon == "checklist" }

                val today = LocalDate.now()
                val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                var totalCount = 0
                var completedCount = 0
                var remainingCount = 0

                if (todoTemplate != null) {
                    val allTodos = lifeItemDao.getItemsByTemplate(todoTemplate.id).first()
                    val todayTodos = allTodos.filter { item ->
                        val due = item.dueDate
                        item.parentId == null && item.status != "ARCHIVED"
                            && due != null && due >= todayStart && due < todayEnd
                    }
                    totalCount = todayTodos.size
                    completedCount = todayTodos.count { it.status == "COMPLETED" }
                    remainingCount = totalCount - completedCount
                }

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_todo_unified)

                    views.setTextViewText(R.id.widget_todo_count, "$totalCount")
                    views.setTextViewText(R.id.widget_remaining_count, "$remainingCount")

                    val hasData = totalCount > 0
                    views.setViewVisibility(R.id.widget_content_with_data, if (hasData) View.VISIBLE else View.GONE)
                    views.setViewVisibility(R.id.widget_empty_state, if (hasData) View.GONE else View.VISIBLE)

                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, appWidgetId, "life")
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
