package com.palmnote.data.db.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v4 → v5 密码本表迁移测试：v4 库升级后 vault_entries 表存在且可读写。
 */
@RunWith(AndroidJUnit4::class)
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
    }

    @Test
    fun migrate4To5_createsVaultEntriesTable() {
        val db = helper.createDatabase(DB, 4).apply {
            execSQL("INSERT INTO vault_entries (id, title, passwordEncrypted) VALUES (1, 'test', x'0102')")
        }
        db.close()

        helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5)

        val migrated = helper.createDatabase(DB, 5)
        migrated.query("SELECT title, passwordEncrypted FROM vault_entries WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("test", cursor.getString(0))
            val blob = cursor.getBlob(1)
            assertNotNull(blob)
            assertEquals(2, blob.size)
        }
    }

    @Test
    fun migrate4To5_existingTablesStillValid() {
        val db = helper.createDatabase(DB, 4)
        db.close()

        helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5)

        val migrated = helper.createDatabase(DB, 5)
        migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name='bills'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
    }
}
