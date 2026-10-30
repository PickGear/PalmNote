package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v18 → v19：给演示数据加「来源」列 `demoOrigin`。
 *
 * ## 为什么需要
 *
 * v18 起示例数据靠 `isDemo` 与用户数据分开（账单落在示例账本，物品/钱包按行标记）。
 * 但「播种出来的示例」与「演示期用户自己记的」都满足 `isDemo = 1`，关演示时
 * 无法既删示例、又留下用户的内容——要么全删（丢用户数据），要么全留（示例混进真实清单）。
 *
 * [com.palmnote.domain.model.DemoOrigin] 把这一位拆开：`SEED` / `USER` / `NONE`。
 *
 * ## 迁移内容
 *
 * 1. 三张表加列，默认 `NONE`（真实数据）；
 * 2. **存量 `isDemo = 1` 的行一律标 `SEED`** —— 保守选择：把老的演示行当作示例种子，
 *    关闭演示时随示例清掉，而不是当成用户数据迁走（迁走会把示例混进真实账本，更难收拾）。
 *
 * `budgets` 表没有 `isDemo` 维度，本版不加列（示例预算随 v19 之后的播种改造再议）。
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf("bills", "assets", "wallets")
        for (table in tables) {
            db.execSQL("ALTER TABLE $table ADD COLUMN demoOrigin TEXT NOT NULL DEFAULT 'NONE'")
        }
        for (table in tables) {
            db.execSQL("UPDATE $table SET demoOrigin = 'SEED' WHERE isDemo = 1")
        }
    }
}
