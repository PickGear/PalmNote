package com.palmnote.feature.vault

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2：新增头像路径与邮箱列。
 */
object MigrationV1ToV2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_entries ADD COLUMN avatarPath TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE vault_entries ADD COLUMN email TEXT NOT NULL DEFAULT ''")
    }
}
