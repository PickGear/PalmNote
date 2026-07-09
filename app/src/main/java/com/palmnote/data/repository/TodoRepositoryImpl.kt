package com.palmnote.data.repository

import com.palmnote.data.db.dao.LegacyDao
import com.palmnote.data.db.entity.TodoItem
import com.palmnote.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepositoryImpl @Inject constructor(
    private val dao: LegacyDao
) : TodoRepository {
    override fun getAllTodos(): Flow<List<TodoItem>> = dao.getAllTodos()

    override suspend fun getTodoById(id: Long): TodoItem? = dao.getTodoById(id)

    override suspend fun insertTodo(todo: TodoItem): Long = dao.insertTodo(todo)

    override suspend fun updateTodo(todo: TodoItem) = dao.updateTodo(todo)

    override suspend fun softDeleteTodo(id: Long) = dao.softDeleteTodo(id)

    override fun getSubtodos(parentId: Long): Flow<List<TodoItem>> = dao.getTodosByParentId(parentId)
}
