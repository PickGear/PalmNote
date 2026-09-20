package com.palmnote.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.palmnote.data.db.entity.FieldValue
import kotlinx.coroutines.flow.Flow

/** 分布聚合行：某选项字段每个取值的计数。 */
data class DistributionRow(val text: String, val cnt: Int)

/** 按天计数行（热力图 / 日历密度），day = 'yyyy-MM-dd'（本地时区）。 */
data class DailyCountRow(val day: String, val cnt: Int)

/** 模板 × 字段数值序列行（折线图），day = 'yyyy-MM-dd'。 */
data class SeriesRow(val day: String, val num: Double)

/**
 * 统计聚合 API（总纲 P4：通用聚合查询）。
 * 所有查询同时追踪 field_values 与 life_items 两张表，双写与条目变更都会触发失效。
 */
@Dao
interface FieldValueDao {

    // ---- 写入（双写路径） ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<FieldValue>)

    @Query("DELETE FROM field_values WHERE itemId = :itemId")
    suspend fun deleteByItem(itemId: Long)

    @Query("DELETE FROM field_values WHERE itemId IN (SELECT id FROM life_items WHERE templateId = :templateId)")
    suspend fun deleteByTemplate(templateId: Long)

    // ---- 数值聚合（NUMERIC 组） ----

    @Query("SELECT SUM(num) FROM field_values WHERE fieldKey = :fieldKey AND num IS NOT NULL")
    fun sumNum(fieldKey: String): Flow<Double?>

    @Query("SELECT AVG(num) FROM field_values WHERE fieldKey = :fieldKey AND num IS NOT NULL")
    fun avgNum(fieldKey: String): Flow<Double?>

    @Query("SELECT MIN(num) FROM field_values WHERE fieldKey = :fieldKey AND num IS NOT NULL")
    fun minNum(fieldKey: String): Flow<Double?>

    @Query("SELECT MAX(num) FROM field_values WHERE fieldKey = :fieldKey AND num IS NOT NULL")
    fun maxNum(fieldKey: String): Flow<Double?>

    @Query("SELECT COUNT(*) FROM field_values WHERE fieldKey = :fieldKey")
    fun countBy(fieldKey: String): Flow<Int>

    /** 某模板某字段的按天数值序列（折线图：每日趋势 / 与上期对比）。 */
    @Query("""
        SELECT date(COALESCE(li.dueDate, li.createdAt) / 1000, 'unixepoch', 'localtime') AS day,
               fv.num AS num
        FROM field_values fv
        INNER JOIN life_items li ON fv.itemId = li.id
        WHERE li.templateId = :templateId AND fv.fieldKey = :fieldKey AND fv.num IS NOT NULL
        ORDER BY day ASC
    """)
    fun seriesByTemplateField(templateId: Long, fieldKey: String): Flow<List<SeriesRow>>

    // ---- 分布（SELECT / MULTI_SELECT / TAG） ----

    @Query("""
        SELECT fv.text AS text, COUNT(*) AS cnt
        FROM field_values fv
        WHERE fv.fieldKey = :fieldKey AND fv.text IS NOT NULL AND fv.text != ''
        GROUP BY fv.text
        ORDER BY cnt DESC
    """)
    fun distribution(fieldKey: String): Flow<List<DistributionRow>>

    /** 某模板内的分布（影响因素 / 情绪结构区）。 */
    @Query("""
        SELECT fv.text AS text, COUNT(*) AS cnt
        FROM field_values fv
        INNER JOIN life_items li ON fv.itemId = li.id
        WHERE li.templateId = :templateId AND fv.fieldKey = :fieldKey
          AND fv.text IS NOT NULL AND fv.text != ''
        GROUP BY fv.text
        ORDER BY cnt DESC
    """)
    fun distributionByTemplate(templateId: Long, fieldKey: String): Flow<List<DistributionRow>>

    // ---- 按天密度（热力图 / 日历标记） ----

    @Query("""
        SELECT date(COALESCE(dueDate, createdAt) / 1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS cnt
        FROM life_items
        WHERE status != 'ARCHIVED'
          AND COALESCE(dueDate, createdAt) >= :startMs
          AND COALESCE(dueDate, createdAt) < :endMs
        GROUP BY day
        ORDER BY day ASC
    """)
    fun dailyCounts(startMs: Long, endMs: Long): Flow<List<DailyCountRow>>

    // ---- STREAK（跨记录聚合，§3.5 F 组归 P4 的那一个） ----

    /** 某模板的活跃日（打卡日 = 条目执行日，缺省落库日），升序去重 'yyyy-MM-dd'。 */
    @Query("""
        SELECT DISTINCT date(COALESCE(dueDate, createdAt) / 1000, 'unixepoch', 'localtime') AS d
        FROM life_items
        WHERE templateId = :templateId AND status != 'ARCHIVED'
        ORDER BY d ASC
    """)
    fun activeDays(templateId: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM field_values WHERE fieldKey = :fieldKey AND num = :num")
    fun countByNum(fieldKey: String, num: Double): Flow<Int>
}
