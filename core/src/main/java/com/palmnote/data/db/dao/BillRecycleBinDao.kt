package com.palmnote.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.palmnote.data.db.entity.BillRecycleBin
import kotlinx.coroutines.flow.Flow

@Dao
interface BillRecycleBinDao {
    @Insert
    suspend fun insert(item: BillRecycleBin)

    @Query("SELECT * FROM bills_recycle_bin ORDER BY deletedAt DESC")
    fun getAll(): Flow<List<BillRecycleBin>>

    @Query("SELECT * FROM bills_recycle_bin WHERE id = :id")
    suspend fun getById(id: Long): BillRecycleBin?

    @Query("DELETE FROM bills_recycle_bin WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bills_recycle_bin")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM bills_recycle_bin")
    fun getCount(): Flow<Int>

    /** 演示数据清理：标记行 + 示例账本里的账单（含用户在演示期自建的，随账本删除）。 */
    @Query("DELETE FROM bills_recycle_bin WHERE isDemo = 1 OR accountBookId IN (SELECT id FROM account_books WHERE isDemo = 1)")
    suspend fun clearDemoBills()

    /** 当前示例行数（关演示反馈用）。 */
    @Query("SELECT COUNT(*) FROM bills_recycle_bin WHERE isDemo = 1")
    suspend fun countDemoBills(): Int
}
