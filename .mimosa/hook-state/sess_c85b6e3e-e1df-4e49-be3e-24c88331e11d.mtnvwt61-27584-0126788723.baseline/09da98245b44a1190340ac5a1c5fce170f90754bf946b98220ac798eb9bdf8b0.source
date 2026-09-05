package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v4 → v5 密码本表迁移测试：v4 库升级后 vault_entries 表存在且可读写。
 * 用 Robolectric 本地 JVM 跑，无需模拟器（CI 免费 runner 更稳定）。
 *
 * 注意：必须用无业务逻辑的 [Application]（而非合并清单里的 .PalmNoteApp），
 * 否则 Hilt 注入会触发 SQLCipher System.loadLibrary("sqlcipher")，在 JVM 上必败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration4To5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-4-to-5"
        const val DB_TABLES = "migration-4-to-5-tables"
    }

    @Test
    fun migrate4To5_createsVaultEntriesTable() {
        // v4 库中不存在 vault_entries 表（v5 才引入），先建 v4 库
        helper.createDatabase(DB, 4).close()

        helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5).close()

        // 迁移后 vault_entries 表已存在且可读写
        val migrated = helper.createDatabase(DB, 5)
        migrated.execSQL(
            "INSERT INTO vault_entries (id, title, username, passwordEncrypted, url, notes, category, createdAt, updatedAt) " +
                "VALUES (1, 'test', 'user', x'0102', '', '', '其他', 1, 1)"
        )
        migrated.query("SELECT title, username, passwordEncrypted FROM vault_entries WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("test", cursor.getString(0))
            assertEquals("user", cursor.getString(1))
            val blob = cursor.getBlob(2)
            assertNotNull(blob)
            assertEquals(2, blob.size)
        }
        migrated.close()
    }

    @Test
    fun migrate4To5_existingTablesStillValid() {
        val db = helper.createDatabase(DB_TABLES, 4)
        db.close()

        helper.runMigrationsAndValidate(DB_TABLES, 5, true, MIGRATION_4_5).close()

        val migrated = helper.createDatabase(DB_TABLES, 5)
        migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name='bills'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        migrated.close()
    }
}
