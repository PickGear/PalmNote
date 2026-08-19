package com.palmnote.domain.repository

import com.palmnote.data.db.entity.FocusRecord
import kotlinx.coroutines.flow.Flow

interface FocusRecordRepository {
    fun getAllRecords(): Flow<List<FocusRecord>>
    fun getRecordsByTodo(todoId: Long): Flow<List<FocusRecord>>
    fun getTodayRecords(startOfDay: Long, endOfDay: Long): Flow<List<FocusRecord>>
    suspend fun getRecordById(id: Long): FocusRecord?
    suspend fun getTodayTotalMinutes(startOfDay: Long, endOfDay: Long): Int
    fun getTotalMinutes(): Flow<Int>
    fun getCompletedCount(): Flow<Int>
    suspend fun insertRecord(record: FocusRecord): Long
    suspend fun updateRecord(record: FocusRecord)
    suspend fun deleteRecord(id: Long)
}
