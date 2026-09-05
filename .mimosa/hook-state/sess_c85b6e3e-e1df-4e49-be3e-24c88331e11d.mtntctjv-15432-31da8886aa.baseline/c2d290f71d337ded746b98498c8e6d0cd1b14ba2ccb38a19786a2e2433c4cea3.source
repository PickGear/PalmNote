package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "life_items",
    indices = [
        Index(value = ["templateId", "status"], name = "idx_items_template_status"),
        Index(value = ["templateId"], name = "idx_items_template"),
        Index(value = ["createdAt"], name = "idx_items_created"),
        Index(value = ["status"], name = "idx_items_status"),
        Index(value = ["dueDate"], name = "idx_items_due"),
        Index(value = ["parentId"], name = "idx_items_parent")
    ]
)
@Immutable
data class LifeItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val title: String,
    val fieldsData: String = "{}",
    val status: String = "ACTIVE",
    val note: String = "",
    val sortOrder: Int = 0,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // ---- 执行列（v8 新增，查询索引，非展示信源；fieldsData 仍是详情/卡片唯一信源）----
    val dueDate: Long? = null,
    val dueTime: Int? = null,
    val recurring: String? = null,
    val recurringEndType: String? = null,
    val recurringEndCount: Int? = null,
    val recurringEndDate: Long? = null,
    val parentId: Long? = null,
    val remindAt: Int? = null,
    val meta: String? = null
)
