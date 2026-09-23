package com.palmnote.data.db.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.palmnote.data.db.entity.LifeItem
import kotlinx.coroutines.flow.Flow

/**
 * 示例数据标记：写在 `LifeItem.meta`（v8 已有列，**免迁移**）。
 *
 * 演示模式写入的是**真实的 `life_items` 行**（可被查看 / 编辑 / 删除），
 * 只是带这个标记，以便「关闭演示模式」时从所有查询里排除干净。
 */
const val LIFE_DEMO_META = "{\"demo\":true}"

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

    /**
     * 某模板下**用户自己的**记录数（示例行不算）。
     *
     * 用途：退役内置模板前的安全闸 —— 只要用户在该模板下真有一条记录就不删模板行，
     * 免得留下指向已删除模板的孤儿记录（宁可留一个不再展示的行，也不动用户数据）。
     */
    @Query("SELECT COUNT(*) FROM life_items WHERE templateId = :templateId AND (meta IS NULL OR meta <> :demoMeta)")
    suspend fun countUserItemsByTemplate(templateId: Long, demoMeta: String = LIFE_DEMO_META): Int

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

    /** parentId 无外键级联 ⟹ 删父条目必须**显式**先删子任务，否则留孤儿行。 */
    @Query("DELETE FROM life_items WHERE parentId = :parentId")
    suspend fun deleteItemsByParent(parentId: Long)

    /** 删除某模板下的全部记录（自定义模板删除时的级联；示例行不在此列，按 meta 区分）。 */
    @Query("DELETE FROM life_items WHERE templateId = :templateId AND (meta IS NULL OR meta <> :demoMeta)")
    suspend fun deleteItemsByTemplate(templateId: Long, demoMeta: String = LIFE_DEMO_META)

    /** 带子任务的级联删除（删父条目的所有出口都应走这里，不走 [deleteItem]）。 */
    @Transaction
    suspend fun deleteItemCascade(id: Long) {
        deleteItemsByParent(id)
        deleteItem(id)
    }

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

    /**
     * 纪念日类条目（生日/纪念日模板创建），供仪表盘纪念日卡与日历同步使用。
     * 演示感知（**互斥**）：includeDemo=true 只看示例；false 只看用户自己的。
     * 日历同步固定传 false —— **示例属于演示，不应写进系统日历**。
     */
    @Query("""
        SELECT li.* FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id
        WHERE lt.icon IN ('cake', 'celebration')
        AND lt.isHidden = 0
        AND li.status != 'ARCHIVED'
        AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        ORDER BY li.dueDate ASC
    """)
    fun getAnniversaryLikeItems(includeDemo: Boolean, demoMeta: String): Flow<List<LifeItem>>

    /** 逾期反馈：dueDate < now 且未完成（不含已关闭模板）。 */
    @Query("""
        SELECT li.* FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.dueDate IS NOT NULL AND li.dueDate < :now
        AND li.status != 'COMPLETED' AND li.status != 'ARCHIVED'
        ORDER BY li.dueDate ASC
    """)
    fun getOverdue(now: Long): Flow<List<LifeItem>>

    /**
     * 某日安排（首页「今日看板」时段桶）：dueDate 落在 [start, end) 内的条目，
     * 返回完整实体（时段桶需要 `dueTime` 列）。演示感知（**互斥**）：开＝只看示例，关＝只看自己的。
     */
    @Query("""
        SELECT li.* FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.dueDate >= :start AND li.dueDate < :end AND li.status != 'ARCHIVED'
          AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        ORDER BY li.dueTime ASC, li.sortOrder ASC, li.createdAt DESC
    """)
    fun getScheduledBetweenDemoAware(
        start: Long,
        end: Long,
        includeDemo: Boolean,
        demoMeta: String
    ): Flow<List<LifeItem>>

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

    // ───────── 示例（演示模式）感知版本：**互斥** ─────────
    // 开启演示（includeDemo=true）＝**只看示例条目**（用户自己的数据不参与）；
    // 关闭演示（includeDemo=false）＝**只看用户自己的条目**（排除带示例标记的行）。
    // 与「演示模式自成一套、不掺真实数据」的产品语义一致（同 Rocket Money 等 demo mode）。

    @Query("""
        SELECT date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS cnt
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE COALESCE(li.dueDate, li.createdAt) >= :start AND COALESCE(li.dueDate, li.createdAt) < :end
        AND li.status != 'ARCHIVED'
        AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        GROUP BY day
    """)
    fun getDayCountsBetweenDemoAware(
        start: Long,
        end: Long,
        includeDemo: Boolean,
        demoMeta: String
    ): Flow<List<LifeDayCount>>

    @Query("""
        SELECT date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') AS day,
               lt.category AS category, COUNT(*) AS cnt
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE COALESCE(li.dueDate, li.createdAt) >= :start AND COALESCE(li.dueDate, li.createdAt) < :end
        AND li.status != 'ARCHIVED'
        AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        GROUP BY day, category
    """)
    fun getDayCategoryCountsBetweenDemoAware(
        start: Long,
        end: Long,
        includeDemo: Boolean,
        demoMeta: String
    ): Flow<List<LifeDayCategoryCount>>

    /** 单日回读条目（v4 §五）：某天全部条目，含模板配色/图标/分类；演示感知（**互斥**：开＝只看示例，关＝只看自己的）。 */
    data class LifeDayItemRow(
        @ColumnInfo(name = "id") val id: Long,
        @ColumnInfo(name = "title") val title: String,
        @ColumnInfo(name = "status") val status: String,
        @ColumnInfo(name = "effective") val effective: Long,
        @ColumnInfo(name = "icon") val icon: String,
        @ColumnInfo(name = "category") val category: String,
        @ColumnInfo(name = "color") val color: String,
        @ColumnInfo(name = "isDemo") val isDemo: Boolean
    )

    @Query("""
        SELECT li.id AS id, li.title AS title, li.status AS status,
               COALESCE(li.dueDate, li.createdAt) AS effective,
               lt.icon AS icon, lt.category AS category, lt.color AS color,
               CASE WHEN li.meta = :demoMeta THEN 1 ELSE 0 END AS isDemo
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE COALESCE(li.dueDate, li.createdAt) >= :start AND COALESCE(li.dueDate, li.createdAt) < :end
          AND li.status != 'ARCHIVED'
          AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        ORDER BY effective ASC
    """)
    fun getItemsForDayDemoAware(
        start: Long,
        end: Long,
        includeDemo: Boolean,
        demoMeta: String
    ): Flow<List<LifeDayItemRow>>

    /**
     * 格子板行（v4 §四）：条目 + 模板元数据一次取出。
     *
     * 卡片**锚模板**、实例靠卡内切换（§4.6），所以这里把模板的 icon / color / fieldsConfig
     * 一并带出，消费端按 `templateId` 归组即可，不必二次查模板表。
     * 演示感知（**互斥**）：开＝只看示例，关＝只看用户自己的。
     */
    data class LifeBoardItemRow(
        @ColumnInfo(name = "itemId") val itemId: Long,
        @ColumnInfo(name = "title") val title: String,
        @ColumnInfo(name = "status") val status: String,
        @ColumnInfo(name = "fieldsData") val fieldsData: String,
        @ColumnInfo(name = "createdAt") val createdAt: Long,
        @ColumnInfo(name = "updatedAt") val updatedAt: Long,
        @ColumnInfo(name = "dueDate") val dueDate: Long?,
        @ColumnInfo(name = "isDemo") val isDemo: Boolean,
        @ColumnInfo(name = "templateId") val templateId: Long,
        @ColumnInfo(name = "templateName") val templateName: String,
        @ColumnInfo(name = "icon") val icon: String,
        @ColumnInfo(name = "color") val color: String,
        @ColumnInfo(name = "category") val category: String,
        @ColumnInfo(name = "fieldsConfig") val fieldsConfig: String,
        @ColumnInfo(name = "isSpecial") val isSpecial: Boolean
    )

    @Query("""
        SELECT li.id AS itemId, li.title AS title, li.status AS status, li.fieldsData AS fieldsData,
               li.createdAt AS createdAt, li.updatedAt AS updatedAt, li.dueDate AS dueDate,
               CASE WHEN li.meta = :demoMeta THEN 1 ELSE 0 END AS isDemo,
               lt.id AS templateId, lt.name AS templateName, lt.icon AS icon, lt.color AS color,
               lt.category AS category, lt.fieldsConfig AS fieldsConfig, lt.isSpecial AS isSpecial
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.status != 'ARCHIVED' AND li.parentId IS NULL
          AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        ORDER BY lt.sortOrder ASC, li.updatedAt DESC
    """)
    fun getBoardItemsDemoAware(includeDemo: Boolean, demoMeta: String): Flow<List<LifeBoardItemRow>>

    /**
     * 某模板在时间范围内的条目（按天，带字段值）——服务详情页**模板专属结构**：
     * 打卡 / 心情的**月热力网格**、日记的**时间线**（§14.12(6)、§14.11 #11/#12/#13）。
     *
     * 口径与月历一致：`COALESCE(dueDate, createdAt)` + `'localtime'`（防跨时区错日）。
     * 演示感知（**互斥**）：开＝只看示例，关＝只看用户自己的。
     */
    data class TemplateDayRow(
        @ColumnInfo(name = "day") val day: String,
        @ColumnInfo(name = "title") val title: String,
        @ColumnInfo(name = "fieldsData") val fieldsData: String
    )

    @Query("""
        SELECT date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') AS day,
               li.title AS title,
               li.fieldsData AS fieldsData
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.templateId = :templateId
          AND COALESCE(li.dueDate, li.createdAt) >= :start AND COALESCE(li.dueDate, li.createdAt) < :end
          AND li.status != 'ARCHIVED'
          AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        ORDER BY day ASC
    """)
    fun getTemplateDayRowsDemoAware(
        templateId: Long,
        start: Long,
        end: Long,
        includeDemo: Boolean,
        demoMeta: String
    ): Flow<List<TemplateDayRow>>

    /** 模板 → 该模板下的记录条数（管理页的「已有 N 条记录」提示用）。 */
    data class TemplateItemCount(
        @ColumnInfo(name = "templateId") val templateId: Long,
        @ColumnInfo(name = "cnt") val cnt: Int
    )

    /**
     * 各模板的记录条数，**只数用户自己的记录**（示例行不算）。
     *
     * 关闭模板的确认文案要如实说明「已有 N 条记录不会被删除」，而演示模式的 42 条示例行
     * 不是用户的数据 —— 把它们算进去会吓到用户，也让「无数据就不弹确认」的判断失效。
     * 口径与格子板一致：排除归档与子任务（只数会被展示的「正条目」）。
     */
    @Query("""
        SELECT templateId AS templateId, COUNT(*) AS cnt
        FROM life_items
        WHERE (meta IS NULL OR meta <> :demoMeta)
          AND status != 'ARCHIVED' AND parentId IS NULL
        GROUP BY templateId
    """)
    fun getItemCountsByTemplate(demoMeta: String): Flow<List<TemplateItemCount>>

    /** 某天的条目（就地打卡 / 就地记心情用）：只要 id 与 fieldsData，按天口径同月历。 */
    data class DayLiteItem(
        @ColumnInfo(name = "itemId") val itemId: Long,
        @ColumnInfo(name = "templateId") val templateId: Long,
        @ColumnInfo(name = "fieldsData") val fieldsData: String
    )

    /**
     * 某天的全部条目（**演示感知，互斥**：开＝只看示例，关＝只看自己的）。
     *
     * 用途：**就地完成**（§7.2）——打卡与心情的语义是「今天有没有」，
     * 所以要先知道今天这条在不在，再决定是插一条还是改字段 / 撤销。
     */
    @Query(
        """
        SELECT li.id AS itemId, li.templateId AS templateId, li.fieldsData AS fieldsData
        FROM life_items li
        WHERE date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') = :day
        AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        """
    )
    fun getDayItemsDemoAware(day: String, includeDemo: Boolean, demoMeta: String): Flow<List<DayLiteItem>>

    /** 某天、某模板的条目（就地打卡判重）。 */
    @Query(
        """
        SELECT li.id AS itemId, li.templateId AS templateId, li.fieldsData AS fieldsData
        FROM life_items li
        WHERE li.templateId = :templateId
        AND date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') = :day
        AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        """
    )
    suspend fun getDayItemsOfTemplate(
        templateId: Long,
        day: String,
        includeDemo: Boolean,
        demoMeta: String
    ): List<DayLiteItem>

    /** 清除示例条目（重新开启演示模式时先清后播 = **重置**；关闭演示模式时也走它物理删除）。 */
    @Query("DELETE FROM life_items WHERE meta = :demoMeta")
    suspend fun clearDemoItems(demoMeta: String)

    /**
     * 某模板的**打卡连击**源数据：distinct 的打卡天（按 `COALESCE(dueDate, createdAt)` 口径，
     * 与月历 / 热力一致），升序。连击 = 这些天里**连续到今天**的最长一段（见 [computeCheckInStreak]）。
     * 演示感知（**互斥**）：开＝只看示例，关＝只看自己的。
     */
    @Query("""
        SELECT DISTINCT date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') AS day
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.templateId = :templateId
          AND li.status != 'ARCHIVED'
          AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
        ORDER BY day ASC
    """)
    fun getDistinctCheckInDays(templateId: Long, includeDemo: Boolean, demoMeta: String): Flow<List<String>>

    /**
     * 某模板在 [start, end) 内的会话 `fieldsData`（专注计时写会话用；统计页按 `duration` 字段求和）。
     * 演示感知（**互斥**）。
     */
    @Query("""
        SELECT li.fieldsData AS fieldsData
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.templateId = :templateId
          AND COALESCE(li.dueDate, li.createdAt) >= :start AND COALESCE(li.dueDate, li.createdAt) < :end
          AND li.status != 'ARCHIVED'
          AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
    """)
    fun getFocusSessionFields(
        templateId: Long,
        start: Long,
        end: Long,
        includeDemo: Boolean,
        demoMeta: String
    ): Flow<List<String>>

    /**
     * 某模板在 [start, end) 内**未完成**的条目数（统计页「今日待办」用）。
     *
     * 「未完成」= `status` 既不是 `COMPLETED` 也不是 `ARCHIVED`；只数正条目（`parentId IS NULL`，
     * 与格子板口径一致），并按模板可见性过滤（关闭的模板不进统计，§4.8(3) 的 11 出口之一）。
     * 演示感知（**互斥**）：开＝只看示例，关＝只看自己的。
     */
    @Query("""
        SELECT COUNT(*)
        FROM life_items li
        INNER JOIN life_templates lt ON li.templateId = lt.id AND lt.isHidden = 0
        WHERE li.templateId = :templateId
          AND COALESCE(li.dueDate, li.createdAt) >= :start AND COALESCE(li.dueDate, li.createdAt) < :end
          AND li.status NOT IN ('COMPLETED', 'ARCHIVED')
          AND li.parentId IS NULL
          AND ((:includeDemo = 1 AND li.meta = :demoMeta) OR (:includeDemo = 0 AND (li.meta IS NULL OR li.meta <> :demoMeta)))
    """)
    fun getUnfinishedCountByTemplate(
        templateId: Long,
        start: Long,
        end: Long,
        includeDemo: Boolean,
        demoMeta: String
    ): Flow<Int>
}
