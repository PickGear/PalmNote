package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY period ASC, scope ASC, id ASC")
    fun getAllBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsOnce(): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getBudgetById(id: Long): Budget?

    /**
     * 找同维度的预算模板（演示口径由 [includeDemo] 带上）。
     *
     * 「同维度」= 周期 + 范围 + 账本 + 分类。可空维度用 `IS NULL` 成对比较，
     * 不能写成 `bookId = :bookId`（两边都是 NULL 时结果是 NULL，永远不匹配）。
     */
    @Query(
        "SELECT * FROM budgets " +
            "WHERE period = :period AND scope = :scope " +
            "AND ((:bookId IS NULL AND bookId IS NULL) OR bookId = :bookId) " +
            "AND ((:category IS NULL AND category IS NULL) OR category = :category) " +
            "AND (demoOrigin != 'NONE') = :includeDemo " +
            "LIMIT 1"
    )
    suspend fun findBudget(
        period: String,
        scope: String,
        bookId: Long?,
        category: String?,
        includeDemo: Boolean
    ): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetById(id: Long)

    /**
     * 分类改名时同步预算：CATEGORY 预算存的是**分类名**而不是 id，
     * 不跟着改会让 `spentOf` 按旧名过滤、进度恒为 0，且列表仍显示旧名。
     */
    @Query("UPDATE budgets SET category = :newName WHERE scope = 'CATEGORY' AND category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String)

    /** 账本删除后清掉指向它的 BOOK 预算，别留一条永远算不出数的规则。 */
    @Query("DELETE FROM budgets WHERE scope = 'BOOK' AND bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)

    /**
     * 分类删除后清掉指向它的 CATEGORY 预算（与 [deleteByBook] 对称）。
     *
     * 演示口径由 [includeDemo] 带上（与 [findBudget] 同款）：真实模式只清 `NONE`，
     * 演示模式只清 `SEED` / `USER`。不隔离的后果是演示期删分类会把用户的真实预算
     * 一起物理删掉，关演示后凭空消失且不可恢复。
     *
     * 不清理的后果不是「多一条记录」这么轻：`spentOf` 按分类名过滤，分类没了就恒为 0，
     * 这条预算会永远停在 0%、永远不提醒，却一直占着预算页。
     */
    @Query(
        "DELETE FROM budgets WHERE scope = 'CATEGORY' AND category = :category " +
            "AND (demoOrigin != 'NONE') = :includeDemo"
    )
    suspend fun deleteByCategory(category: String, includeDemo: Boolean)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    /** 演示预算数（SEED / USER 都算），供清理前快照计数。 */
    @Query("SELECT COUNT(*) FROM budgets WHERE demoOrigin != 'NONE'")
    suspend fun countDemoBudgets(): Int

    /** 关闭演示：清掉演示预算（SEED / USER 都算），真实预算（NONE）不动。 */
    @Query("DELETE FROM budgets WHERE demoOrigin != 'NONE'")
    suspend fun clearDemoBudgets()

    /** 小组件直连 DAO 用：按演示口径取「全账本月度总预算」。 */
    @Query(
        "SELECT * FROM budgets WHERE period = 'MONTH' AND scope = 'TOTAL' " +
            "AND (demoOrigin != 'NONE') = :includeDemo LIMIT 1"
    )
    suspend fun getMonthlyTotalScoped(includeDemo: Boolean): Budget?

    /** 小组件直连 DAO 用：按演示口径取「某账本的月度预算」。 */
    @Query(
        "SELECT * FROM budgets WHERE period = 'MONTH' AND scope = 'BOOK' AND bookId = :bookId " +
            "AND (demoOrigin != 'NONE') = :includeDemo LIMIT 1"
    )
    suspend fun getMonthlyBookScoped(bookId: Long, includeDemo: Boolean): Budget?
}
