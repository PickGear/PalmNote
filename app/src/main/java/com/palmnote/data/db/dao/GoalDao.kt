package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Goal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY priority DESC, createdAt DESC")
    fun getAllGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoalById(id: Long): Goal?

    @Query("SELECT * FROM goals WHERE id = :id")
    fun getGoalByIdFlow(id: Long): Flow<Goal?>

    @Query("SELECT * FROM goals WHERE category = :category ORDER BY priority DESC")
    fun getGoalsByCategory(category: String): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE goalType = :type ORDER BY priority DESC")
    fun getGoalsByType(type: String): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE goalType = 'HABIT' ORDER BY title ASC")
    fun getHabitGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE currentCount >= totalCount")
    fun getCompletedGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE currentCount < totalCount AND deadline IS NOT NULL AND deadline < :now")
    fun getOverdueGoals(now: Long): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE currentCount < totalCount ORDER BY deadline ASC")
    fun getInProgressGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE streak > 0 ORDER BY streak DESC")
    fun getGoalsWithStreak(): Flow<List<Goal>>

    @Query("SELECT COUNT(*) FROM goals WHERE 1=1")
    fun getGoalCount(): Flow<Int>

    @Query("SELECT * FROM goals ORDER BY priority DESC, createdAt DESC LIMIT 3")
    fun getRecentGoals(): Flow<List<Goal>>

    @Query("SELECT COUNT(*) FROM goals WHERE currentCount >= totalCount")
    fun getCompletedGoalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM goals WHERE goalType = 'HABIT'")
    fun getHabitCount(): Flow<Int>

    @Query("SELECT SUM(streak) FROM goals WHERE 1=1")
    fun getTotalStreak(): Flow<Int?>

    @Query("SELECT MAX(longestStreak) FROM goals WHERE 1=1")
    fun getMaxStreak(): Flow<Int?>


    @Query("SELECT * FROM goals WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    suspend fun search(query: String): List<Goal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

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

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Query("SELECT * FROM goals WHERE goalType = 'HABIT' AND currentPeriodEnd > 0 AND currentPeriodEnd < :now")
    suspend fun getHabitGoalsNeedingReset(now: Long = System.currentTimeMillis()): List<Goal>

    @Query("UPDATE goals SET currentCount = 0, currentPeriodStart = :periodStart, currentPeriodEnd = :periodEnd, updatedAt = :now WHERE id IN (:ids)")
    suspend fun batchResetPeriod(ids: List<Long>, periodStart: Long, periodEnd: Long, now: Long = System.currentTimeMillis())

}
