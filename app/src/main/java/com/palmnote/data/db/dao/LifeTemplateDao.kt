package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.LifeTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeTemplateDao {
    @Query("SELECT * FROM life_templates WHERE isHidden = 0 AND isDeleted = 0 ORDER BY category, sortOrder")
    fun getAllVisibleTemplates(): Flow<List<LifeTemplate>>

    @Query("SELECT * FROM life_templates WHERE category = :category AND isHidden = 0 AND isDeleted = 0 ORDER BY sortOrder")
    fun getTemplatesByCategory(category: String): Flow<List<LifeTemplate>>

    @Query("SELECT * FROM life_templates WHERE isDeleted = 0 ORDER BY category, sortOrder")
    fun getAllTemplates(): Flow<List<LifeTemplate>>

    @Query("SELECT * FROM life_templates WHERE id = :id AND isDeleted = 0")
    suspend fun getTemplateById(id: Long): LifeTemplate?

    @Query("SELECT * FROM life_templates WHERE id = :id AND isDeleted = 0")
    fun getTemplateByIdFlow(id: Long): Flow<LifeTemplate?>

    @Query("SELECT * FROM life_templates WHERE isBuiltin = 1 AND isDeleted = 0 ORDER BY category, sortOrder")
    fun getBuiltinTemplates(): Flow<List<LifeTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: LifeTemplate): Long

    @Update
    suspend fun updateTemplate(template: LifeTemplate)

    @Query("UPDATE life_templates SET isHidden = :hidden, updatedAt = :now WHERE id = :id")
    suspend fun setTemplateHidden(id: Long, hidden: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE life_templates SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteTemplate(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE life_templates SET isDeleted = 0, deletedAt = NULL, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restoreTemplate(id: Long, restoredAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTemplate(template: LifeTemplate)

    @Query("DELETE FROM life_templates WHERE id = :id")
    suspend fun hardDeleteTemplateById(id: Long)

    @Query("DELETE FROM life_templates")
    suspend fun deleteAll()
}
