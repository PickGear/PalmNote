package com.palmnote.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "mood_diaries", indices = [
    Index(value = ["date"], name = "idx_mood_date"),
    Index(value = ["lifeItemId"], name = "idx_mood_life_item")
])
data class MoodDiary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,
    val mood: String,
    val content: String,
    val tags: String,
    val factors: String = "[]",
    val lifeItemId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
