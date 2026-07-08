package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Countdown
import kotlinx.coroutines.flow.Flow

interface CountdownRepository {
    fun getUpcomingCountdowns(): Flow<List<Countdown>>
    suspend fun getCountdownById(id: Long): Countdown?
    suspend fun insertCountdown(countdown: Countdown): Long
    suspend fun updateCountdown(countdown: Countdown)
    suspend fun softDeleteCountdown(id: Long)
}
