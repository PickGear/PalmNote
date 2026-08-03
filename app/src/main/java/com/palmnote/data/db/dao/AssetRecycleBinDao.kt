package com.palmnote.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.palmnote.data.db.entity.AssetRecycleBin
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetRecycleBinDao {
    @Insert
    suspend fun insert(item: AssetRecycleBin)

    @Query("SELECT * FROM assets_recycle_bin ORDER BY deletedAt DESC")
    fun getAll(): Flow<List<AssetRecycleBin>>

    @Query("SELECT * FROM assets_recycle_bin WHERE id = :id")
    suspend fun getById(id: Long): AssetRecycleBin?

    @Query("DELETE FROM assets_recycle_bin WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM assets_recycle_bin")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM assets_recycle_bin")
    fun getCount(): Flow<Int>
}
