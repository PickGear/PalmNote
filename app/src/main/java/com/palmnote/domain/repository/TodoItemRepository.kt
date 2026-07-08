package com.palmnote.domain.repository

import com.palmnote.data.db.entity.TodoItem
import kotlinx.coroutines.flow.Flow

interface TodoItemRepository {
    fun getAllTodos(): Flow<List<TodoItem>>
    fun getIncompleteTodos(): Flow<List<TodoItem>>
    fun getCompletedTodos(): Flow<List<TodoItem>>
    fun getIncompleteCount(): Flow<Int>
    fun getTotalCount(): Flow<Int>
    suspend fun getTodoById(id: Long): TodoItem?
    suspend fun insertTodo(todo: TodoItem): Long
    suspend fun updateTodo(todo: TodoItem)
    suspend fun setCompleted(id: Long, isCompleted: Boolean)
    suspend fun softDeleteTodo(id: Long)
    suspend fun restoreTodo(id: Long)
    suspend fun batchComplete(ids: List<Long>)
    suspend fun batchSoftDelete(ids: List<Long>)
    suspend fun batchUncomplete(ids: List<Long>)
    fun getTodayTodos(endOfDay: Long): Flow<List<TodoItem>>
    fun getTodosByPlanId(planId: Long): Flow<List<TodoItem>>
}
