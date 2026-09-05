package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "life_moments", indices = [
    Index(value = ["date"], name = "idx_moment_date"),
    Index(value = ["lifeItemId"], name = "idx_moment_life_item")
])
@Immutable
data class LifeMoment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val imageUri: String? = null,
    val date: Long,
    val tags: String = "",
    val mood: String? = null,
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val weather: String = "",
    val temperature: Int = 0,
    val isMarkdown: Boolean = false,
    val category: String = "",
    val isFavorite: Boolean = false,
    val lifeItemId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
