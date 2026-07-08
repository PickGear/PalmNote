package com.palmnote.data.repository

import com.palmnote.data.db.dao.LifeReportDao
import com.palmnote.data.db.entity.LifeReport
import com.palmnote.domain.repository.LifeReportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LifeReportRepositoryImpl @Inject constructor(
    private val dao: LifeReportDao
) : LifeReportRepository {
    override fun getReportsByType(type: String): Flow<List<LifeReport>> = dao.getReportsByType(type)
    override fun getReportsByTypeAndRange(type: String, start: Long, end: Long): Flow<List<LifeReport>> = dao.getReportsByTypeAndRange(type, start, end)
    override suspend fun getReportById(id: Long): LifeReport? = dao.getReportById(id)
    override suspend fun getReport(type: String, periodStart: Long): LifeReport? = dao.getReport(type, periodStart)
    override suspend fun insertReport(report: LifeReport): Long = dao.insertReport(report)
    override suspend fun deleteReport(type: String, periodStart: Long) = dao.deleteReport(type, periodStart)
}
