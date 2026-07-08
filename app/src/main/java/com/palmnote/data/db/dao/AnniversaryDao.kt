package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Anniversary
import kotlinx.coroutines.flow.Flow

@Dao
interface AnniversaryDao {
    @Query("SELECT * FROM anniversaries WHERE isDeleted = 0 ORDER BY isPinned DESC, solarDate ASC")
    fun getAllAnniversaries(): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE id = :id AND isDeleted = 0")
    suspend fun getAnniversaryById(id: Long): Anniversary?

    @Query("SELECT * FROM anniversaries WHERE type = :type AND isDeleted = 0 ORDER BY solarDate ASC")
    fun getAnniversariesByType(type: String): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE personName LIKE '%' || :name || '%' AND isDeleted = 0")
    fun searchByPerson(name: String): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE notificationEnabled = 1 AND isDeleted = 0")
    fun getNotificationEnabledAnniversaries(): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE isPinned = 1 AND isDeleted = 0 ORDER BY solarDate ASC")
    fun getPinnedAnniversaries(): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedAnniversaries(): Flow<List<Anniversary>>

    @Query("SELECT COUNT(*) FROM anniversaries WHERE isDeleted = 0")
    fun getAnniversaryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM anniversaries WHERE type = :type AND isDeleted = 0")
    fun getCountByType(type: String): Flow<Int>

    @Query("SELECT * FROM anniversaries WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR personName LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    suspend fun search(query: String): List<Anniversary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnniversary(anniversary: Anniversary): Long

    @Update
    suspend fun updateAnniversary(anniversary: Anniversary)

    @Query("UPDATE anniversaries SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE anniversaries SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteAnniversary(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE anniversaries SET isDeleted = 0, deletedAt = null, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restoreAnniversary(id: Long, restoredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM anniversaries WHERE id = :id")
    suspend fun hardDeleteAnniversary(id: Long)

    @Query("DELETE FROM anniversaries")
    suspend fun deleteAll()
}
