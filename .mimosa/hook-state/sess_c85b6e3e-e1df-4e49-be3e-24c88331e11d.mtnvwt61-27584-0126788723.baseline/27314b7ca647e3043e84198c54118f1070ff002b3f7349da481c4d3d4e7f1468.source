package com.palmnote.domain.repository

import com.palmnote.data.db.entity.LifeMoment
import kotlinx.coroutines.flow.Flow

interface LifeMomentRepository {
    fun getRecentMoments(limit: Int): Flow<List<LifeMoment>>
    fun getAllMoments(): Flow<List<LifeMoment>>
    suspend fun getMomentById(id: Long): LifeMoment?
    suspend fun insertMoment(moment: LifeMoment): Long
    suspend fun updateMoment(moment: LifeMoment)
    suspend fun deleteMoment(id: Long)
    fun getMomentCount(): Flow<Int>
}
