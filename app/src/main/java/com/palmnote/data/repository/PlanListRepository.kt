package com.palmnote.data.repository

import com.palmnote.data.db.dao.PlanListDao
import com.palmnote.data.db.dao.PlanListItemDao
import com.palmnote.data.db.entity.PlanList
import com.palmnote.data.db.entity.PlanListItem
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.PlanListRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanListRepository @Inject constructor(
    private val planListDao: PlanListDao,
    private val planListItemDao: PlanListItemDao
) : PlanListRepository {
    override fun getAllLists(): Flow<List<PlanList>> = planListDao.getAllLists()

    override fun getActiveLists(): Flow<List<PlanList>> = planListDao.getActiveLists()

    override fun getCompletedLists(): Flow<List<PlanList>> = planListDao.getCompletedLists()

    override suspend fun getListById(id: Long): PlanList? = planListDao.getListById(id)

    override fun getItemsByListId(listId: Long): Flow<List<PlanListItem>> = planListItemDao.getItemsByListId(listId)

    override suspend fun getItemById(id: Long): PlanListItem? = planListItemDao.getItemById(id)

    override fun getItemCount(listId: Long): Flow<Int> = planListItemDao.getItemCount(listId)

    override fun getCompletedItemCount(listId: Long): Flow<Int> = planListItemDao.getCompletedItemCount(listId)

    override fun getTotalCost(listId: Long): Flow<Double?> = planListItemDao.getTotalCost(listId)

    override fun getCompletedTotalCost(listId: Long): Flow<Double?> = planListItemDao.getCompletedTotalCost(listId)

    override suspend fun insertList(list: PlanList): Long = planListDao.insertList(list)

    override suspend fun updateList(list: PlanList) = planListDao.updateList(list)

    override suspend fun setListCompleted(id: Long, isCompleted: Boolean) = planListDao.setCompleted(id, isCompleted)

    override suspend fun softDeleteList(id: Long) = planListDao.softDeleteList(id)

    override suspend fun insertItem(item: PlanListItem): Long = planListItemDao.insertItem(item)

    override suspend fun insertItems(items: List<PlanListItem>) = planListItemDao.insertItems(items)

    override suspend fun updateItem(item: PlanListItem) = planListItemDao.updateItem(item)

    override suspend fun setItemCompleted(id: Long, isCompleted: Boolean) = planListItemDao.setItemCompleted(id, isCompleted)

    override suspend fun deleteItem(id: Long) = planListItemDao.deleteItem(id)

    override suspend fun deleteItemsByListId(listId: Long) = planListItemDao.deleteItemsByListId(listId)
}
