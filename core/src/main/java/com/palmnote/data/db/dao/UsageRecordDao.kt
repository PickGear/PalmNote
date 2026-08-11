package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.UsageRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageRecordDao {
    @Query("SELECT * FROM usage_records WHERE assetId = :assetId ORDER BY usedAt DESC")
    fun getUsageRecordsByAsset(assetId: Long): Flow<List<UsageRecord>>

    @Query("SELECT * FROM usage_records WHERE assetId = :assetId ORDER BY usedAt DESC LIMIT :limit")
    fun getRecentUsageRecords(assetId: Long, limit: Int = 10): Flow<List<UsageRecord>>

    @Query("SELECT COUNT(*) FROM usage_records WHERE assetId = :assetId")
    fun getUsageCount(assetId: Long): Flow<Int>

    @Query("SELECT * FROM usage_records ORDER BY usedAt DESC")
    fun getAllUsageRecords(): Flow<List<UsageRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageRecord(record: UsageRecord): Long

    @Update
    suspend fun updateUsageRecord(record: UsageRecord)

    @Delete
    suspend fun deleteUsageRecord(record: UsageRecord)

    @Query("DELETE FROM usage_records WHERE id = :id")
    suspend fun deleteUsageRecordById(id: Long)

    @Query("DELETE FROM usage_records WHERE assetId = :assetId")
    suspend fun deleteAllByAssetId(assetId: Long)

    @Query("DELETE FROM usage_records")
    suspend fun deleteAll()
}
