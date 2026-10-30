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
 * v19 → v20 迁移：`budgets` 加「来源」列 `demoOrigin`，默认 `NONE`。
 *
 * 空库迁移是老用户的常态路径（绝大多数人没设过预算），不炸即合格；
 * 列缺失会让预算写入静默失败或落在「不属于任何来源」的灰色状态，所以要钉住列与默认值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration19To20Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /** 读迁移后 `budgets` 的列定义（列名 → 默认值）。 */
    private fun budgetColumns(dbName: String): Map<String, String?> {
        val map = mutableMapOf<String, String?>()
        helper.runMigrationsAndValidate(dbName, 20, true, MIGRATION_19_20).use { db ->
            db.query("PRAGMA table_info(budgets)").use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                val dfltIdx = c.getColumnIndexOrThrow("dflt_value")
                while (c.moveToNext()) {
                    map[c.getString(nameIdx)] = c.getString(dfltIdx)
                }
            }
        }
        return map
    }

    @Test
    fun `迁移后 budgets 有 demoOrigin 且默认 NONE`() {
        val dbName = "migration-19-to-20"
        helper.createDatabase(dbName, 19).close()
        val cols = budgetColumns(dbName)
        assertTrue("budgets 缺少 demoOrigin 列", cols.containsKey("demoOrigin"))
        assertEquals("'NONE'", cols["demoOrigin"])
    }
}
