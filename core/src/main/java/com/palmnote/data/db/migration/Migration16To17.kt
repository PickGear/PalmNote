package com.palmnote.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v16 → v17：生活条目接入回收站。
 *
 * 此前生活条目的所有删除出口都是物理级联删除（连带子任务），与账单/物品的
 * 「删除入站、可恢复」语义不一致，误删没有回头路。本迁移新建 `life_recycle_bin`
 * 快照表（列结构与 `life_items` 对应 + 模板名快照 + `deletedAt`），删除链路改为
 * 快照入站。存量数据无需回填——此前删掉的就是删掉了，表从空开始。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `life_recycle_bin` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`originalId` INTEGER NOT NULL, " +
                "`templateId` INTEGER NOT NULL, " +
                "`templateName` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`fieldsData` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`isFavorite` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`dueDate` INTEGER, " +
                "`dueTime` INTEGER, " +
                "`recurring` TEXT, " +
                "`recurringEndType` TEXT, " +
                "`recurringEndCount` INTEGER, " +
                "`recurringEndDate` INTEGER, " +
                "`parentId` INTEGER, " +
                "`remindAt` INTEGER, " +
                "`meta` TEXT, " +
                "`isSeedSample` INTEGER NOT NULL DEFAULT 0, " +
                "`deletedAt` INTEGER NOT NULL)"
        )
    }
}
