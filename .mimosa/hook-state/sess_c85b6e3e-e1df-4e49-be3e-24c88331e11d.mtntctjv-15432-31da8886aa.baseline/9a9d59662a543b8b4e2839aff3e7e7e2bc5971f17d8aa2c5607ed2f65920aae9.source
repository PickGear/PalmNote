package com.palmnote.domain.repository

import com.palmnote.data.db.entity.UsageRecord
import kotlinx.coroutines.flow.Flow

interface UsageRecordRepository {
    fun getUsageRecordsByAsset(assetId: Long): Flow<List<UsageRecord>>
    fun getRecentUsageRecords(assetId: Long, limit: Int): Flow<List<UsageRecord>>
    fun getUsageCount(assetId: Long): Flow<Int>
    fun getAllUsageRecords(): Flow<List<UsageRecord>>
    suspend fun insertUsageRecord(record: UsageRecord): Long
    suspend fun updateUsageRecord(record: UsageRecord)
    suspend fun deleteUsageRecord(record: UsageRecord)
    suspend fun deleteUsageRecordById(id: Long)
    suspend fun deleteAllByAssetId(assetId: Long)
}
