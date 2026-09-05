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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class LifeCounterWidgetProvider : AppWidgetProvider() {

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
                val counterTemplates = templates.filter { it.icon == "timer" }

                data class CounterEvent(val name: String, val daysLeft: Long, val targetDate: LocalDate)
                val events = mutableListOf<CounterEvent>()

                for (tpl in counterTemplates) {
                    val items = lifeItemDao.getItemsByTemplate(tpl.id).first()
                    for (item in items) {
                        val dueDate = item.dueDate
                        if (dueDate != null) {
                            val targetDate = java.time.Instant.ofEpochMilli(dueDate)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), targetDate)
                            if (daysLeft >= 0) {
                                events.add(CounterEvent(item.title, daysLeft, targetDate))
                            }
                        }
                    }
                }

                val sortedEvents = events.sortedBy { it.daysLeft }
                val firstEvent = sortedEvents.firstOrNull()

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_counter_unified)

                    views.setTextViewText(R.id.widget_counter_count, "${sortedEvents.size}")

                    if (firstEvent != null) {
                        views.setTextViewText(R.id.widget_event_name, firstEvent.name)
                        views.setTextViewText(R.id.widget_days_count, "${firstEvent.daysLeft}")
                        val datePattern = context.getString(R.string.widget_date_format_cn)
                        views.setTextViewText(R.id.widget_event_date,
                            firstEvent.targetDate.format(DateTimeFormatter.ofPattern(datePattern)))
                        views.setViewVisibility(R.id.widget_first_event, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_empty_state, View.GONE)
                    } else {
                        views.setViewVisibility(R.id.widget_first_event, View.GONE)
                        views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
                    }

                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, 300_000 + appWidgetId, "life")
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                AppLogger.e("LifeCounterWidgetProvider", "Widget update failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
