package com.palmnote.data.repository

import com.palmnote.data.db.dao.TodoItemDao
import com.palmnote.data.db.entity.TodoItem
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.TodoItemRepository
class TodoItemRepository(
    private val todoItemDao: TodoItemDao
) : TodoItemRepository {
    override fun getAllTodos(): Flow<List<TodoItem>> = todoItemDao.getAllTodos()

    override fun getIncompleteTodos(): Flow<List<TodoItem>> = todoItemDao.getIncompleteTodos()

    override fun getCompletedTodos(): Flow<List<TodoItem>> = todoItemDao.getCompletedTodos()

    override fun getIncompleteCount(): Flow<Int> = todoItemDao.getIncompleteCount()

    override fun getTotalCount(): Flow<Int> = todoItemDao.getTotalCount()

    override suspend fun getTodoById(id: Long): TodoItem? = todoItemDao.getTodoById(id)

    override suspend fun insertTodo(todo: TodoItem): Long = todoItemDao.insertTodo(todo)

    override suspend fun updateTodo(todo: TodoItem) = todoItemDao.updateTodo(todo)

    override suspend fun setCompleted(id: Long, isCompleted: Boolean) = todoItemDao.setCompleted(id, isCompleted)

    override suspend fun softDeleteTodo(id: Long) = todoItemDao.softDeleteTodo(id)

    override suspend fun restoreTodo(id: Long) = todoItemDao.restoreTodo(id)

    override suspend fun batchComplete(ids: List<Long>) = todoItemDao.batchComplete(ids)

    override suspend fun batchSoftDelete(ids: List<Long>) = todoItemDao.batchSoftDelete(ids)

    override suspend fun batchUncomplete(ids: List<Long>) = todoItemDao.batchUncomplete(ids)

    override fun getTodayTodos(endOfDay: Long): Flow<List<TodoItem>> = todoItemDao.getTodayTodos(endOfDay)

    override fun getTodosByPlanId(planId: Long): Flow<List<TodoItem>> = todoItemDao.getTodosByPlanId(planId)
}
