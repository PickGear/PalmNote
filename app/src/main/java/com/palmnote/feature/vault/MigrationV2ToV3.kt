package com.palmnote.feature.vault

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 -> v3：新增收藏标记与最近查看时间列。
 */
object MigrationV2ToV3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_entries ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vault_entries ADD COLUMN lastViewAt INTEGER NOT NULL DEFAULT 0")
    }
}
