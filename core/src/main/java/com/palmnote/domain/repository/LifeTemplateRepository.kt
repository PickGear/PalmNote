package com.palmnote.domain.repository

import com.palmnote.data.db.entity.LifeTemplate
import kotlinx.coroutines.flow.Flow

interface LifeTemplateRepository {
    fun getAllVisibleTemplates(): Flow<List<LifeTemplate>>
    fun getTemplatesByCategory(category: String): Flow<List<LifeTemplate>>
    fun getAllTemplates(): Flow<List<LifeTemplate>>
    suspend fun getTemplateById(id: Long): LifeTemplate?
    fun getTemplateByIdFlow(id: Long): Flow<LifeTemplate?>
    fun getBuiltinTemplates(): Flow<List<LifeTemplate>>
    fun searchTemplates(query: String): Flow<List<LifeTemplate>>
    suspend fun insertTemplate(template: LifeTemplate): Long
    suspend fun updateTemplate(template: LifeTemplate)
    suspend fun setTemplateHidden(id: Long, hidden: Boolean)
    suspend fun deleteTemplate(id: Long)
    /** 关联删除：先删该模板下全部用户记录，再删模板（仅自定义模板调用）。 */
    suspend fun deleteTemplateCascade(id: Long)
}
