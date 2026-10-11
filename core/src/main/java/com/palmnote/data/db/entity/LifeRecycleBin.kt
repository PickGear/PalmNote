package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 生活条目回收站：删除的记录（含子任务快照行）暂存于此，可恢复。
 *
 * 快照策略与账单回收站一致 —— 一行 `life_items` 原样存一份：
 * - 删除时「父条目 + 全部子任务」各自成行，行间用快照里的 `parentId` 关联；
 *   列表只显示整组删除的顶层行（`parentId` 为 NULL、或其父行不在站内），恢复按组还原；
 * - `templateName` 是删除那一刻的模板名快照，仅供列表展示（模板可能随后被删）；
 * - 演示标记随 `meta` / `isSeedSample` 原样进站，演示清理按 `meta` 清站内示例行。
 *
 * 恢复的前提是模板还在：模板被删时其条目是不可恢复的硬删（见
 * `LifeTemplateRepositoryImpl.deleteTemplateCascade`），站内残留行也会一并清掉。
 */
@Entity(tableName = "life_recycle_bin")
data class LifeRecycleBin(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalId: Long,
    val templateId: Long,
    val templateName: String,
    val title: String,
    val fieldsData: String,
    val status: String,
    val note: String,
    val sortOrder: Int,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val dueDate: Long? = null,
    val dueTime: Int? = null,
    val recurring: String? = null,
    val recurringEndType: String? = null,
    val recurringEndCount: Int? = null,
    val recurringEndDate: Long? = null,
    val parentId: Long? = null,
    val remindAt: Int? = null,
    val meta: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isSeedSample: Boolean = false,
    val deletedAt: Long = System.currentTimeMillis()
)

fun LifeItem.toRecycleBin(templateName: String, deletedAt: Long) = LifeRecycleBin(
    originalId = id, templateId = templateId, templateName = templateName,
    title = title, fieldsData = fieldsData, status = status, note = note,
    sortOrder = sortOrder, isFavorite = isFavorite,
    createdAt = createdAt, updatedAt = updatedAt,
    dueDate = dueDate, dueTime = dueTime,
    recurring = recurring, recurringEndType = recurringEndType,
    recurringEndCount = recurringEndCount, recurringEndDate = recurringEndDate,
    parentId = parentId, remindAt = remindAt, meta = meta, isSeedSample = isSeedSample,
    // 同一次删除的整组共享同一时间戳：它是组桶标识，组内恢复/彻底删除按它圈定范围
    deletedAt = deletedAt
)

fun LifeRecycleBin.toLifeItem() = LifeItem(
    id = originalId, templateId = templateId,
    title = title, fieldsData = fieldsData, status = status, note = note,
    sortOrder = sortOrder, isFavorite = isFavorite,
    createdAt = createdAt, updatedAt = updatedAt,
    dueDate = dueDate, dueTime = dueTime,
    recurring = recurring, recurringEndType = recurringEndType,
    recurringEndCount = recurringEndCount, recurringEndDate = recurringEndDate,
    parentId = parentId, remindAt = remindAt, meta = meta, isSeedSample = isSeedSample
)
