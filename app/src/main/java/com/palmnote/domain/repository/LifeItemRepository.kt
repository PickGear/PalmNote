package com.palmnote.domain.repository

import androidx.paging.PagingData
import com.palmnote.data.db.entity.LifeItem
import kotlinx.coroutines.flow.Flow

interface LifeItemRepository {
    fun getAllItems(): Flow<List<LifeItem>>
    fun getItemsByTemplate(templateId: Long): Flow<List<LifeItem>>
    fun getItemsByTemplateAndStatus(templateId: Long, status: String): Flow<List<LifeItem>>
    suspend fun getItemById(id: Long): LifeItem?
    fun getItemByIdFlow(id: Long): Flow<LifeItem?>
    fun getActiveItemsByTemplate(templateId: Long, limit: Int): Flow<List<LifeItem>>
    fun getItemCountByTemplate(templateId: Long): Flow<Int>
    fun getTotalItemCount(): Flow<Int>
    fun getPagedItemsByTemplate(templateId: Long): Flow<PagingData<LifeItem>>
    fun getPagedAllItems(): Flow<PagingData<LifeItem>>
    suspend fun search(query: String): List<LifeItem>
    suspend fun insertItem(item: LifeItem): Long
    suspend fun updateItem(item: LifeItem)
    suspend fun updateStatus(id: Long, status: String)
    suspend fun updateFieldsData(id: Long, fieldsData: String)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun softDelete(id: Long)
    suspend fun restore(id: Long)
    suspend fun hardDelete(id: Long)
}
