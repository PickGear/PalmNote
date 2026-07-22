package com.palmnote.data.repository

import com.palmnote.data.db.dao.CountdownDao
import com.palmnote.data.db.entity.Countdown
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.CountdownRepository
class CountdownRepository(
    private val countdownDao: CountdownDao
) : CountdownRepository {
    override fun getUpcomingCountdowns(): Flow<List<Countdown>> = countdownDao.getUpcomingCountdowns()

    override suspend fun getCountdownById(id: Long): Countdown? = countdownDao.getCountdownById(id)

    override suspend fun insertCountdown(countdown: Countdown): Long = countdownDao.insertCountdown(countdown)

    override suspend fun updateCountdown(countdown: Countdown) = countdownDao.updateCountdown(countdown)

    override suspend fun softDeleteCountdown(id: Long) = countdownDao.softDeleteCountdown(id)
}
