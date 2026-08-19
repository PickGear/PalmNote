package com.palmnote.ui.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.palmnote.data.db.entity.GoalCheckIn
import com.palmnote.domain.repository.GoalRepository
import com.palmnote.domain.util.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// 桌面打卡入口：点击小组件圆圈直接写入打卡记录，无需打开应用
class HabitCheckInReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CheckInEntryPoint {
        fun goalRepository(): GoalRepository
        fun goalCheckInDao(): com.palmnote.data.db.dao.GoalCheckInDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK_IN) return
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L)
        if (goalId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, CheckInEntryPoint::class.java
                )
                val today = LocalDate.now()
                val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val dao = entryPoint.goalCheckInDao()
                if (dao.getTodayCheckIn(goalId, dayStart, dayEnd) == null) {
                    val repository = entryPoint.goalRepository()
                    repository.insertCheckIn(GoalCheckIn(goalId = goalId, date = dayStart))
                    repository.incrementGoalProgress(goalId)
                }
                HabitWidgetProvider.requestUpdateAll(context)
            } catch (e: Exception) {
                AppLogger.e("HabitCheckInReceiver", "Widget check-in failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK_IN = "com.palmnote.widget.action.HABIT_CHECK_IN"
        const val EXTRA_GOAL_ID = "extra_goal_id"

        fun checkInPendingIntent(context: Context, goalId: Long): PendingIntent {
            val intent = Intent(context, HabitCheckInReceiver::class.java)
                .setAction(ACTION_CHECK_IN)
                .putExtra(EXTRA_GOAL_ID, goalId)
            return PendingIntent.getBroadcast(
                context,
                (goalId % Int.MAX_VALUE).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
