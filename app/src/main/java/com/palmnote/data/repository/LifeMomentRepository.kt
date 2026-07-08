package com.palmnote.data.repository

import com.palmnote.data.db.dao.LifeMomentDao
import com.palmnote.data.db.entity.LifeMoment
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.LifeMomentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LifeMomentRepository @Inject constructor(
    private val lifeMomentDao: LifeMomentDao
) : LifeMomentRepository {
    override fun getRecentMoments(limit: Int): Flow<List<LifeMoment>> = lifeMomentDao.getRecentMoments(limit)

    override fun getAllMoments(): Flow<List<LifeMoment>> = lifeMomentDao.getAllMoments()

    override suspend fun getMomentById(id: Long): LifeMoment? = lifeMomentDao.getMomentById(id)

    override suspend fun insertMoment(moment: LifeMoment): Long = lifeMomentDao.insertMoment(moment)

    override suspend fun updateMoment(moment: LifeMoment) = lifeMomentDao.updateMoment(moment)

    override suspend fun softDeleteMoment(id: Long) = lifeMomentDao.softDeleteMoment(id)

    override fun getMomentCount(): Flow<Int> = lifeMomentDao.getMomentCount()
}
