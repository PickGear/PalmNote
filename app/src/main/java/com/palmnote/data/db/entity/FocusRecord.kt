package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "focus_records",
    indices = [
        Index(value = ["todoId"], name = "idx_focus_todo"),
        Index(value = ["startTime"], name = "idx_focus_start")
    ]
)
data class FocusRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val todoId: Long? = null,
    val readingItemId: Long? = null,
    val durationMinutes: Int,
    val targetMinutes: Int = 25,
    val completed: Boolean,
    val startTime: Long,
    val endTime: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
