package com.palmnote.data.repository

import com.palmnote.data.db.dao.GoalDao
import com.palmnote.data.db.entity.Goal
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.GoalRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao
) : GoalRepository {
    override fun getAllGoals(): Flow<List<Goal>> = goalDao.getAllGoals()

    override fun getRecentGoals(): Flow<List<Goal>> = goalDao.getRecentGoals()

    override suspend fun getGoalById(id: Long): Goal? = goalDao.getGoalById(id)

    override fun getGoalsByCategory(category: String): Flow<List<Goal>> = goalDao.getGoalsByCategory(category)

    override fun getGoalCount(): Flow<Int> = goalDao.getGoalCount()

    override fun getCompletedGoalCount(): Flow<Int> = goalDao.getCompletedGoalCount()

    override fun getOverdueGoals(now: Long): Flow<List<Goal>> = goalDao.getOverdueGoals(now)

    override fun getHabitGoals(): Flow<List<Goal>> = goalDao.getHabitGoals()

    override fun getTotalStreak(): Flow<Int?> = goalDao.getTotalStreak()

    override fun getDeletedGoals(): Flow<List<Goal>> = goalDao.getDeletedGoals()

    override suspend fun insertGoal(goal: Goal): Long = goalDao.insertGoal(goal)

    override suspend fun updateGoal(goal: Goal) = goalDao.updateGoal(goal)

    override suspend fun softDeleteGoal(id: Long) = goalDao.softDeleteGoal(id)

    override suspend fun restoreGoal(id: Long) = goalDao.restoreGoal(id)

    override suspend fun hardDeleteGoal(id: Long) = goalDao.hardDeleteGoal(id)

    override suspend fun incrementGoalProgress(id: Long) = goalDao.incrementGoalProgress(id)

    override suspend fun updateStreak(id: Long, streak: Int, checkInDate: Long, now: Long) = goalDao.updateStreak(id, streak, checkInDate, now)

    override suspend fun search(query: String): List<Goal> = goalDao.search(query)

    override suspend fun setGoalProgress(id: Long, count: Int) = goalDao.setGoalProgress(id, count)

    override suspend fun getHabitGoalsNeedingReset(): List<Goal> = goalDao.getHabitGoalsNeedingReset()

    override suspend fun batchResetPeriod(ids: List<Long>, periodStart: Long, periodEnd: Long) = goalDao.batchResetPeriod(ids, periodStart, periodEnd)
}
