package com.palmnote.data.repository
import javax.inject.Inject

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.GoalDao
import com.palmnote.data.db.entity.Goal
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.GoalRepository
class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao,
    private val checkInDao: com.palmnote.data.db.dao.GoalCheckInDao,
    private val appDatabase: AppDatabase
) : GoalRepository {
    override fun getAllGoals(): Flow<List<Goal>> = goalDao.getAllGoals()

    override fun getRecentGoals(): Flow<List<Goal>> = goalDao.getRecentGoals()

    override suspend fun getGoalById(id: Long): Goal? = goalDao.getGoalById(id)

    override fun getGoalsByCategory(category: String): Flow<List<Goal>> = goalDao.getGoalsByCategory(category)

    override fun getGoalCount(): Flow<Int> = goalDao.getGoalCount()

    override fun getCompletedGoalCount(): Flow<Int> = goalDao.getCompletedGoalCount()

    override fun getNonHabitGoalCount(): Flow<Int> = goalDao.getNonHabitGoalCount()

    override fun getCompletedNonHabitGoalCount(): Flow<Int> = goalDao.getCompletedNonHabitGoalCount()

    override fun getTodayCheckedGoalIds(dayStart: Long, dayEnd: Long): Flow<List<Long>> =
        checkInDao.getTodayCheckedGoalIds(dayStart, dayEnd)

    override fun getOverdueGoals(now: Long): Flow<List<Goal>> = goalDao.getOverdueGoals(now)

    override fun getHabitGoals(): Flow<List<Goal>> = goalDao.getHabitGoals()

    override fun getTotalStreak(): Flow<Int?> = goalDao.getTotalStreak()


    override suspend fun insertGoal(goal: Goal): Long = goalDao.insertGoal(goal)

    override suspend fun updateGoal(goal: Goal) = goalDao.updateGoal(goal)

    // 同事务清除打卡记录，避免 goal_check_ins 留下孤儿行
    override suspend fun deleteGoal(id: Long) = appDatabase.withTransaction {
        checkInDao.deleteAllByGoalId(id)
        goalDao.deleteGoal(id)
    }


    override suspend fun incrementGoalProgress(id: Long) = goalDao.incrementGoalProgress(id)

    override suspend fun updateStreak(id: Long, streak: Int, checkInDate: Long, now: Long) =
        goalDao.updateStreak(id, streak, checkInDate, now)

    override suspend fun search(query: String): List<Goal> = goalDao.search(query)

    override suspend fun setGoalProgress(id: Long, count: Int) = goalDao.setGoalProgress(id, count)

    override suspend fun getHabitGoalsNeedingReset(): List<Goal> = goalDao.getHabitGoalsNeedingReset()

    override suspend fun batchResetPeriod(ids: List<Long>, periodStart: Long, periodEnd: Long) =
        goalDao.batchResetPeriod(ids, periodStart, periodEnd)

    override fun getCheckInsByGoal(goalId: Long): Flow<List<com.palmnote.data.db.entity.GoalCheckIn>> =
        checkInDao.getCheckInsByGoal(goalId)

    // 打卡写入与 streak 回写同事务：Widget/仪表盘/习惯页三个入口共用本方法，
    // streak/totalCheckInDays 在任意入口打卡后都保持正确。
    // 同事务判重：三个入口各自的守卫都是 check-then-act，并发打卡仍可能双插双计数。
    // 重复时返回 -1，调用方据此跳过进度累计。
    override suspend fun insertCheckIn(checkIn: com.palmnote.data.db.entity.GoalCheckIn): Long =
        appDatabase.withTransaction {
            val dayEnd = checkIn.date + java.time.Duration.ofDays(1).toMillis()
            if (checkInDao.getTodayCheckIn(checkIn.goalId, checkIn.date, dayEnd) != null) {
                return@withTransaction -1L
            }
            val id = checkInDao.insertCheckIn(checkIn)
            goalDao.updateStreak(checkIn.goalId, computeStreak(checkIn.goalId, checkIn.date), checkIn.date)
            id
        }

    /** 从打卡日期集计算截至 [checkInDate] 所在日的连续打卡天数。 */
    private suspend fun computeStreak(goalId: Long, checkInDate: Long): Int {
        val dates = checkInDao.getCheckInsByGoalOnce(goalId)
            .mapNotNull { c ->
                try {
                    if (c.date > 1_000_000_000_000L) {
                        java.time.Instant.ofEpochMilli(c.date).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    } else if (c.date > 0) {
                        java.time.LocalDate.ofEpochDay(c.date)
                    } else null
                } catch (_: Exception) { null }
            }.toSet()
        var streak = 0
        var cursor = try {
            java.time.Instant.ofEpochMilli(checkInDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } catch (_: Exception) { return 0 }
        if (cursor !in dates) cursor = cursor.minusDays(1)  // 补卡场景：从昨天起算不误断
        while (cursor in dates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
