package com.palmnote.data.db.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 成就（`achievements` 表）。
 *
 * ## ⚠️ 目前是**死基础设施**（2026-10-01 全仓审计）
 *
 * 实体 / `AchievementDao` / `AchievementRepository` 三层齐备、Hilt 也绑定了，
 * 但**没有任何 ViewModel 或界面使用它**：
 * - 没有任何地方 `unlock`（写入 `unlockedAt`）→ 表永远是空的；
 * - 没有任何地方展示成就列表。
 *
 * 与之呼应的是 `TriggerEventConsumer` 里 `HabitCheckedIn` 分支的那句注释
 * 「习惯打卡可触发成就评估」—— 评估本身**没有实现**，事件也没人发。
 *
 * 所以：**别把它当作"已有功能"**。要做成就系统，得先定"哪些行为解锁哪些成就"
 * 以及展示入口；在那之前这张表只是占位。
 */
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
