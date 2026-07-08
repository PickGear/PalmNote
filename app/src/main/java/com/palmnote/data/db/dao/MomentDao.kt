package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Moment
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentDao {
    @Query("SELECT * FROM moments WHERE isDeleted = 0 ORDER BY isPinned DESC, timestamp DESC")
    fun getAllMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE id = :id AND isDeleted = 0")
    suspend fun getMomentById(id: Long): Moment?

    @Query("SELECT * FROM moments WHERE linkedAssetId = :assetId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMomentsByAsset(assetId: Long): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE linkedAnniversaryId = :anniversaryId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMomentsByAnniversary(anniversaryId: Long): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE linkedGoalId = :goalId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMomentsByGoal(goalId: Long): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE tags LIKE '%' || :tag || '%' AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMomentsByTag(tag: String): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE category = :category AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMomentsByCategory(category: String): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE mood = :mood AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMomentsByMood(mood: String): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE isFavorite = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getFavoriteMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE isPinned = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getPinnedMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') AND isDeleted = 0 ORDER BY timestamp DESC")
    fun searchMoments(query: String): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE timestamp >= :startDate AND timestamp <= :endDate AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMomentsByDateRange(startDate: Long, endDate: Long): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE location LIKE '%' || :location || '%' AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMomentsByLocation(location: String): Flow<List<Moment>>

    @Query("SELECT COUNT(*) FROM moments WHERE isDeleted = 0")
    fun getMomentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM moments WHERE mood = :mood AND isDeleted = 0")
    fun getMoodCount(mood: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM moments WHERE isFavorite = 1 AND isDeleted = 0")
    fun getFavoriteCount(): Flow<Int>

    @Query("SELECT * FROM moments WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedMoments(): Flow<List<Moment>>

    @Query("SELECT DISTINCT category FROM moments WHERE category != '' AND isDeleted = 0 ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT tags FROM moments WHERE tags != '' AND isDeleted = 0")
    fun getAllTags(): Flow<List<String>>

    @Query("SELECT * FROM moments WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    suspend fun search(query: String): List<Moment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoment(moment: Moment): Long

    @Update
    suspend fun updateMoment(moment: Moment)

    @Query("UPDATE moments SET isFavorite = :isFavorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE moments SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE moments SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteMoment(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE moments SET isDeleted = 0, deletedAt = null, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restoreMoment(id: Long, restoredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM moments WHERE id = :id")
    suspend fun hardDeleteMoment(id: Long)

    @Query("DELETE FROM moments")
    suspend fun deleteAll()
}
