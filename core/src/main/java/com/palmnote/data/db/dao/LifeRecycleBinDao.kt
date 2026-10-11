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

    /** 列表只显示可整组恢复的顶层行：无父、或父行不在站内（单独删掉的子任务也要能看见、能恢复）。 */
    @Query(
        """
        SELECT * FROM life_recycle_bin
        WHERE parentId IS NULL OR parentId NOT IN (SELECT originalId FROM life_recycle_bin)
        ORDER BY deletedAt DESC
        """
    )
    fun getTopLevel(): Flow<List<LifeRecycleBin>>

    @Query("SELECT * FROM life_recycle_bin WHERE id = :id")
    suspend fun getById(id: Long): LifeRecycleBin?

    @Query("SELECT * FROM life_recycle_bin WHERE parentId = :parentOriginalId")
    suspend fun getChildrenOf(parentOriginalId: Long): List<LifeRecycleBin>

    @Query("DELETE FROM life_recycle_bin WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM life_recycle_bin WHERE originalId = :originalId OR parentId = :originalId")
    suspend fun deleteByOriginalId(originalId: Long)

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
