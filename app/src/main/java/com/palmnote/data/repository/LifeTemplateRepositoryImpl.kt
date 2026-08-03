package com.palmnote.data.repository
import javax.inject.Inject

import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeTemplateRepository
import kotlinx.coroutines.flow.Flow
class LifeTemplateRepositoryImpl @Inject constructor(
    private val dao: LifeTemplateDao
) : LifeTemplateRepository {
    override fun getAllVisibleTemplates(): Flow<List<LifeTemplate>> = dao.getAllVisibleTemplates()
    override fun getTemplatesByCategory(category: String): Flow<List<LifeTemplate>> = dao.getTemplatesByCategory(category)
    override fun getAllTemplates(): Flow<List<LifeTemplate>> = dao.getAllTemplates()
    override suspend fun getTemplateById(id: Long): LifeTemplate? = dao.getTemplateById(id)
    override fun getTemplateByIdFlow(id: Long): Flow<LifeTemplate?> = dao.getTemplateByIdFlow(id)
    override fun getBuiltinTemplates(): Flow<List<LifeTemplate>> = dao.getBuiltinTemplates()
    override suspend fun insertTemplate(template: LifeTemplate): Long = dao.insertTemplate(template)
    override suspend fun updateTemplate(template: LifeTemplate) = dao.updateTemplate(template)
    override suspend fun setTemplateHidden(id: Long, hidden: Boolean) = dao.setTemplateHidden(id, hidden)
    override suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)
}
