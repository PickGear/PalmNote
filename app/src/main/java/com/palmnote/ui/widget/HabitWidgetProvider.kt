package com.palmnote.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.GoalCheckInDao
import com.palmnote.data.db.dao.GoalDao
import com.palmnote.data.db.entity.Goal
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class HabitWidgetProvider : ScopedWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun goalDao(): GoalDao
        fun goalCheckInDao(): GoalCheckInDao
        fun preferencesManager(): PreferencesManager
    }

    override suspend fun onUpdateAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val habits = entryPoint.goalDao().getHabitGoals().first()
        val checkedIds = fetchTodayCheckedIds(entryPoint.goalCheckInDao())
        val accent = WidgetData.readAccentTheme(context, entryPoint.preferencesManager())

        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(
                appWidgetId,
                bindViews(context, appWidgetId, habits, checkedIds, accent)
            )
        }
    }

    private suspend fun fetchTodayCheckedIds(dao: GoalCheckInDao): Set<Long> {
        val today = LocalDate.now()
        val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dao.getTodayCheckedGoalIds(dayStart, dayEnd).first().toSet()
    }

    internal fun bindViews(
        context: Context,
        appWidgetId: Int,
        habits: List<Goal>,
        checkedIds: Set<Long>,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_habit_unified)
        views.removeAllViews(R.id.widget_habit_list)
        habits.take(4).forEach { habit ->
            views.addView(R.id.widget_habit_list, habitRow(context, habit, habit.id in checkedIds, accent))
        }

        val checkedCount = habits.count { it.id in checkedIds }
        views.setTextViewText(R.id.widget_habit_count, "$checkedCount/${habits.size}")
        views.setTextColor(R.id.widget_habit_count, accent.accent)
        views.setViewVisibility(R.id.widget_habit_count, if (habits.isEmpty()) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_habit_empty, if (habits.isEmpty()) View.VISIBLE else View.GONE)

        views.setOnClickPendingIntent(
            R.id.widget_layout,
            WidgetHelper.createPendingIntent(context, WidgetDeepLink.SEG_HABIT + appWidgetId, WidgetDeepLink.TAB_LIFE)
        )
        return views
    }

    private fun habitRow(
        context: Context,
        habit: Goal,
        checked: Boolean,
        accent: WidgetData.AccentTheme
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_habit_item)
        // 圆圈三层结构（灰环/可着色圆底/对勾）：主题色经 setColorFilter 运行时着色，全主题生效
        row.setViewVisibility(R.id.widget_habit_check_ring, if (checked) View.GONE else View.VISIBLE)
        row.setViewVisibility(R.id.widget_habit_check_fill, if (checked) View.VISIBLE else View.GONE)
        if (checked) {
            row.setInt(R.id.widget_habit_check_fill, "setColorFilter", accent.accent)
        }
        row.setViewVisibility(R.id.widget_habit_check, if (checked) View.VISIBLE else View.GONE)
        row.setTextViewText(R.id.widget_habit_check, if (checked) "✓" else "")
        row.setTextViewText(R.id.widget_habit_name, habit.title)
        val nameColor = if (checked) {
            context.getColor(R.color.widget_v2_text_tertiary)
        } else {
            context.getColor(R.color.widget_v2_text_primary)
        }
        row.setTextColor(R.id.widget_habit_name, nameColor)
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
        return row
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
