package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY yearMonth DESC")
    fun getAllBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    suspend fun getBudgetByMonth(yearMonth: String): Budget?

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun getBudgetByMonthFlow(yearMonth: String): Flow<Budget?>

    @Query("SELECT * FROM budgets ORDER BY yearMonth DESC LIMIT 1")
    fun getLatestBudget(): Flow<Budget?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetById(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
