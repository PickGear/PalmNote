package com.palmnote.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.palmnote.data.db.entity.BillRecycleBin
import kotlinx.coroutines.flow.Flow

@Dao
interface BillRecycleBinDao {
    @Insert
    suspend fun insert(item: BillRecycleBin)

    @Query("SELECT * FROM bills_recycle_bin ORDER BY deletedAt DESC")
    fun getAll(): Flow<List<BillRecycleBin>>

    @Query("SELECT * FROM bills_recycle_bin WHERE id = :id")
    suspend fun getById(id: Long): BillRecycleBin?

    @Query("DELETE FROM bills_recycle_bin WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bills_recycle_bin")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM bills_recycle_bin")
    fun getCount(): Flow<Int>
}
