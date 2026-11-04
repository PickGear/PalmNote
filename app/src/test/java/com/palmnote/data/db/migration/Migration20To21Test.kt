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
 * v20 → v21 迁移：`budgets` 从「一个月一行」重建为「长期预算模板」。
 *
 * 三件事必须成立：
 * 1. 新表结构齐全（period/scope/bookId/category/reminderEnabled）；
 * 2. 旧行被折成 `MONTH/TOTAL` 模板，金额与阈值原样保留；
 * 3. **真实与演示口径各留最近一条**——演示预算不能盖掉用户自己的预算，也不能被它盖掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration20To21Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun budgetColumns(dbName: String): Map<String, String?> {
        val map = mutableMapOf<String, String?>()
        helper.runMigrationsAndValidate(dbName, 21, true, MIGRATION_20_21).use { db ->
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
    fun `迁移后 budgets 是模板表结构`() {
        val dbName = "migration-20-to-21-schema"
        helper.createDatabase(dbName, 20).close()
        val cols = budgetColumns(dbName)
        listOf("period", "scope", "bookId", "category", "totalBudget", "reminderEnabled")
            .forEach { assertTrue("budgets 缺少 $it 列", cols.containsKey(it)) }
        // 旧的月份键与两个布尔提醒列已经被拆除
        assertTrue("budgets 不应再有 yearMonth", !cols.containsKey("yearMonth"))
        assertTrue("budgets 不应再有 reminderAtThreshold", !cols.containsKey("reminderAtThreshold"))
    }

    @Test
    fun `迁移保留每个口径最近一个月的额度`() {
        val dbName = "migration-20-to-21-data"
        helper.createDatabase(dbName, 20).use { db ->
            // 真实数据：1 月 2000、3 月 3500（最近）；演示：2 月 999
            db.execSQL(
                "INSERT INTO budgets (yearMonth, totalBudget, reminderThreshold, reminderAtThreshold, " +
                    "reminderOverBudget, createdAt, updatedAt, demoOrigin) " +
                    "VALUES ('2026-01', 200000, 0.8, 1, 1, 1, 1, 'NONE'), " +
                    "('2026-03', 350000, 0.7, 1, 0, 2, 2, 'NONE'), " +
                    "('2026-02', 99900, 0.9, 1, 1, 3, 3, 'SEED')"
            )
        }
        helper.runMigrationsAndValidate(dbName, 21, true, MIGRATION_20_21).use { db ->
            db.query(
                "SELECT period, scope, totalBudget, reminderThreshold, reminderEnabled, demoOrigin " +
                    "FROM budgets ORDER BY demoOrigin"
            ).use { c ->
                assertEquals("应只留真实与演示各一条", 2, c.count)
                // 第一条 = NONE（ORDER BY demoOrigin：NONE < SEED）
                c.moveToNext()
                assertEquals("MONTH", c.getString(0))
                assertEquals("TOTAL", c.getString(1))
                assertEquals(350000L, c.getLong(2))
                assertEquals(0.7, c.getDouble(3), 0.0001)
                assertEquals("任一提醒列开 → 通知开", 1, c.getInt(4))
                assertEquals("NONE", c.getString(5))
                // 第二条 = SEED
                c.moveToNext()
                assertEquals(99900L, c.getLong(2))
                assertEquals("SEED", c.getString(5))
            }
        }
    }
}
