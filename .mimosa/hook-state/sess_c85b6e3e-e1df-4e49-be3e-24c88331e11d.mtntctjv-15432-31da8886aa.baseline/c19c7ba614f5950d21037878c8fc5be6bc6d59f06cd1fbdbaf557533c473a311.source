package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.TodoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoItemDao {
    @Query("SELECT * FROM todo_items ORDER BY sortOrder ASC, dueDate ASC, createdAt DESC")
    fun getAllTodos(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getTodoById(id: Long): TodoItem?

    @Query("SELECT * FROM todo_items WHERE isCompleted = 0 ORDER BY sortOrder ASC, dueDate ASC, createdAt DESC")
    fun getIncompleteTodos(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE isCompleted = 1 ORDER BY updatedAt DESC")
    fun getCompletedTodos(): Flow<List<TodoItem>>

    @Query("SELECT COUNT(*) FROM todo_items WHERE isCompleted = 0")
    fun getIncompleteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM todo_items WHERE 1=1")
    fun getTotalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItem): Long

    @Update
    suspend fun updateTodo(todo: TodoItem)

    @Query("UPDATE todo_items SET isCompleted = :isCompleted, updatedAt = :now WHERE id = :id")
    suspend fun setCompleted(id: Long, isCompleted: Boolean, now: Long = System.currentTimeMillis())




    @Query("UPDATE todo_items SET sortOrder = :sortOrder, updatedAt = :now WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int, now: Long = System.currentTimeMillis())

    @Query("""
        SELECT * FROM todo_items
       WHERE isCompleted = 0
          AND (dueDate IS NULL OR dueDate <= :endOfDay)
        ORDER BY
          CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
          dueDate ASC, createdAt ASC
    """)
    fun getTodayTodos(endOfDay: Long): Flow<List<TodoItem>>

    @Query("""
        SELECT * FROM todo_items
       WHERE isCompleted = 0 AND planId = :planId
        ORDER BY
          CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
          dueDate ASC, createdAt ASC
    """)
    fun getTodosByPlanId(planId: Long): Flow<List<TodoItem>>

    @Query("UPDATE todo_items SET isCompleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun batchComplete(ids: List<Long>, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM todo_items WHERE id IN (:ids)")
    suspend fun batchSoftDelete(ids: List<Long>)

    @Query("UPDATE todo_items SET isCompleted = 0, updatedAt = :now WHERE id IN (:ids)")
    suspend fun batchUncomplete(ids: List<Long>, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM todo_items")
    suspend fun deleteAll()
}
