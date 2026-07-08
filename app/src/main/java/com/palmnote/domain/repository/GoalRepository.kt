package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Goal
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun getAllGoals(): Flow<List<Goal>>
    suspend fun getGoalById(id: Long): Goal?
    fun getGoalsByCategory(category: String): Flow<List<Goal>>
    fun getGoalCount(): Flow<Int>
    fun getCompletedGoalCount(): Flow<Int>
    fun getOverdueGoals(now: Long): Flow<List<Goal>>
    fun getHabitGoals(): Flow<List<Goal>>
    fun getTotalStreak(): Flow<Int?>
    fun getDeletedGoals(): Flow<List<Goal>>
    suspend fun insertGoal(goal: Goal): Long
    suspend fun updateGoal(goal: Goal)
    suspend fun softDeleteGoal(id: Long)
    suspend fun restoreGoal(id: Long)
    suspend fun hardDeleteGoal(id: Long)
    suspend fun incrementGoalProgress(id: Long)
    suspend fun updateStreak(id: Long, streak: Int, checkInDate: Long, now: Long)
    suspend fun search(query: String): List<Goal>
    suspend fun setGoalProgress(id: Long, count: Int)
    suspend fun getHabitGoalsNeedingReset(): List<Goal>
    suspend fun batchResetPeriod(ids: List<Long>, periodStart: Long, periodEnd: Long)
}
