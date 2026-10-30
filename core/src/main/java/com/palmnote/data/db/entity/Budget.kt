package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.palmnote.domain.model.DemoOrigin

@Entity(tableName = "budgets")
@Immutable
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val yearMonth: String, // "2024-01"
    val totalBudget: Long, // 预算总额（分）
    val reminderThreshold: Double = 0.8, // 80%
    val reminderAtThreshold: Boolean = true,
    val reminderOverBudget: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // 预算没有账本维度，演示隔离靠这一位（与 bills/assets/wallets.demoOrigin 同口径）：
    // NONE 真实 / SEED 播种示例 / USER 演示期自建
    val demoOrigin: DemoOrigin = DemoOrigin.NONE
)
