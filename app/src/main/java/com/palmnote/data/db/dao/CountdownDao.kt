package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Countdown
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdowns WHERE isDeleted = 0 AND targetDate >= :today ORDER BY targetDate ASC")
    fun getUpcomingCountdowns(today: Long = System.currentTimeMillis()): Flow<List<Countdown>>

    @Query("SELECT * FROM countdowns WHERE id = :id AND isDeleted = 0")
    suspend fun getCountdownById(id: Long): Countdown?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountdown(countdown: Countdown): Long

    @Update
    suspend fun updateCountdown(countdown: Countdown)

    @Query("UPDATE countdowns SET isDeleted = 1, deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteCountdown(id: Long, now: Long = System.currentTimeMillis())
}
