package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.TodoItem
import com.palmnote.data.db.entity.LifeMoment
import com.palmnote.data.db.entity.MoodDiary
import kotlinx.coroutines.flow.Flow

@Dao
interface LegacyDao {
    // TodoItem queries
    @Query("SELECT * FROM todo_items WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllTodos(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getTodoById(id: Long): TodoItem?

    @Query("SELECT * FROM todo_items WHERE lifeItemId = :lifeItemId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getTodosByLifeItem(lifeItemId: Long): Flow<List<TodoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItem): Long

    @Update
    suspend fun updateTodo(todo: TodoItem)

    @Query("UPDATE todo_items SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteTodo(id: Long, deletedAt: Long = System.currentTimeMillis())

    // LifeMoment queries
    @Query("SELECT * FROM life_moments WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllMoments(): Flow<List<LifeMoment>>

    @Query("SELECT * FROM life_moments WHERE id = :id")
    suspend fun getMomentById(id: Long): LifeMoment?

    @Query("SELECT * FROM life_moments WHERE lifeItemId = :lifeItemId AND isDeleted = 0 ORDER BY date DESC")
    fun getMomentsByLifeItem(lifeItemId: Long): Flow<List<LifeMoment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoment(moment: LifeMoment): Long

    @Update
    suspend fun updateMoment(moment: LifeMoment)

    @Query("UPDATE life_moments SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteMoment(id: Long, deletedAt: Long = System.currentTimeMillis())

    // MoodDiary queries
    @Query("SELECT * FROM mood_diaries ORDER BY date DESC")
    fun getAllMoodDiaries(): Flow<List<MoodDiary>>

    @Query("SELECT * FROM mood_diaries WHERE id = :id")
    suspend fun getMoodDiaryById(id: Long): MoodDiary?

    @Query("SELECT * FROM mood_diaries WHERE lifeItemId = :lifeItemId ORDER BY date DESC")
    fun getMoodDiariesByLifeItem(lifeItemId: Long): Flow<List<MoodDiary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoodDiary(diary: MoodDiary): Long

    @Update
    suspend fun updateMoodDiary(diary: MoodDiary)

    @Query("DELETE FROM mood_diaries WHERE id = :id")
    suspend fun deleteMoodDiary(id: Long)
}
