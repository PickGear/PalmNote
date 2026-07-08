package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "life_reports",
    indices = [
        Index(value = ["type", "periodStart"], name = "idx_report_type_period")
    ]
)
data class LifeReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val periodStart: Long,
    val periodEnd: Long,
    val reportData: String,
    val createdAt: Long = System.currentTimeMillis()
)
