package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.MoodDiary
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDiaryDao {
    @Query("SELECT * FROM mood_diaries ORDER BY date DESC")
    fun getAllMoodDiaries(): Flow<List<MoodDiary>>

    @Query("SELECT * FROM mood_diaries WHERE date >= :dayStart AND date < :dayEnd LIMIT 1")
    fun getMoodByDate(dayStart: Long, dayEnd: Long): Flow<List<MoodDiary>>

    @Query("SELECT * FROM mood_diaries WHERE date >= :weekStart AND date <= :weekEnd ORDER BY date ASC")
    fun getMoodByWeek(weekStart: Long, weekEnd: Long): Flow<List<MoodDiary>>

    @Query("SELECT * FROM mood_diaries WHERE date >= :dayStart AND date < :dayEnd LIMIT 1")
    suspend fun getMoodByDateOnce(dayStart: Long, dayEnd: Long): MoodDiary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoodDiary(moodDiary: MoodDiary): Long

    @Update
    suspend fun updateMoodDiary(moodDiary: MoodDiary)

    @Query("DELETE FROM mood_diaries WHERE id = :id")
    suspend fun deleteMoodDiary(id: Long)

    @Query("DELETE FROM mood_diaries")
    suspend fun deleteAll()
}
