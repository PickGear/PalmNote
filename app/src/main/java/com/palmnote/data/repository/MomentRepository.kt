package com.palmnote.data.repository

import com.palmnote.data.db.dao.MomentDao
import com.palmnote.data.db.entity.Moment
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.MomentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentRepository @Inject constructor(
    private val momentDao: MomentDao
) : MomentRepository {
    override fun getAllMoments(): Flow<List<Moment>> = momentDao.getAllMoments()

    override suspend fun getMomentById(id: Long): Moment? = momentDao.getMomentById(id)

    override fun getMomentsByAsset(assetId: Long): Flow<List<Moment>> = momentDao.getMomentsByAsset(assetId)

    override fun getMomentsByAnniversary(anniversaryId: Long): Flow<List<Moment>> =
        momentDao.getMomentsByAnniversary(anniversaryId)

    override fun getMomentsByGoal(goalId: Long): Flow<List<Moment>> = momentDao.getMomentsByGoal(goalId)

    override fun getMomentsByTag(tag: String): Flow<List<Moment>> = momentDao.getMomentsByTag(tag)

    override fun getMomentCount(): Flow<Int> = momentDao.getMomentCount()

    override fun getDeletedMoments(): Flow<List<Moment>> = momentDao.getDeletedMoments()

    override suspend fun insertMoment(moment: Moment): Long = momentDao.insertMoment(moment)

    override suspend fun updateMoment(moment: Moment) = momentDao.updateMoment(moment)

    override suspend fun setFavorite(id: Long, isFavorite: Boolean) = momentDao.setFavorite(id, isFavorite)

    override suspend fun softDeleteMoment(id: Long) = momentDao.softDeleteMoment(id)

    override suspend fun restoreMoment(id: Long) = momentDao.restoreMoment(id)

    override suspend fun hardDeleteMoment(id: Long) = momentDao.hardDeleteMoment(id)

    override suspend fun search(query: String): List<Moment> = momentDao.search(query)
}
