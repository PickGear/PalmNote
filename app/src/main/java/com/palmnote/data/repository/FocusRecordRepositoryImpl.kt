package com.palmnote.data.repository

import com.palmnote.data.db.dao.FocusRecordDao
import com.palmnote.data.db.entity.FocusRecord
import com.palmnote.domain.repository.FocusRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusRecordRepositoryImpl @Inject constructor(
    private val dao: FocusRecordDao
) : FocusRecordRepository {
    override fun getAllRecords(): Flow<List<FocusRecord>> = dao.getAllRecords()
    override fun getRecordsByTodo(todoId: Long): Flow<List<FocusRecord>> = dao.getRecordsByTodo(todoId)
    override fun getTodayRecords(startOfDay: Long, endOfDay: Long): Flow<List<FocusRecord>> = dao.getTodayRecords(startOfDay, endOfDay)
    override suspend fun getRecordById(id: Long): FocusRecord? = dao.getRecordById(id)
    override suspend fun getTodayTotalMinutes(startOfDay: Long, endOfDay: Long): Int = dao.getTodayTotalMinutes(startOfDay, endOfDay)
    override fun getTotalMinutes(): Flow<Int> = dao.getTotalMinutes()
    override fun getCompletedCount(): Flow<Int> = dao.getCompletedCount()
    override suspend fun insertRecord(record: FocusRecord): Long = dao.insertRecord(record)
    override suspend fun updateRecord(record: FocusRecord) = dao.updateRecord(record)
    override suspend fun deleteRecord(id: Long) = dao.deleteRecordById(id)
}
