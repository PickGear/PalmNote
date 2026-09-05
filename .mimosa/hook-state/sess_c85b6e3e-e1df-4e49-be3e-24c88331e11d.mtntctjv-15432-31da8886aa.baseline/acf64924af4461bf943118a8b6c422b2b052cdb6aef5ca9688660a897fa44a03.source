package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.PlanList
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanListDao {
    @Query("SELECT * FROM plan_lists ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllLists(): Flow<List<PlanList>>

    @Query("SELECT * FROM plan_lists WHERE id = :id")
    suspend fun getListById(id: Long): PlanList?

    @Query("SELECT * FROM plan_lists WHERE isCompleted = 0 ORDER BY sortOrder ASC, createdAt DESC")
    fun getActiveLists(): Flow<List<PlanList>>

    @Query("SELECT * FROM plan_lists WHERE isCompleted = 1 ORDER BY updatedAt DESC")
    fun getCompletedLists(): Flow<List<PlanList>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: PlanList): Long

    @Update
    suspend fun updateList(list: PlanList)

    @Query("UPDATE plan_lists SET isCompleted = :isCompleted, updatedAt = :now WHERE id = :id")
    suspend fun setCompleted(id: Long, isCompleted: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM plan_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

}
