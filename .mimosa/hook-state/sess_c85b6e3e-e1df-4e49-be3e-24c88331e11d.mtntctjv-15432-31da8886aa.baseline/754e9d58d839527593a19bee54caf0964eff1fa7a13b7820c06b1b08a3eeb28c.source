package com.palmnote.data.repository
import javax.inject.Inject

import com.palmnote.data.db.dao.AchievementDao
import com.palmnote.data.db.entity.Achievement
import com.palmnote.domain.repository.AchievementRepository
import kotlinx.coroutines.flow.Flow
class AchievementRepositoryImpl @Inject constructor(
    private val dao: AchievementDao
) : AchievementRepository {
    override fun getAllAchievements(): Flow<List<Achievement>> = dao.getAllAchievements()
    override fun getUnlockedAchievements(): Flow<List<Achievement>> = dao.getUnlockedAchievements()
    override fun getLockedAchievements(): Flow<List<Achievement>> = dao.getLockedAchievements()
    override suspend fun getAchievementByCode(code: String): Achievement? = dao.getAchievementByCode(code)
    override suspend fun isUnlocked(code: String): Boolean = dao.isUnlocked(code)
    override suspend fun insertAchievement(achievement: Achievement): Long = dao.insertAchievement(achievement)
    override suspend fun unlockAchievement(code: String, goalId: Long?) = dao.unlockAchievement(code, goalId = goalId)
}
