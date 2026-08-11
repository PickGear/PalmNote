package com.palmnote.domain.repository

import com.palmnote.data.db.entity.LifeReport
import kotlinx.coroutines.flow.Flow

interface LifeReportRepository {
    fun getReportsByType(type: String): Flow<List<LifeReport>>
    fun getReportsByTypeAndRange(type: String, start: Long, end: Long): Flow<List<LifeReport>>
    suspend fun getReportById(id: Long): LifeReport?
    suspend fun getReport(type: String, periodStart: Long): LifeReport?
    suspend fun insertReport(report: LifeReport): Long
    suspend fun deleteReport(type: String, periodStart: Long)
}
