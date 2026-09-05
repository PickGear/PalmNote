package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Moment
import kotlinx.coroutines.flow.Flow

interface MomentRepository {
    fun getAllMoments(): Flow<List<Moment>>
    suspend fun getMomentById(id: Long): Moment?
    fun getMomentsByAsset(assetId: Long): Flow<List<Moment>>
    fun getMomentsByAnniversary(anniversaryId: Long): Flow<List<Moment>>
    fun getMomentsByGoal(goalId: Long): Flow<List<Moment>>
    fun getMomentsByTag(tag: String): Flow<List<Moment>>
    fun getMomentCount(): Flow<Int>
    suspend fun insertMoment(moment: Moment): Long
    suspend fun updateMoment(moment: Moment)
    suspend fun setFavorite(id: Long, isFavorite: Boolean)
    suspend fun deleteMoment(id: Long)
    suspend fun search(query: String): List<Moment>
}
