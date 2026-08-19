package com.palmnote.data.repository
import javax.inject.Inject

import com.palmnote.data.db.dao.GoalDao
import com.palmnote.data.db.entity.Goal
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.GoalRepository
class GoalRepositoryImpl @Inject constructor(
    private val goalDao: GoalDao,
    private val checkInDao: com.palmnote.data.db.dao.GoalCheckInDao
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

    override suspend fun deleteGoal(id: Long) = goalDao.deleteGoal(id)


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

    override suspend fun insertCheckIn(checkIn: com.palmnote.data.db.entity.GoalCheckIn): Long = checkInDao.insertCheckIn(checkIn)
}
