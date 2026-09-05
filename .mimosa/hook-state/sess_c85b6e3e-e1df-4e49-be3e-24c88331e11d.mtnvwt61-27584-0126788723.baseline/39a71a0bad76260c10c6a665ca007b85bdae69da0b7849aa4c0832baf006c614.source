package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Achievement
import kotlinx.coroutines.flow.Flow

interface AchievementRepository {
    fun getAllAchievements(): Flow<List<Achievement>>
    fun getUnlockedAchievements(): Flow<List<Achievement>>
    fun getLockedAchievements(): Flow<List<Achievement>>
    suspend fun getAchievementByCode(code: String): Achievement?
    suspend fun isUnlocked(code: String): Boolean
    suspend fun insertAchievement(achievement: Achievement): Long
    suspend fun unlockAchievement(code: String, goalId: Long?)
}
