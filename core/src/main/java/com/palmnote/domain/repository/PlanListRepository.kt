package com.palmnote.domain.repository

import com.palmnote.data.db.entity.PlanList
import com.palmnote.data.db.entity.PlanListItem
import kotlinx.coroutines.flow.Flow

interface PlanListRepository {
    fun getAllLists(): Flow<List<PlanList>>
    fun getActiveLists(): Flow<List<PlanList>>
    fun getCompletedLists(): Flow<List<PlanList>>
    suspend fun getListById(id: Long): PlanList?
    fun getItemsByListId(listId: Long): Flow<List<PlanListItem>>
    suspend fun getItemById(id: Long): PlanListItem?
    fun getItemCount(listId: Long): Flow<Int>
    fun getCompletedItemCount(listId: Long): Flow<Int>
    fun getTotalCost(listId: Long): Flow<Long?>
    fun getCompletedTotalCost(listId: Long): Flow<Long?>
    suspend fun insertList(list: PlanList): Long
    suspend fun updateList(list: PlanList)
    suspend fun setListCompleted(id: Long, isCompleted: Boolean)
    suspend fun deleteList(id: Long)
    suspend fun insertItem(item: PlanListItem): Long
    suspend fun insertItems(items: List<PlanListItem>)
    suspend fun updateItem(item: PlanListItem)
    suspend fun setItemCompleted(id: Long, isCompleted: Boolean)
    suspend fun deleteItem(id: Long)
    suspend fun deleteItemsByListId(listId: Long)
}
