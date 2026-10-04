package com.palmnote.data

import android.content.Context
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.GoalCheckInDao
import com.palmnote.data.db.dao.GoalDao
import com.palmnote.data.db.entity.Goal
import com.palmnote.data.db.entity.GoalCheckIn
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 目标 / 习惯模块的演示数据播种器（示例习惯 + 打卡历史）。
 *
 * ## 为什么演示打卡历史
 *
 * 习惯页的价值全在「连击 / 热力 / 完成率」上——只播一个空习惯，页面照样是死的。
 * 所以这里按**连续的打卡天**生成历史（含一处刻意中断，展示 streak 断掉的样子），
 * 并把 Goal 上冗余的统计列（streak / longestStreak / lastCheckInDate / totalCheckInDays）
 * 与历史**算的一致**：页面读哪个都自洽。
 *
 * 累计型目标（读书 7/12 本）一并演示，让「目标」与「习惯」两种形态都在。
 */
@Singleton
class HabitDemoSeeder @Inject constructor(
    private val goalDao: GoalDao,
    private val checkInDao: GoalCheckInDao,
    @ApplicationContext private val context: Context
) {

    companion object {
        /** 改动示例内容后必须 +1（与其它播种器同一约定）；v4 同因：清掉并发播种可能留下的重复。 */
        const val SEED_VERSION = 4
    }

    data class ClearResult(val removed: Int)

    /**
     * 示例习惯定义：`streak` 天连续（截至昨天/今天），更早处 `gapDays` 天中断，
     * 中断前再有 [historyBeforeGap] 天记录 —— 热力图上能看出"坚持过、断过、又续上"。
     */
    private data class DemoHabit(
        val title: String,
        val description: String,
        val category: String,
        val unit: String,
        val targetPerPeriod: Int,
        val color: String,
        val streak: Int,
        val gapDays: Int,
        val historyBeforeGap: Int
    )

    private val habits = listOf(
        DemoHabit("晨跑 30 分钟", "工作日早上 7 点", "FITNESS", "次", 1, "#1FA870", streak = 12, gapDays = 4, historyBeforeGap = 5),
        DemoHabit("阅读 20 页", "睡前读书", "READING", "页", 1, "#2B6FE0", streak = 21, gapDays = 3, historyBeforeGap = 9),
        DemoHabit("喝水 8 杯", "全天提醒", "HABIT", "杯", 1, "#9A4FD0", streak = 6, gapDays = 2, historyBeforeGap = 7)
    )

    suspend fun ensureSeeded(preferences: PreferencesManager): Int {
        if (!preferences.lifeDemoMode.first()) return 0
        val upToDate = preferences.habitDemoSeeded.first() &&
            preferences.habitDemoSeedVersion.first() >= SEED_VERSION
        if (upToDate && goalDao.countDemoGoals() > 0) return 0
        return reseed(preferences)
    }

    suspend fun reseed(preferences: PreferencesManager): Int {
        deleteDemoRows()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        habits.forEach { habit -> seedHabit(habit, today, zone) }

        // 累计型目标：读完 12 本书（当前 7 本），演示「目标」形态与进度读数
        goalDao.insertGoal(
            Goal(
                title = DemoTexts.localize(context, "今年读完 12 本书"),
                description = DemoTexts.localize(context, "每月一本，慢慢读"),
                category = "READING",
                goalType = "CUMULATIVE",
                totalCount = 12,
                currentCount = 7,
                unit = "本",
                startDate = today.minusDays(280).atStartOfDay(zone).toInstant().toEpochMilli(),
                deadline = LocalDate.of(today.year, 12, 31).atStartOfDay(zone).toInstant().toEpochMilli(),
                color = "#1FA870",
                notes = "《置身事内》在读",
                isDemo = true
            )
        )

        preferences.setHabitDemoSeeded(true)
        preferences.setHabitDemoSeedVersion(SEED_VERSION)
        return habits.size + 1
    }

    /** 播一个示例习惯 + 它的打卡历史（统计列与历史严格一致，页面读哪个都自洽）。 */
    private suspend fun seedHabit(habit: DemoHabit, today: LocalDate, zone: ZoneId) {
        val checkInDays = buildList {
            repeat(habit.streak) { add(today.minusDays(it.toLong())) }
            val gapStart = habit.streak + habit.gapDays
            repeat(habit.historyBeforeGap) { add(today.minusDays((gapStart + it).toLong())) }
        }.sorted()
        val goalId = goalDao.insertGoal(
            Goal(
                title = DemoTexts.localize(context, habit.title),
                description = DemoTexts.localize(context, habit.description),
                category = habit.category,
                goalType = "HABIT",
                totalCount = checkInDays.size,
                currentCount = checkInDays.size,
                unit = habit.unit,
                frequency = "DAILY",
                targetPerPeriod = habit.targetPerPeriod,
                startDate = checkInDays.first().atStartOfDay(zone).toInstant().toEpochMilli(),
                color = habit.color,
                streak = habit.streak,
                longestStreak = maxOf(habit.streak, habit.historyBeforeGap),
                lastCheckInDate = checkInDays.last().atStartOfDay(zone).toInstant().toEpochMilli(),
                totalCheckInDays = checkInDays.size,
                isDemo = true
            )
        )
        checkInDays.forEach { day ->
            checkInDao.insertCheckIn(
                GoalCheckIn(
                    goalId = goalId,
                    date = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                    count = 1,
                    mood = if (day == today) "GOOD" else "",
                    isDemo = true
                )
            )
        }
    }

    /** 关闭演示：示例习惯与打卡记录一并物理删除（打卡跟随习惯）。 */
    suspend fun clearAll(preferences: PreferencesManager): ClearResult {
        val removed = goalDao.countDemoGoals() + checkInDao.countDemoCheckIns()
        deleteDemoRows()
        preferences.setHabitDemoSeeded(false)
        return ClearResult(removed = removed)
    }

    private suspend fun deleteDemoRows() {
        checkInDao.clearDemoCheckIns()
        goalDao.clearDemoGoals()
    }
}
