package com.palmnote.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeItemPagingSource
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.repository.LifeItemRepository
import kotlinx.coroutines.flow.Flow
class LifeItemRepositoryImpl(
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
    override suspend fun search(query: String): List<LifeItem> = try {
        val ftsQuery = query.split("\\s+".toRegex()).filter { it.isNotBlank() }.joinToString(" ") { "\"$it\"*" }
        if (ftsQuery.isBlank()) dao.search(query)
        else dao.searchFts(androidx.sqlite.db.SimpleSQLiteQuery("SELECT life_items.* FROM life_items INNER JOIN life_items_fts ON life_items.id = life_items_fts.rowid WHERE life_items_fts MATCH ? AND life_items.isDeleted = 0 ORDER BY life_items.updatedAt DESC LIMIT 50", arrayOf(ftsQuery)))
    } catch (_: Exception) {
        dao.search(query)
    }
    override suspend fun insertItem(item: LifeItem): Long = try {
        dao.insertItem(item)
    } catch (e: Exception) {
        android.util.Log.e("LifeItemRepo", "insertItem failed", e)
        throw e
    }
    override suspend fun updateItem(item: LifeItem) = try {
        dao.updateItem(id = item.id, title = item.title, fieldsData = item.fieldsData, status = item.status, note = item.note, sortOrder = item.sortOrder, isFavorite = item.isFavorite)
    } catch (e: Exception) {
        android.util.Log.e("LifeItemRepo", "updateItem failed", e)
        throw e
    }
    override suspend fun updateStatus(id: Long, status: String) = try {
        dao.updateStatus(id, status)
    } catch (e: Exception) {
        android.util.Log.e("LifeItemRepo", "updateStatus failed", e)
        throw e
    }
    override suspend fun updateFieldsData(id: Long, fieldsData: String) = try {
        dao.updateFieldsData(id, fieldsData)
    } catch (e: Exception) {
        android.util.Log.e("LifeItemRepo", "updateFieldsData failed", e)
        throw e
    }
    override suspend fun setFavorite(id: Long, favorite: Boolean) = try {
        dao.setFavorite(id, favorite)
    } catch (e: Exception) {
        android.util.Log.e("LifeItemRepo", "setFavorite failed", e)
        throw e
    }
    override suspend fun softDelete(id: Long) = try {
        dao.softDelete(id)
    } catch (e: Exception) {
        android.util.Log.e("LifeItemRepo", "softDelete failed", e)
        throw e
    }
    override suspend fun restore(id: Long) = try {
        dao.restore(id)
    } catch (e: Exception) {
        android.util.Log.e("LifeItemRepo", "restore failed", e)
        throw e
    }
    override suspend fun hardDelete(id: Long) = try {
        dao.hardDeleteById(id)
    } catch (e: Exception) {
        android.util.Log.e("LifeItemRepo", "hardDelete failed", e)
        throw e
    }
}
