package com.palmnote.domain.repository

import com.palmnote.data.db.entity.MoodDiary
import kotlinx.coroutines.flow.Flow

interface MoodDiaryRepository {
    fun getAllMoodDiaries(): Flow<List<MoodDiary>>
    fun getMoodByDate(dayStart: Long, dayEnd: Long): Flow<List<MoodDiary>>
    fun getMoodByWeek(weekStart: Long, weekEnd: Long): Flow<List<MoodDiary>>
    suspend fun getMoodByDateOnce(dayStart: Long, dayEnd: Long): MoodDiary?
    suspend fun insertMoodDiary(moodDiary: MoodDiary): Long
    suspend fun updateMoodDiary(moodDiary: MoodDiary)
    suspend fun deleteMoodDiary(id: Long)
}
