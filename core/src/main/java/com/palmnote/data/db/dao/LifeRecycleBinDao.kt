package com.palmnote.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.palmnote.data.db.entity.LifeRecycleBin
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeRecycleBinDao {
    @Insert
    suspend fun insertAll(rows: List<LifeRecycleBin>)

    /**
     * 列表只显示可整组恢复的顶层行：无父、或父行不在**同一次删除**的站内快照里
     * （批次用 [LifeRecycleBin.deletedAt] 区分——单独删掉的子任务不因父条目后来也被删
     * 而被并进父的组，它的恢复/彻底删除始终独立可见可操作）。
     */
    @Query(
        """
        SELECT * FROM life_recycle_bin
        WHERE parentId IS NULL OR NOT EXISTS (
            SELECT 1 FROM life_recycle_bin p
            WHERE p.originalId = life_recycle_bin.parentId AND p.deletedAt = life_recycle_bin.deletedAt
        )
        ORDER BY deletedAt DESC
        """
    )
    fun getTopLevel(): Flow<List<LifeRecycleBin>>

    @Query("SELECT * FROM life_recycle_bin WHERE id = :id")
    suspend fun getById(id: Long): LifeRecycleBin?

    /** 同一次删除里父条目的子任务行（批次由调用方取父行的 deletedAt 传入）。 */
    @Query("SELECT * FROM life_recycle_bin WHERE parentId = :parentOriginalId AND deletedAt = :batchDeletedAt")
    suspend fun getChildrenOf(parentOriginalId: Long, batchDeletedAt: Long): List<LifeRecycleBin>

    @Query("DELETE FROM life_recycle_bin WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清掉一次删除的整组（父 + 同批次子任务）；[batchDeletedAt] 取父行的 deletedAt。 */
    @Query(
        """
        DELETE FROM life_recycle_bin
        WHERE (originalId = :originalId OR parentId = :originalId) AND deletedAt = :batchDeletedAt
        """
    )
    suspend fun deleteByOriginalId(originalId: Long, batchDeletedAt: Long)

    @Query("DELETE FROM life_recycle_bin WHERE templateId = :templateId")
    suspend fun deleteByTemplate(templateId: Long)

    @Query("DELETE FROM life_recycle_bin")
    suspend fun deleteAll()

    /** 演示数据清理：随原行进站的示例行按 meta 清掉（口径同 bills/assets 回收站）。 */
    @Query("DELETE FROM life_recycle_bin WHERE meta = :demoMeta")
    suspend fun clearDemoRows(demoMeta: String)

    @Query("SELECT COUNT(*) FROM life_recycle_bin WHERE meta = :demoMeta")
    suspend fun countDemoRows(demoMeta: String): Int
}
