package com.palmnote.domain.repository

import com.palmnote.data.db.entity.Anniversary
import kotlinx.coroutines.flow.Flow

interface AnniversaryRepository {
    fun getAllAnniversaries(): Flow<List<Anniversary>>
    suspend fun getAnniversaryById(id: Long): Anniversary?
    fun getAnniversariesByType(type: String): Flow<List<Anniversary>>
    fun getNotificationEnabledAnniversaries(): Flow<List<Anniversary>>
    fun getDeletedAnniversaries(): Flow<List<Anniversary>>
    fun getAnniversaryCount(): Flow<Int>
    suspend fun insertAnniversary(anniversary: Anniversary): Long
    suspend fun updateAnniversary(anniversary: Anniversary)
    suspend fun softDeleteAnniversary(id: Long)
    suspend fun restoreAnniversary(id: Long)
    suspend fun hardDeleteAnniversary(id: Long)
    suspend fun search(query: String): List<Anniversary>
}
