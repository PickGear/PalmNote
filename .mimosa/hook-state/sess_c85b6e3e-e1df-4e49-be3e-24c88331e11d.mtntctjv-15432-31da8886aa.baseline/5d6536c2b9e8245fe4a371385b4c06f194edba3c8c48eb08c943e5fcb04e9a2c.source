package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v2 → v3 迁移测试：bills/assets 表补索引。校验迁移后索引齐备且 schema 与 3.json 一致。
 * 用 Robolectric 本地 JVM 跑，无需模拟器（CI 免费 runner 更稳定）。
 *
 * 注意：必须用无业务逻辑的 [Application]（而非合并清单里的 .PalmNoteApp），
 * 否则 Hilt 注入会触发 SQLCipher System.loadLibrary("sqlcipher")，在 JVM 上必败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration2To3Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-2-to-3"
    }

    @Test
    fun migrate2To3_createsAllExpectedIndexes() {
        helper.createDatabase(DB, 2).close()

        val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)

        val expected = listOf(
            "index_bills_yearMonth_type_isDeleted",
            "index_bills_accountBookId_yearMonth_isDeleted",
            "index_bills_type_isDeleted",
            "index_bills_date_isDeleted",
            "index_bills_isReimbursable_isReimbursed_isDeleted",
            "index_bills_recurringId_isDeleted",
            "index_bills_category_isDeleted",
            "index_assets_status_isDeleted",
            "index_assets_category_isDeleted",
            "index_assets_warrantyExpireDate_isDeleted",
            "index_assets_nextMaintenanceDate_isDeleted",
            "index_assets_insuranceExpireDate_isDeleted",
            "index_assets_isFavorite_isDeleted"
        )
        val placeholders = expected.joinToString(",") { "?" }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN ($placeholders)",
            expected.toTypedArray()
        ).use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names.add(c.getString(0))
            assertEquals(expected.size, names.size)
            assertEquals(expected.sorted(), names.sorted())
        }
        db.close()
    }
}
