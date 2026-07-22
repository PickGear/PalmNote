package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable
import com.palmnote.ui.theme.AppIcon

/**
 * 可自定义的分类配置
 * 支持资产分类、账单分类、目标分类、纪念日类型、瞬间分类
 */
@Entity(tableName = "category_configs")
@Immutable
data class CategoryConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // ASSET, BILL_EXPENSE, BILL_INCOME, GOAL, ANNIVERSARY, MOMENT, TAG
    val name: String, // 分类名称
    @ColumnInfo(defaultValue = "Settings")
    val icon: AppIcon = AppIcon.Settings, // 图标
    val color: String = "", // 自定义颜色（十六进制）
    val sortOrder: Int = 0,
    val isDefault: Boolean = false, // 是否为系统默认
    val isEnabled: Boolean = true, // 是否启用
    val parentId: Long? = null, // 父分类ID（用于子分类）
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 可自定义的标签
 */
@Entity(tableName = "custom_tags")
@Immutable
data class CustomTag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String, // 标签名称
    val color: String = "", // 标签颜色
    @ColumnInfo(defaultValue = "Flag")
    val icon: AppIcon = AppIcon.Flag, // 标签图标
    val usageCount: Int = 0, // 使用次数
    val applicableTypes: String = "", // JSON array: ["ASSET", "BILL", "GOAL", "MOMENT"]
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
