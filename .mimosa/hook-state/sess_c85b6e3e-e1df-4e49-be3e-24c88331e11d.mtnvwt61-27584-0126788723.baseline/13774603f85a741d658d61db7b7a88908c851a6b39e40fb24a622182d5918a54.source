package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.FocusRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusRecordDao {
    @Query("SELECT * FROM focus_records ORDER BY startTime DESC")
    fun getAllRecords(): Flow<List<FocusRecord>>

    @Query("SELECT * FROM focus_records WHERE todoId = :todoId ORDER BY startTime DESC")
    fun getRecordsByTodo(todoId: Long): Flow<List<FocusRecord>>

    @Query("SELECT * FROM focus_records WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime DESC")
    fun getTodayRecords(startOfDay: Long, endOfDay: Long): Flow<List<FocusRecord>>

    @Query("SELECT * FROM focus_records WHERE id = :id")
    suspend fun getRecordById(id: Long): FocusRecord?

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_records WHERE startTime >= :startOfDay AND startTime < :endOfDay AND completed = 1")
    suspend fun getTodayTotalMinutes(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM focus_records WHERE completed = 1")
    fun getTotalMinutes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM focus_records WHERE completed = 1")
    fun getCompletedCount(): Flow<Int>

    @Insert
    suspend fun insertRecord(record: FocusRecord): Long

    @Update
    suspend fun updateRecord(record: FocusRecord)

    @Delete
    suspend fun deleteRecord(record: FocusRecord)

    @Query("DELETE FROM focus_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM focus_records")
    suspend fun deleteAll()
}
