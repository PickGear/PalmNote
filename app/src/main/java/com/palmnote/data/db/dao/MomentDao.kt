package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Moment
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentDao {
    @Query("SELECT * FROM moments ORDER BY isPinned DESC, timestamp DESC")
    fun getAllMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE id = :id")
    suspend fun getMomentById(id: Long): Moment?

    @Query("SELECT * FROM moments WHERE linkedAssetId = :assetId ORDER BY timestamp DESC")
    fun getMomentsByAsset(assetId: Long): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE linkedAnniversaryId = :anniversaryId ORDER BY timestamp DESC")
    fun getMomentsByAnniversary(anniversaryId: Long): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE linkedGoalId = :goalId ORDER BY timestamp DESC")
    fun getMomentsByGoal(goalId: Long): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE tags LIKE '%' || :tag || '%' ORDER BY timestamp DESC")
    fun getMomentsByTag(tag: String): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE category = :category ORDER BY timestamp DESC")
    fun getMomentsByCategory(category: String): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE mood = :mood ORDER BY timestamp DESC")
    fun getMomentsByMood(mood: String): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE isPinned = 1 ORDER BY timestamp DESC")
    fun getPinnedMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchMoments(query: String): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE timestamp >= :startDate AND timestamp <= :endDate ORDER BY timestamp DESC")
    fun getMomentsByDateRange(startDate: Long, endDate: Long): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE location LIKE '%' || :location || '%' ORDER BY timestamp DESC")
    fun getMomentsByLocation(location: String): Flow<List<Moment>>

    @Query("SELECT COUNT(*) FROM moments WHERE 1=1")
    fun getMomentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM moments WHERE mood = :mood")
    fun getMoodCount(mood: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM moments WHERE isFavorite = 1")
    fun getFavoriteCount(): Flow<Int>


    @Query("SELECT DISTINCT category FROM moments WHERE category != '' ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT tags FROM moments WHERE tags != ''")
    fun getAllTags(): Flow<List<String>>

    @Query("SELECT * FROM moments WHERE (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    suspend fun search(query: String): List<Moment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoment(moment: Moment): Long

    @Update
    suspend fun updateMoment(moment: Moment)

    @Query("UPDATE moments SET isFavorite = :isFavorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE moments SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long = System.currentTimeMillis())




    @Query("DELETE FROM moments WHERE id = :id")
    suspend fun deleteMoment(id: Long)

    @Query("DELETE FROM moments")
    suspend fun deleteAll()
}
