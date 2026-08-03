package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Anniversary
import kotlinx.coroutines.flow.Flow

@Dao
interface AnniversaryDao {
    @Query("SELECT * FROM anniversaries ORDER BY isPinned DESC, solarDate ASC")
    fun getAllAnniversaries(): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE id = :id")
    suspend fun getAnniversaryById(id: Long): Anniversary?

    @Query("SELECT * FROM anniversaries WHERE type = :type ORDER BY solarDate ASC")
    fun getAnniversariesByType(type: String): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE personName LIKE '%' || :name || '%'")
    fun searchByPerson(name: String): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE notificationEnabled = 1")
    fun getNotificationEnabledAnniversaries(): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE isPinned = 1 ORDER BY solarDate ASC")
    fun getPinnedAnniversaries(): Flow<List<Anniversary>>


    @Query("SELECT COUNT(*) FROM anniversaries WHERE 1=1")
    fun getAnniversaryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM anniversaries WHERE type = :type")
    fun getCountByType(type: String): Flow<Int>

    @Query("SELECT * FROM anniversaries WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR personName LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    suspend fun search(query: String): List<Anniversary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnniversary(anniversary: Anniversary): Long

    @Update
    suspend fun updateAnniversary(anniversary: Anniversary)

    @Query("UPDATE anniversaries SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM anniversaries WHERE id = :id")
    suspend fun deleteAnniversary(id: Long)

    @Query("DELETE FROM anniversaries")
    suspend fun deleteAll()
}
