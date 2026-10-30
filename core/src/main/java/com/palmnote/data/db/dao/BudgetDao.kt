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

    /** 演示预算数（SEED / USER 都算），供清理前快照计数。 */
    @Query("SELECT COUNT(*) FROM budgets WHERE demoOrigin != 'NONE'")
    suspend fun countDemoBudgets(): Int

    /** 关闭演示：清掉演示预算（SEED / USER 都算），真实预算（NONE）不动。 */
    @Query("DELETE FROM budgets WHERE demoOrigin != 'NONE'")
    suspend fun clearDemoBudgets()

    /** 小组件直连 DAO 用：按演示口径取月预算（演示看 SEED/USER，真实看 NONE）。 */
    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth AND (demoOrigin != 'NONE') = :includeDemo")
    suspend fun getBudgetByMonthScoped(yearMonth: String, includeDemo: Boolean): Budget?

    @Query("SELECT * FROM budgets WHERE (demoOrigin != 'NONE') = :includeDemo ORDER BY yearMonth DESC LIMIT 1")
    suspend fun getLatestBudgetScoped(includeDemo: Boolean): Budget?
}
