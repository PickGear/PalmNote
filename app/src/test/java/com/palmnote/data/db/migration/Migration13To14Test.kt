package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
 * v13 → v14 迁移测试：给记账 / 物品 / 账本等表加 `isDemo`。
 *
 * 回归事故：回收站两张表的表名被写成**单数** `bill_recycle_bin` / `asset_recycle_bin`（真名是复数
 * `bills_recycle_bin` / `assets_recycle_bin`），于是迁移对不存在的表做 ALTER → 真机从旧库升级到 v14 时
 * 抛 `no such table`，**开库即崩**——从旧版本升级上来表现为「覆盖安装后闪退、卸载重装才正常」。
 * 这条迁移当时没有测试，
 * 所以一直没被发现。此处既补单步，也补 v7→v14 整链——用户从 v1.3.0（DB v7）升到 v1.4.0（DB v14）
 * 连跑 7 步，正是这条链上炸的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration13To14Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-13-to-14"
        const val DB_CHAIN = "migration-7-to-14-chain"

        /** 13→14 要加 isDemo 的表：含回收站两张，表名必须是复数 */
        val DEMO_TABLES = listOf(
            "bills", "assets", "wallets", "account_books", "goals", "goal_check_ins",
            "bills_recycle_bin", "assets_recycle_bin"
        )
    }

    /** 逐表 SELECT 新列：列不存在或表名写错都会在这里抛错 */
    private fun assertIsDemoColumns(db: SupportSQLiteDatabase) {
        for (table in DEMO_TABLES) {
            db.query("SELECT isDemo FROM `$table`").use { c -> assertEquals(0, c.count) }
        }
    }

    @Test
    fun migrate13To14_addsIsDemoToEveryTableIncludingRecycleBins() {
        helper.createDatabase(DB, 13).close()

        val migrated = helper.runMigrationsAndValidate(DB, 14, true, MIGRATION_13_14)

        assertIsDemoColumns(migrated)
        migrated.close()
    }

    @Test
    fun chain7To14_survivesTheWholeUpgradePath() {
        helper.createDatabase(DB_CHAIN, 7).close()

        val migrated = helper.runMigrationsAndValidate(
            DB_CHAIN, 14, true,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
        )

        assertIsDemoColumns(migrated)
        migrated.close()
    }
}
