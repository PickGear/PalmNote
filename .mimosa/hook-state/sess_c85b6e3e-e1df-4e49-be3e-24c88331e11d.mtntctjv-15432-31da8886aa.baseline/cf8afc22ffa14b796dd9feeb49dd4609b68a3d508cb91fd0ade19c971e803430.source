package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.RecurringTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTemplateDao {
    @Query("SELECT * FROM recurring_templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<RecurringTemplate>>

    @Query("SELECT * FROM recurring_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): RecurringTemplate?

    @Query("SELECT * FROM recurring_templates WHERE isActive = 1")
    fun getActiveTemplates(): Flow<List<RecurringTemplate>>

    @Query("SELECT * FROM recurring_templates WHERE type = :type")
    fun getTemplatesByType(type: String): Flow<List<RecurringTemplate>>

    @Query("SELECT * FROM recurring_templates WHERE isActive = 1 AND nextGenerateDate <= :now")
    suspend fun getTemplatesDueForGeneration(now: Long): List<RecurringTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: RecurringTemplate): Long

    @Update
    suspend fun updateTemplate(template: RecurringTemplate)

    @Query("UPDATE recurring_templates SET lastGeneratedDate = :date, nextGenerateDate = :nextDate, updatedAt = :now WHERE id = :id")
    suspend fun updateGenerationInfo(id: Long, date: Long, nextDate: Long, now: Long = System.currentTimeMillis())



    @Query("DELETE FROM recurring_templates")
    suspend fun deleteAll()
}
