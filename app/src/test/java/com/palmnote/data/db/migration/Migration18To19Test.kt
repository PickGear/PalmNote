package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v18 → v19 迁移：给演示数据加来源列 `demoOrigin`。
 *
 * 本测守两件事（空库即可验）：
 * 1. 三张表都真的加上了列，且默认值 `NONE` —— 漏一张表，那张表的写入会静默失败或落在
 *    「不属于任何来源」的灰色状态；
 * 2. 空库迁移不炸（老用户库可能一条演示数据都没有，这条路最常见）。
 *
 * **未覆盖**：`isDemo = 1` 行回填成 `SEED` 的那条 UPDATE。要验它得把 bills / assets / wallets
 * 全部 NOT NULL 列都喂进 INSERT，成本远高于收益；该路径靠真机 18→19 升级验，见提交说明。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration18To19Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun columnsOf(dbName: String, table: String): Map<String, Pair<String, String?>> {
        val map = mutableMapOf<String, Pair<String, String?>>()
        helper.createDatabase(dbName, 19).use { db ->
            db.query("PRAGMA table_info($table)").use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                val typeIdx = c.getColumnIndexOrThrow("type")
                val dfltIdx = c.getColumnIndexOrThrow("dflt_value")
                while (c.moveToNext()) {
                    map[c.getString(nameIdx)] = c.getString(typeIdx) to c.getString(dfltIdx)
                }
            }
        }
        return map
    }

    @Test
    fun `迁移后三张表都有 demoOrigin 且默认 NONE`() {
        val dbName = "migration-18-to-19"
        // 先建一个 v18 空库，再迁到 19（runMigrationsAndValidate 会按 schema 校验）
        helper.createDatabase(dbName, 18).close()
        helper.runMigrationsAndValidate(dbName, 19, true, MIGRATION_18_19).use { /* 立即关闭 */ }

        for (table in listOf("bills", "assets", "wallets")) {
            val cols = columnsOf(dbName, table)
            val col = cols["demoOrigin"]
            assertTrue("$table 缺少 demoOrigin 列", col != null)
            assertEquals("$table.demoOrigin 默认值应为 NONE", "'NONE'", col?.second)
        }
    }
}
