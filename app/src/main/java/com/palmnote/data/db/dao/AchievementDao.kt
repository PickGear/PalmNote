package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Achievement
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY unlockedAt IS NULL, unlockedAt ASC")
    fun getAllAchievements(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE code = :code")
    suspend fun getAchievementByCode(code: String): Achievement?

    @Query("SELECT * FROM achievements WHERE unlockedAt IS NOT NULL ORDER BY unlockedAt DESC")
    fun getUnlockedAchievements(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE unlockedAt IS NULL ORDER BY id ASC")
    fun getLockedAchievements(): Flow<List<Achievement>>

    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE code = :code AND unlockedAt IS NOT NULL)")
    suspend fun isUnlocked(code: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: Achievement): Long

    @Query("UPDATE achievements SET unlockedAt = :timestamp, goalId = :goalId WHERE code = :code")
    suspend fun unlockAchievement(code: String, timestamp: Long = System.currentTimeMillis(), goalId: Long? = null)
}
