package com.palmnote.domain.repository

import com.palmnote.data.db.entity.TodoItem
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    fun getAllTodos(): Flow<List<TodoItem>>
    suspend fun getTodoById(id: Long): TodoItem?
    suspend fun insertTodo(todo: TodoItem): Long
    suspend fun updateTodo(todo: TodoItem)
    suspend fun softDeleteTodo(id: Long)
    fun getSubtodos(parentId: Long): Flow<List<TodoItem>>
}
