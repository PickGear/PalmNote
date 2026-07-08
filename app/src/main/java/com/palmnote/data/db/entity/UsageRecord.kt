package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_records")
data class UsageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assetId: Long,
    val usedAt: Long = System.currentTimeMillis(),
    val note: String = ""
)
