package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "achievements",
    indices = [Index(value = ["code"], unique = true)]
)
@Immutable
data class Achievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val description: String,
    val icon: String,
    val unlockedAt: Long? = null,
    val goalId: Long? = null
)
