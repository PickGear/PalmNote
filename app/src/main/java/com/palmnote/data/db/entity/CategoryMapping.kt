package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * @deprecated UI 未使用，保留避免数据库迁移
 */
@Entity(tableName = "category_mappings")
data class CategoryMapping(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assetCategory: String,
    val billCategory: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
