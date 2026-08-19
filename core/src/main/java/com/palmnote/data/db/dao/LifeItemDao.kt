package com.palmnote.data.db.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.palmnote.data.db.entity.LifeItem
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeItemDao {
    @Query("SELECT * FROM life_items ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<LifeItem>>

    @Query("SELECT * FROM life_items WHERE templateId = :templateId ORDER BY sortOrder, createdAt DESC")
    fun getItemsByTemplate(templateId: Long): Flow<List<LifeItem>>

    @Query("SELECT * FROM life_items WHERE templateId = :templateId AND status = :status ORDER BY sortOrder, createdAt DESC")
    fun getItemsByTemplateAndStatus(templateId: Long, status: String): Flow<List<LifeItem>>

    @Query("SELECT * FROM life_items WHERE id = :id")
    suspend fun getItemById(id: Long): LifeItem?

    @Query("SELECT * FROM life_items WHERE id = :id")
    fun getItemByIdFlow(id: Long): Flow<LifeItem?>

    @Query("SELECT * FROM life_items WHERE templateId = :templateId AND status = 'ACTIVE' ORDER BY sortOrder, createdAt DESC LIMIT :limit")
    fun getActiveItemsByTemplate(templateId: Long, limit: Int = 5): Flow<List<LifeItem>>

    @Query("SELECT COUNT(*) FROM life_items WHERE templateId = :templateId")
    fun getItemCountByTemplate(templateId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM life_items WHERE 1=1")
    fun getTotalItemCount(): Flow<Int>

    @Query("SELECT * FROM life_items ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllItemsPaged(offset: Int, limit: Int): List<LifeItem>

    @Query("SELECT * FROM life_items WHERE templateId = :templateId ORDER BY sortOrder, createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getItemsByTemplatePaged(templateId: Long, offset: Int, limit: Int): List<LifeItem>

    @Query("SELECT * FROM life_items WHERE (title LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%') ORDER BY updatedAt DESC LIMIT 50")
    suspend fun search(query: String): List<LifeItem>

    /** 全量搜索：标题 / 备注 / 字段值全文，实时 Flow。 */
    @Query("""
        SELECT * FROM life_items
        WHERE title LIKE '%' || :query || '%'
           OR note LIKE '%' || :query || '%'
           OR fieldsData LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC LIMIT 50
    """)
    fun searchItems(query: String): Flow<List<LifeItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: LifeItem): Long

    @Query("""
        UPDATE life_items SET 
            title = :title, fieldsData = :fieldsData, status = :status, 
            note = :note, sortOrder = :sortOrder, isFavorite = :isFavorite, 
            dueDate = :dueDate, dueTime = :dueTime, recurring = :recurring,
            recurringEndType = :recurringEndType, recurringEndCount = :recurringEndCount,
            recurringEndDate = :recurringEndDate, parentId = :parentId,
            remindAt = :remindAt, meta = :meta,
            updatedAt = :now 
        WHERE id = :id
    """)
    @Suppress("LongParameterList")
    suspend fun updateItem(
        id: Long,
        title: String,
        fieldsData: String,
        status: String,
        note: String,
        sortOrder: Int,
        isFavorite: Boolean,
        now: Long = System.currentTimeMillis(),
        dueDate: Long? = null,
        dueTime: Int? = null,
        recurring: String? = null,
        recurringEndType: String? = null,
        recurringEndCount: Int? = null,
        recurringEndDate: Long? = null,
        parentId: Long? = null,
        remindAt: Int? = null,
        meta: String? = null
    )

    @Query("UPDATE life_items SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE life_items SET fieldsData = :fieldsData, updatedAt = :now WHERE id = :id")
    suspend fun updateFieldsData(id: Long, fieldsData: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE life_items SET fieldsData = :fieldsData, dueDate = :dueDate, dueTime = :dueTime, updatedAt = :now WHERE id = :id")
    suspend fun updateFieldsDataWithSchedule(id: Long, fieldsData: String, dueDate: Long?, dueTime: Int?, now: Long = System.currentTimeMillis())

    @Query("UPDATE life_items SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long = System.currentTimeMillis())



    @Query("DELETE FROM life_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM life_items")
    suspend fun deleteAll()

    // ---- v8 执行列查询（今日看板 / 待办补集 / 计划子任务 / 逾期）----

    /** 今日看板聚合：dueDate 落在 [start, end) 内的条目（不含归档）。 */
    @Query("""
        SELECT * FROM life_items
        WHERE dueDate >= :start AND dueDate < :end AND status != 'ARCHIVED'
        ORDER BY dueTime ASC, sortOrder ASC, createdAt DESC
    """)
    fun getScheduledBetween(start: Long, end: Long): Flow<List<LifeItem>>

    /** 周历标记点：范围内 distinct 的 dueDate（毫秒，不含归档）。 */
    @Query("""
        SELECT DISTINCT dueDate FROM life_items
        WHERE dueDate IS NOT NULL AND dueDate >= :start AND dueDate < :end
        AND status != 'ARCHIVED'
    """)
    fun getDistinctDueDatesBetween(start: Long, end: Long): Flow<List<Long>>

    /** 待办卡补集：待办模板、非今日（逾期/未来/无日期）、未完成、非子任务。 */
    @Query("""
        SELECT * FROM life_items
        WHERE templateId = :todoTemplateId AND parentId IS NULL
        AND status != 'COMPLETED' AND status != 'ARCHIVED'
        AND (dueDate IS NULL OR dueDate < :todayStart OR dueDate >= :todayEnd)
        ORDER BY dueDate ASC, sortOrder ASC, createdAt DESC
    """)
    fun getTodoComplement(todayStart: Long, todayEnd: Long, todoTemplateId: Long): Flow<List<LifeItem>>

    /** 计划页子任务：parentId 关联。 */
    @Query("SELECT * FROM life_items WHERE parentId = :parentId ORDER BY sortOrder ASC, createdAt DESC")
    fun getSubtasks(parentId: Long): Flow<List<LifeItem>>

    /** 逾期反馈：dueDate < now 且未完成。 */
    @Query("""
        SELECT * FROM life_items
        WHERE dueDate IS NOT NULL AND dueDate < :now
        AND status != 'COMPLETED' AND status != 'ARCHIVED'
        ORDER BY dueDate ASC
    """)
    fun getOverdue(now: Long): Flow<List<LifeItem>>
}
