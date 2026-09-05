package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.LifeReport
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeReportDao {
    @Query("SELECT * FROM life_reports WHERE type = :type ORDER BY periodStart DESC LIMIT 12")
    fun getReportsByType(type: String): Flow<List<LifeReport>>

    @Query("SELECT * FROM life_reports WHERE type = :type AND periodStart >= :start AND periodStart <= :end ORDER BY periodStart DESC")
    fun getReportsByTypeAndRange(type: String, start: Long, end: Long): Flow<List<LifeReport>>

    @Query("SELECT * FROM life_reports WHERE id = :id")
    suspend fun getReportById(id: Long): LifeReport?

    @Query("SELECT * FROM life_reports WHERE type = :type AND periodStart = :periodStart")
    suspend fun getReport(type: String, periodStart: Long): LifeReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: LifeReport): Long

    @Query("DELETE FROM life_reports WHERE type = :type AND periodStart = :periodStart")
    suspend fun deleteReport(type: String, periodStart: Long)

    @Query("DELETE FROM life_reports")
    suspend fun deleteAll()
}
