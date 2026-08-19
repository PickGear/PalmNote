package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.LifeMoment
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeMomentDao {
    @Query("SELECT * FROM life_moments ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun getRecentMoments(limit: Int = 3): Flow<List<LifeMoment>>

    @Query("SELECT * FROM life_moments ORDER BY date DESC, createdAt DESC")
    fun getAllMoments(): Flow<List<LifeMoment>>

    @Query("SELECT * FROM life_moments WHERE id = :id")
    suspend fun getMomentById(id: Long): LifeMoment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoment(moment: LifeMoment): Long

    @Update
    suspend fun updateMoment(moment: LifeMoment)


    @Query("SELECT COUNT(*) FROM life_moments WHERE 1=1")
    fun getMomentCount(): Flow<Int>

    @Query("DELETE FROM life_moments WHERE id = :id")
    suspend fun deleteLifeMoment(id: Long)

    @Query("DELETE FROM life_moments")
    suspend fun deleteAll()
}
