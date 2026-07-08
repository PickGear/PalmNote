package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Goal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE isDeleted = 0 ORDER BY priority DESC, createdAt DESC")
    fun getAllGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id AND isDeleted = 0")
    suspend fun getGoalById(id: Long): Goal?

    @Query("SELECT * FROM goals WHERE id = :id AND isDeleted = 0")
    fun getGoalByIdFlow(id: Long): Flow<Goal?>

    @Query("SELECT * FROM goals WHERE category = :category AND isDeleted = 0 ORDER BY priority DESC")
    fun getGoalsByCategory(category: String): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE goalType = :type AND isDeleted = 0 ORDER BY priority DESC")
    fun getGoalsByType(type: String): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE goalType = 'HABIT' AND isDeleted = 0 ORDER BY title ASC")
    fun getHabitGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE currentCount >= totalCount AND isDeleted = 0")
    fun getCompletedGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE currentCount < totalCount AND deadline IS NOT NULL AND deadline < :now AND isDeleted = 0")
    fun getOverdueGoals(now: Long): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE currentCount < totalCount AND isDeleted = 0 ORDER BY deadline ASC")
    fun getInProgressGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE streak > 0 AND isDeleted = 0 ORDER BY streak DESC")
    fun getGoalsWithStreak(): Flow<List<Goal>>

    @Query("SELECT COUNT(*) FROM goals WHERE isDeleted = 0")
    fun getGoalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM goals WHERE currentCount >= totalCount AND isDeleted = 0")
    fun getCompletedGoalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM goals WHERE goalType = 'HABIT' AND isDeleted = 0")
    fun getHabitCount(): Flow<Int>

    @Query("SELECT SUM(streak) FROM goals WHERE isDeleted = 0")
    fun getTotalStreak(): Flow<Int?>

    @Query("SELECT MAX(longestStreak) FROM goals WHERE isDeleted = 0")
    fun getMaxStreak(): Flow<Int?>

    @Query("SELECT * FROM goals WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    suspend fun search(query: String): List<Goal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Query("UPDATE goals SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteGoal(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE goals SET isDeleted = 0, deletedAt = null, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restoreGoal(id: Long, restoredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun hardDeleteGoal(id: Long)

    @Query("UPDATE goals SET currentCount = currentCount + :count, updatedAt = :now WHERE id = :id")
    suspend fun incrementGoalProgress(id: Long, count: Int = 1, now: Long = System.currentTimeMillis())

    @Query("UPDATE goals SET currentCount = :count, updatedAt = :now WHERE id = :id")
    suspend fun setGoalProgress(id: Long, count: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE goals SET streak = :streak, longestStreak = MAX(longestStreak, :streak), lastCheckInDate = :checkInDate, totalCheckInDays = totalCheckInDays + 1, updatedAt = :now WHERE id = :id")
    suspend fun updateStreak(id: Long, streak: Int, checkInDate: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE goals SET currentPeriodCount = currentPeriodCount + :count, updatedAt = :now WHERE id = :id")
    suspend fun incrementPeriodCount(id: Long, count: Int = 1, now: Long = System.currentTimeMillis())

    @Query("UPDATE goals SET currentPeriodCount = 0, periodStartDate = :startDate, updatedAt = :now WHERE id = :id")
    suspend fun resetPeriodCount(id: Long, startDate: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE goals SET notes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Query("SELECT * FROM goals WHERE goalType = 'HABIT' AND isDeleted = 0 AND currentPeriodEnd > 0 AND currentPeriodEnd < :now")
    suspend fun getHabitGoalsNeedingReset(now: Long = System.currentTimeMillis()): List<Goal>

    @Query("UPDATE goals SET currentCount = 0, currentPeriodStart = :periodStart, currentPeriodEnd = :periodEnd, updatedAt = :now WHERE id IN (:ids)")
    suspend fun batchResetPeriod(ids: List<Long>, periodStart: Long, periodEnd: Long, now: Long = System.currentTimeMillis())

}
