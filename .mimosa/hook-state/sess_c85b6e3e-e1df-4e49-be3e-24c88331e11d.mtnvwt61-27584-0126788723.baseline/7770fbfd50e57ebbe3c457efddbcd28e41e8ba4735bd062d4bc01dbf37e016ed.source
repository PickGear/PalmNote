package com.palmnote.data.repository
import javax.inject.Inject

import com.palmnote.data.db.dao.MoodDiaryDao
import com.palmnote.data.db.entity.MoodDiary
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.MoodDiaryRepository
class MoodDiaryRepositoryImpl @Inject constructor(
    private val moodDiaryDao: MoodDiaryDao
) : MoodDiaryRepository {
    override fun getAllMoodDiaries(): Flow<List<MoodDiary>> = moodDiaryDao.getAllMoodDiaries()

    override fun getMoodByDate(dayStart: Long, dayEnd: Long): Flow<List<MoodDiary>> = moodDiaryDao.getMoodByDate(dayStart, dayEnd)

    override fun getMoodByWeek(weekStart: Long, weekEnd: Long): Flow<List<MoodDiary>> = moodDiaryDao.getMoodByWeek(weekStart, weekEnd)

    override suspend fun getMoodByDateOnce(dayStart: Long, dayEnd: Long): MoodDiary? = moodDiaryDao.getMoodByDateOnce(dayStart, dayEnd)

    override suspend fun insertMoodDiary(moodDiary: MoodDiary): Long = moodDiaryDao.insertMoodDiary(moodDiary)

    override suspend fun updateMoodDiary(moodDiary: MoodDiary) = moodDiaryDao.updateMoodDiary(moodDiary)

    override suspend fun deleteMoodDiary(id: Long) = moodDiaryDao.deleteMoodDiary(id)
}
