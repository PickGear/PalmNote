package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v13 → v14：给记账 / 物品四张表加 `isDemo` 标记列，让「演示数据」覆盖到生活页之外。
 *
 * ## 为什么加列而不是用现有字段当哨兵
 *
 * 示例账本（示例账本 + 示例钱包 + 示例账单 + 示例物品）需要能被整批识别与移除：
 * 账本表没有可作哨兵的现有列，`category` / `description` 都是用户可见字段，塞标记会露出马脚。
 * `isDemo` 是显式、可索引、可复用于导出过滤（示例不进 CSV）的唯一正确做法。
 *
 * ## 为什么是安全的 ALTER
 *
 * 四张表都只是 `ADD COLUMN ... NOT NULL DEFAULT 0`（同 `MIGRATION_10_11` 的 isSeedSample），
 * 不重建表、不动存量行 —— 存量数据一律 isDemo = 0。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `bills` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `assets` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `wallets` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `account_books` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `goals` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `goal_check_ins` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
        // 回收站也要带标记：用户在演示期删除的示例账单/物品会先进回收站，
        // 不带标记的话关演示清不掉、从回收站恢复还会混进真实数据。
        db.execSQL("ALTER TABLE `bill_recycle_bin` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `asset_recycle_bin` ADD COLUMN `isDemo` INTEGER NOT NULL DEFAULT 0")
    }
}
