package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.GoalCheckIn
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalCheckInDao {
    @Query("SELECT * FROM goal_check_ins WHERE goalId = :goalId ORDER BY date DESC")
    fun getCheckInsByGoal(goalId: Long): Flow<List<GoalCheckIn>>

    @Query("SELECT * FROM goal_check_ins WHERE goalId = :goalId ORDER BY date DESC LIMIT :limit")
    fun getRecentCheckIns(goalId: Long, limit: Int = 30): Flow<List<GoalCheckIn>>

    @Query("SELECT * FROM goal_check_ins WHERE goalId = :goalId AND date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getCheckInsByDateRange(goalId: Long, startDate: Long, endDate: Long): Flow<List<GoalCheckIn>>

    @Query("SELECT COUNT(*) FROM goal_check_ins WHERE goalId = :goalId")
    fun getTotalCheckInCount(goalId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM goal_check_ins WHERE goalId = :goalId AND date >= :startDate")
    fun getCheckInCountSince(goalId: Long, startDate: Long): Flow<Int>

    @Query("SELECT * FROM goal_check_ins WHERE goalId = :goalId AND date >= :dayStart AND date < :dayEnd LIMIT 1")
    suspend fun getTodayCheckIn(goalId: Long, dayStart: Long, dayEnd: Long): GoalCheckIn?

    @Query("SELECT SUM(count) FROM goal_check_ins WHERE goalId = :goalId")
    fun getTotalCount(goalId: Long): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: GoalCheckIn): Long

    @Update
    suspend fun updateCheckIn(checkIn: GoalCheckIn)

    @Delete
    suspend fun deleteCheckIn(checkIn: GoalCheckIn)

    @Query("DELETE FROM goal_check_ins WHERE id = :id")
    suspend fun deleteCheckInById(id: Long)

    @Query("DELETE FROM goal_check_ins WHERE goalId = :goalId")
    suspend fun deleteAllByGoalId(goalId: Long)

    @Query("DELETE FROM goal_check_ins")
    suspend fun deleteAll()
}
