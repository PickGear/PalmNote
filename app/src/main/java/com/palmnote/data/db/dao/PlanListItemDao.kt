package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.PlanListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanListItemDao {
    @Query("SELECT * FROM plan_list_items WHERE listId = :listId ORDER BY sortOrder ASC, createdAt ASC")
    fun getItemsByListId(listId: Long): Flow<List<PlanListItem>>

    @Query("SELECT * FROM plan_list_items WHERE id = :id")
    suspend fun getItemById(id: Long): PlanListItem?

    @Query("SELECT COUNT(*) FROM plan_list_items WHERE listId = :listId")
    fun getItemCount(listId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM plan_list_items WHERE listId = :listId AND isCompleted = 1")
    fun getCompletedItemCount(listId: Long): Flow<Int>

    @Query("SELECT SUM(unitPrice * quantity) FROM plan_list_items WHERE listId = :listId AND isCompleted = 1")
    fun getCompletedTotalCost(listId: Long): Flow<Long?>

    @Query("SELECT SUM(unitPrice * quantity) FROM plan_list_items WHERE listId = :listId")
    fun getTotalCost(listId: Long): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PlanListItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlanListItem>)

    @Update
    suspend fun updateItem(item: PlanListItem)

    @Query("UPDATE plan_list_items SET isCompleted = :isCompleted, updatedAt = :now WHERE id = :id")
    suspend fun setItemCompleted(id: Long, isCompleted: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM plan_list_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM plan_list_items WHERE listId = :listId")
    suspend fun deleteItemsByListId(listId: Long)

    @Query("UPDATE plan_list_items SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int, now: Long = System.currentTimeMillis())
}
