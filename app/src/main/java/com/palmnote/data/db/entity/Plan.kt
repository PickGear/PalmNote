package com.palmnote.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable
import com.palmnote.ui.theme.AppIcon

@Entity(tableName = "plans")
@Immutable
data class Plan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "Flag")
    val icon: AppIcon = AppIcon.Flag,
    val category: String = "LIFE", // WORK, STUDY, LIFE, HEALTH
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

