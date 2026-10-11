package com.palmnote.data.repository
import androidx.room.withTransaction
import javax.inject.Inject

import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.FieldValueDao
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeTemplateRepository
import kotlinx.coroutines.flow.Flow
class LifeTemplateRepositoryImpl @Inject constructor(
    private val dao: LifeTemplateDao,
    private val itemDao: LifeItemDao,
    private val fieldValueDao: FieldValueDao,
    private val appDatabase: AppDatabase
) : LifeTemplateRepository {
    override fun getAllVisibleTemplates(): Flow<List<LifeTemplate>> = dao.getAllVisibleTemplates()
    override fun getTemplatesByCategory(category: String): Flow<List<LifeTemplate>> = dao.getTemplatesByCategory(category)
    override fun getAllTemplates(): Flow<List<LifeTemplate>> = dao.getAllTemplates()
    override suspend fun getTemplateById(id: Long): LifeTemplate? = dao.getTemplateById(id)
    override fun getTemplateByIdFlow(id: Long): Flow<LifeTemplate?> = dao.getTemplateByIdFlow(id)
    override fun getBuiltinTemplates(): Flow<List<LifeTemplate>> = dao.getBuiltinTemplates()
    override fun searchTemplates(query: String): Flow<List<LifeTemplate>> = dao.searchTemplates(query)
    override suspend fun insertTemplate(template: LifeTemplate): Long = dao.insertTemplate(template)
    override suspend fun updateTemplate(template: LifeTemplate) = dao.updateTemplate(template)
    override suspend fun setTemplateHidden(id: Long, hidden: Boolean) = dao.setTemplateHidden(id, hidden)
    override suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)
    override suspend fun deleteTemplateCascade(id: Long) {
        // 与主写入路径同规：条目行与统计副本（field_values）必须同事务清掉，
        // 否则模板删了、聚合查询还在喂孤儿行。副本必须**先删**——deleteByTemplate 靠
        // life_items 反查 itemId，条目先删就查不到要删的副本了。
        // 示例条目不写 field_values，无对应副本可清。
        appDatabase.withTransaction {
            fieldValueDao.deleteByTemplate(id)
            itemDao.deleteItemsByTemplate(id)
            dao.deleteTemplate(id)
        }
    }
}
