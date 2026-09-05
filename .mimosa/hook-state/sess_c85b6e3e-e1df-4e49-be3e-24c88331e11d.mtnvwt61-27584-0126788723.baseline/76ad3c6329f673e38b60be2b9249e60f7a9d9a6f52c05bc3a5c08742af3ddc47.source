package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.LifeTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeTemplateDao {
    @Query("SELECT * FROM life_templates WHERE isHidden = 0 ORDER BY category, sortOrder")
    fun getAllVisibleTemplates(): Flow<List<LifeTemplate>>

    @Query("SELECT * FROM life_templates WHERE category = :category AND isHidden = 0 ORDER BY sortOrder")
    fun getTemplatesByCategory(category: String): Flow<List<LifeTemplate>>

    @Query("SELECT * FROM life_templates ORDER BY category, sortOrder")
    fun getAllTemplates(): Flow<List<LifeTemplate>>

    @Query("SELECT * FROM life_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): LifeTemplate?

    @Query("SELECT * FROM life_templates WHERE id = :id")
    fun getTemplateByIdFlow(id: Long): Flow<LifeTemplate?>

    @Query("SELECT * FROM life_templates WHERE isBuiltin = 1 ORDER BY category, sortOrder")
    fun getBuiltinTemplates(): Flow<List<LifeTemplate>>

    @Query("""
        SELECT * FROM life_templates
        WHERE isHidden = 0
          AND (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY category, sortOrder
    """)
    fun searchTemplates(query: String): Flow<List<LifeTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: LifeTemplate): Long

    @Update
    suspend fun updateTemplate(template: LifeTemplate)

    @Query("UPDATE life_templates SET isHidden = :hidden, updatedAt = :now WHERE id = :id")
    suspend fun setTemplateHidden(id: Long, hidden: Boolean, now: Long = System.currentTimeMillis())



    @Delete
    suspend fun deleteTemplate(template: LifeTemplate)

    @Query("DELETE FROM life_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM life_templates")
    suspend fun deleteAll()
}
