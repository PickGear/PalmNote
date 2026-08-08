package com.palmnote.feature.vault

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.palmnote.data.db.converter.Converters

/**
 * 密码本独立数据库。
 * 与主库 AppDatabase 物理隔离，加密体系独立。
 */
@Database(entities = [VaultEntry::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        const val DATABASE_NAME = "palmnote_vault.db"
    }
}
