package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v19 → v20：`budgets` 表加「来源」列 `demoOrigin`，演示模式开始覆盖预算功能。
 *
 * ## 为什么现在加
 *
 * v19 起账单 / 物品 / 钱包都有 [com.palmnote.domain.model.DemoOrigin] 区分
 * 播种示例与演示期自建，唯独 `budgets` 没有——演示模式下预算整层返回空，
 * 预算页永远「未设预算」。加列后与三张表同构：演示播种一份示例预算（`SEED`），
 * 演示期自建标 `USER`，真实预算保持 `NONE`。
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budgets ADD COLUMN demoOrigin TEXT NOT NULL DEFAULT 'NONE'")
    }
}
