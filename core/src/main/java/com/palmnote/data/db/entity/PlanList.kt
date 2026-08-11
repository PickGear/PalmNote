package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable
import com.palmnote.ui.theme.AppIcon

/**
 * @deprecated 已被 Plan 替代，保留避免数据库迁移
 */
@Entity(tableName = "plan_lists")
@Immutable
data class PlanList(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "emoji", defaultValue = "Assignment")
    val icon: AppIcon = AppIcon.Assignment,
    val description: String = "",
    val dueDate: Long? = null,
    val template: String = "",
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
