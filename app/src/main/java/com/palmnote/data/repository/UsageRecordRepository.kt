package com.palmnote.data.repository

import com.palmnote.data.db.dao.UsageRecordDao
import com.palmnote.data.db.entity.UsageRecord
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.UsageRecordRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRecordRepository @Inject constructor(
    private val usageRecordDao: UsageRecordDao
) : UsageRecordRepository {
    override fun getUsageRecordsByAsset(assetId: Long): Flow<List<UsageRecord>> =
        usageRecordDao.getUsageRecordsByAsset(assetId)

    override fun getRecentUsageRecords(assetId: Long, limit: Int): Flow<List<UsageRecord>> =
        usageRecordDao.getRecentUsageRecords(assetId, limit)

    override fun getUsageCount(assetId: Long): Flow<Int> = usageRecordDao.getUsageCount(assetId)

    override fun getAllUsageRecords(): Flow<List<UsageRecord>> = usageRecordDao.getAllUsageRecords()

    override suspend fun insertUsageRecord(record: UsageRecord): Long =
        usageRecordDao.insertUsageRecord(record)

    override suspend fun updateUsageRecord(record: UsageRecord) =
        usageRecordDao.updateUsageRecord(record)

    override suspend fun deleteUsageRecord(record: UsageRecord) =
        usageRecordDao.deleteUsageRecord(record)

    override suspend fun deleteUsageRecordById(id: Long) =
        usageRecordDao.deleteUsageRecordById(id)

    override suspend fun deleteAllByAssetId(assetId: Long) =
        usageRecordDao.deleteAllByAssetId(assetId)
}
