package com.palmnote.data.repository

import com.palmnote.data.db.dao.AnniversaryDao
import com.palmnote.data.db.entity.Anniversary
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.AnniversaryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnniversaryRepository @Inject constructor(
    private val anniversaryDao: AnniversaryDao
) : AnniversaryRepository {
    override fun getAllAnniversaries(): Flow<List<Anniversary>> = anniversaryDao.getAllAnniversaries()

    override suspend fun getAnniversaryById(id: Long): Anniversary? = anniversaryDao.getAnniversaryById(id)

    override fun getAnniversariesByType(type: String): Flow<List<Anniversary>> =
        anniversaryDao.getAnniversariesByType(type)

    override fun getNotificationEnabledAnniversaries(): Flow<List<Anniversary>> =
        anniversaryDao.getNotificationEnabledAnniversaries()

    override fun getDeletedAnniversaries(): Flow<List<Anniversary>> = anniversaryDao.getDeletedAnniversaries()

    override fun getAnniversaryCount(): Flow<Int> = anniversaryDao.getAnniversaryCount()

    override suspend fun insertAnniversary(anniversary: Anniversary): Long =
        anniversaryDao.insertAnniversary(anniversary)

    override suspend fun updateAnniversary(anniversary: Anniversary) =
        anniversaryDao.updateAnniversary(anniversary)

    override suspend fun softDeleteAnniversary(id: Long) = anniversaryDao.softDeleteAnniversary(id)

    override suspend fun restoreAnniversary(id: Long) = anniversaryDao.restoreAnniversary(id)

    override suspend fun hardDeleteAnniversary(id: Long) = anniversaryDao.hardDeleteAnniversary(id)

    override suspend fun search(query: String): List<Anniversary> = anniversaryDao.search(query)
}
