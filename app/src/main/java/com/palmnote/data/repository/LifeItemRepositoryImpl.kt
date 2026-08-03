package com.palmnote.data.repository
import javax.inject.Inject

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeItemPagingSource
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.util.AppLogger
import kotlinx.coroutines.flow.Flow
class LifeItemRepositoryImpl @Inject constructor(
    private val dao: LifeItemDao
) : LifeItemRepository {
    override fun getAllItems(): Flow<List<LifeItem>> = dao.getAllItems()
    override fun getItemsByTemplate(templateId: Long): Flow<List<LifeItem>> = dao.getItemsByTemplate(templateId)
    override fun getItemsByTemplateAndStatus(templateId: Long, status: String): Flow<List<LifeItem>> = dao.getItemsByTemplateAndStatus(templateId, status)
    override suspend fun getItemById(id: Long): LifeItem? = dao.getItemById(id)
    override fun getItemByIdFlow(id: Long): Flow<LifeItem?> = dao.getItemByIdFlow(id)
    override fun getActiveItemsByTemplate(templateId: Long, limit: Int): Flow<List<LifeItem>> = dao.getActiveItemsByTemplate(templateId, limit)
    override fun getItemCountByTemplate(templateId: Long): Flow<Int> = dao.getItemCountByTemplate(templateId)
    override fun getTotalItemCount(): Flow<Int> = dao.getTotalItemCount()
    override fun getPagedItemsByTemplate(templateId: Long): Flow<PagingData<LifeItem>> = Pager(PagingConfig(pageSize = 20)) { LifeItemPagingSource(dao, templateId) }.flow
    override fun getPagedAllItems(): Flow<PagingData<LifeItem>> = Pager(PagingConfig(pageSize = 20)) { LifeItemPagingSource(dao) }.flow
    override suspend fun search(query: String): List<LifeItem> = dao.search(query)
    override suspend fun insertItem(item: LifeItem): Long = try {
        dao.insertItem(item)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "insertItem failed", e)
        throw e
    }
    override suspend fun updateItem(item: LifeItem) = try {
        dao.updateItem(id = item.id, title = item.title, fieldsData = item.fieldsData, status = item.status, note = item.note, sortOrder = item.sortOrder, isFavorite = item.isFavorite)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "updateItem failed", e)
        throw e
    }
    override suspend fun updateStatus(id: Long, status: String) = try {
        dao.updateStatus(id, status)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "updateStatus failed", e)
        throw e
    }
    override suspend fun updateFieldsData(id: Long, fieldsData: String) = try {
        dao.updateFieldsData(id, fieldsData)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "updateFieldsData failed", e)
        throw e
    }
    override suspend fun setFavorite(id: Long, favorite: Boolean) = try {
        dao.setFavorite(id, favorite)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "setFavorite failed", e)
        throw e
    }
    override suspend fun delete(id: Long) = try {
        dao.deleteItem(id)
    } catch (e: Exception) {
        AppLogger.e("LifeItemRepo", "softDelete failed", e)
        throw e
    }
}
