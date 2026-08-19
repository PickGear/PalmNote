package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_records")
@Immutable
data class UsageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assetId: Long,
    val usedAt: Long = System.currentTimeMillis(),
    val note: String = ""
)
