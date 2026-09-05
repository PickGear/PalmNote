package com.palmnote.feature.vault

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v3 → v4 密码本手机号列迁移测试：v3 库升级后 phone 列存在且默认空串。
 * 用 Robolectric 本地 JVM 跑，与主库迁移测试一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class MigrationV3ToV4Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VaultDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-vault-3-to-4"
    }

    @Test
    fun migrateV3ToV4_addsPhoneColumn() {
        helper.createDatabase(DB, 3).close()

        helper.runMigrationsAndValidate(DB, 4, true, MigrationV3ToV4).close()

        val migrated = helper.createDatabase(DB, 4)
        // 已有行 phone 默认空串
        migrated.execSQL(
            "INSERT INTO vault_entries " +
                "(id, title, username, email, phone, passwordEncrypted, url, notes, category, avatarPath, isFavorite, lastViewAt, createdAt, updatedAt) " +
                "VALUES (1, 't', 'u', 'e@x.com', '', x'0102', '', '', '其他', '', 0, 0, 1, 1)"
        )
        migrated.query("SELECT phone FROM vault_entries WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
        }
        // 新写入的 phone 可正常存储
        migrated.execSQL("UPDATE vault_entries SET phone = '13800000000' WHERE id = 1")
        migrated.query("SELECT phone FROM vault_entries WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("13800000000", cursor.getString(0))
        }
        migrated.close()
    }
}
