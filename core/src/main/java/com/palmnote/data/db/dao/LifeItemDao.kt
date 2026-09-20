package com.palmnote.data.db.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.palmnote.data.db.entity.LifeItem
import kotlinx.coroutines.flow.Flow

/** 月历密度行（v4 §五：按天条数，口径 = COALESCE(dueDate, createdAt)）。 */
data class LifeDayCount(val day: String, val cnt: Int)

/** 月历分类小点行（v4 §五：格内 ≤3 个分类色小点）。 */
data class LifeDayCategoryCount(val day: String, val category: String, val cnt: Int)

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
    // ⚠️ 定案 31：全出口关闭——过滤下沉到查询端（INNER JOIN life_templates AND lt.isHidden = 0），
    // 消费端不再各自过滤；新增出口必须同样下沉，禁止只做消费端过滤（假修）。

    /** 今日看板聚合：dueDate 落在 [start, end) 内的条目（不含归档、不含已关闭模板）。 */
    @Query("""
        SELECT li.* FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.dueDate >= :start AND li.dueDate < :end AND li.status != 'ARCHIVED'
        ORDER BY li.dueTime ASC, li.sortOrder ASC, li.createdAt DESC
    """)
    fun getScheduledBetween(start: Long, end: Long): Flow<List<LifeItem>>

    /** 周历标记点：范围内 distinct 的 dueDate（毫秒，不含归档、不含已关闭模板）。 */
    @Query("""
        SELECT DISTINCT li.dueDate FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.dueDate IS NOT NULL AND li.dueDate >= :start AND li.dueDate < :end
        AND li.status != 'ARCHIVED'
    """)
    fun getDistinctDueDatesBetween(start: Long, end: Long): Flow<List<Long>>

    /** 待办卡补集：待办模板、非今日（逾期/未来/无日期）、未完成、非子任务。 */
    @Query("""
        SELECT li.* FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.templateId = :todoTemplateId AND li.parentId IS NULL
        AND li.status != 'COMPLETED' AND li.status != 'ARCHIVED'
        AND (li.dueDate IS NULL OR li.dueDate < :todayStart OR li.dueDate >= :todayEnd)
        ORDER BY li.dueDate ASC, li.sortOrder ASC, li.createdAt DESC
    """)
    fun getTodoComplement(todayStart: Long, todayEnd: Long, todoTemplateId: Long): Flow<List<LifeItem>>

    /** 计划页子任务：parentId 关联。 */
    @Query("SELECT * FROM life_items WHERE parentId = :parentId ORDER BY sortOrder ASC, createdAt DESC")
    fun getSubtasks(parentId: Long): Flow<List<LifeItem>>

    /** 纪念日类条目（生日/纪念日模板创建），供仪表盘纪念日卡与日历同步使用。 */
    @Query("""
        SELECT li.* FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id
        WHERE lt.icon IN ('cake', 'celebration')
        AND lt.isHidden = 0
        AND li.status != 'ARCHIVED'
        ORDER BY li.dueDate ASC
    """)
    fun getAnniversaryLikeItems(): Flow<List<LifeItem>>

    /** 逾期反馈：dueDate < now 且未完成（不含已关闭模板）。 */
    @Query("""
        SELECT li.* FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.dueDate IS NOT NULL AND li.dueDate < :now
        AND li.status != 'COMPLETED' AND li.status != 'ARCHIVED'
        ORDER BY li.dueDate ASC
    """)
    fun getOverdue(now: Long): Flow<List<LifeItem>>

    /** 月历密度（v4 §五）：按天条数，口径 COALESCE(dueDate, createdAt)，'localtime' 防跨时区漂移。 */
    @Query("""
        SELECT date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS cnt
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE COALESCE(li.dueDate, li.createdAt) >= :start AND COALESCE(li.dueDate, li.createdAt) < :end
        AND li.status != 'ARCHIVED'
        GROUP BY day
    """)
    fun getDayCountsBetween(start: Long, end: Long): Flow<List<LifeDayCount>>

    /** 月历分类小点：按天 × 模板分类计数（消费端每天最多取 3 类）。 */
    @Query("""
        SELECT date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') AS day,
               lt.category AS category, COUNT(*) AS cnt
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE COALESCE(li.dueDate, li.createdAt) >= :start AND COALESCE(li.dueDate, li.createdAt) < :end
        AND li.status != 'ARCHIVED'
        GROUP BY day, category
    """)
    fun getDayCategoryCountsBetween(start: Long, end: Long): Flow<List<LifeDayCategoryCount>>
}
