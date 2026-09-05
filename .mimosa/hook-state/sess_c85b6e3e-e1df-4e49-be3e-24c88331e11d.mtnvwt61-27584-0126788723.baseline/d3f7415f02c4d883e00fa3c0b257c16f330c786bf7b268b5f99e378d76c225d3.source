package com.palmnote.feature.vault

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 -> v4：新增手机号列（明文，与 email 同规则）。
 */
object MigrationV3ToV4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vault_entries ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
    }
}
