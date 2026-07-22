package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
@Immutable
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val yearMonth: String, // "2024-01"
    val totalBudget: Double,
    val reminderThreshold: Double = 0.8, // 80%
    val reminderAtThreshold: Boolean = true,
    val reminderOverBudget: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
