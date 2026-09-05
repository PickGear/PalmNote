package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
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

class HabitWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun goalDao(): com.palmnote.data.db.dao.GoalDao
        fun goalCheckInDao(): com.palmnote.data.db.dao.GoalCheckInDao
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
                val habits = entryPoint.goalDao().getHabitGoals().first()
                val today = LocalDate.now()
                val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val checkedIds = entryPoint.goalCheckInDao().getTodayCheckedGoalIds(dayStart, dayEnd).first().toSet()
                val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_habit_unified)

                    views.removeAllViews(R.id.widget_habit_list)
                    val shown = habits.take(4)
                    shown.forEach { habit ->
                        val checked = habit.id in checkedIds
                        val row = RemoteViews(context.packageName, R.layout.widget_habit_item)
                        row.setInt(
                            R.id.widget_habit_check, "setBackgroundResource",
                            if (checked) accent.circleFillRes else R.drawable.widget_ring_gray
                        )
                        row.setTextViewText(R.id.widget_habit_check, if (checked) "✓" else "")
                        row.setTextViewText(R.id.widget_habit_name, habit.title)
                        row.setTextColor(
                            R.id.widget_habit_name,
                            if (checked) context.getColor(R.color.widget_v2_text_tertiary) else context.getColor(R.color.widget_v2_text_primary)
                        )
                        row.setTextViewText(
                            R.id.widget_habit_streak,
                            if (habit.streak > 0) context.getString(R.string.widget_streak_format, habit.streak) else ""
                        )
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            val state = if (checked) context.getString(R.string.widget_completed) else ""
                            row.setContentDescription(R.id.widget_habit_row, "${habit.title} $state".trim())
                        }
                        // 整行可点：18dp 圆圈太小，全行作为打卡热区
                        row.setOnClickPendingIntent(
                            R.id.widget_habit_row,
                            HabitCheckInReceiver.checkInPendingIntent(context, habit.id)
                        )
                        row.setOnClickPendingIntent(
                            R.id.widget_habit_check,
                            HabitCheckInReceiver.checkInPendingIntent(context, habit.id)
                        )
                        views.addView(R.id.widget_habit_list, row)
                    }

                    val checkedCount = habits.count { it.id in checkedIds }
                    views.setTextViewText(R.id.widget_habit_count, "$checkedCount/${habits.size}")
                    views.setTextColor(R.id.widget_habit_count, accent.accentText)
                    views.setViewVisibility(R.id.widget_habit_count, if (habits.isEmpty()) View.GONE else View.VISIBLE)
                    views.setViewVisibility(R.id.widget_habit_empty, if (habits.isEmpty()) View.VISIBLE else View.GONE)

                    views.setOnClickPendingIntent(
                        R.id.widget_layout,
                        WidgetHelper.createPendingIntent(context, 800_000 + appWidgetId, "life")
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                AppLogger.e("HabitWidgetProvider", "Widget update failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun requestUpdateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HabitWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                android.content.Intent(context, HabitWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
