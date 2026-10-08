package com.palmnote.data.db.migration

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
 * v14 → v15 迁移测试：给 `assets` / `assets_recycle_bin` 加保质期相关的四列。
 *
 * 验收口径：
 * - 四列都存在且**存量行一律 NULL**（未填写）——迁移不得凭空造出保质期；
 * - 存量行原有列（含 warrantyExpireDate / insuranceExpireDate）一字不动；
 * - 空表也要能迁移。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class Migration14To15Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private companion object {
        const val DB = "migration-14-to-15"

        /** 第二个测试用独立库名：同名库残留文件可能让两个测试互相影响（同 11→12 的教训）。 */
        const val DB_EMPTY = "migration-14-to-15-empty"

        const val SHELF_LIFE_COLUMNS =
            "shelfLifeExpireDate, shelfLifeProducedDate, shelfLifeDurationValue, shelfLifeDurationUnit"
    }

    private fun seedV14Asset(db: SupportSQLiteDatabase, id: Long, name: String, warranty: Long, insurance: Long) {
        db.execSQL(
            "INSERT INTO assets " +
                "(id, name, category, subCategory, brand, model, purchasePrice, acquisitionType, status, " +
                "costMode, quantity, useCount, totalUsageHours, location, room, purchaseChannel, " +
                "warrantyExpireDate, insuranceExpireDate, insuranceCompany, insurancePolicyNo, images, " +
                "description, condition, serialNumber, receiptPath, depreciationRate, currentValue, " +
                "maintenanceIntervalDays, maintenanceNotes, isFavorite, tags, retireReason, lostReason, " +
                "sortOrder, createdAt, updatedAt) " +
                "VALUES (?, ?, '数码', '', '', '', 100000, 'PURCHASE', 'HELD', 'DAILY', 1, 0, 0.0, '', '', '', " +
                "?, ?, '', '', '', '', 'GOOD', '', '', 0.0, 0, 0, '', 0, '', '', '', 0, 1690000000000, 1690000000000)",
            arrayOf<Any?>(id, name, warranty, insurance)
        )
    }

    @Test
    fun migrate14To15_addsNullColumnsAndKeepsExistingRows() {
        val seed = helper.createDatabase(DB, 14)
        seedV14Asset(seed, id = 1, name = "相机", warranty = 1700000000000, insurance = 1700000000001)
        seedV14Asset(seed, id = 2, name = "耳机", warranty = 1700000000002, insurance = 1700000000003)
        seed.close()

        val migrated = helper.runMigrationsAndValidate(DB, 15, true, MIGRATION_14_15)

        migrated.query(
            "SELECT name, warrantyExpireDate, insuranceExpireDate, $SHELF_LIFE_COLUMNS FROM assets WHERE id = 1"
        ).use { c ->
            assertTrue("期望存在 id=1 的物品", c.moveToFirst())
            assertEquals("相机", c.getString(0))
            assertEquals("存量保修日期不得被迁移改动", 1700000000000, c.getLong(1))
            assertEquals("存量保险日期不得被迁移改动", 1700000000001, c.getLong(2))
            for (i in 3..6) {
                assertTrue("第 ${i - 2} 个保质期列必须为 NULL（未填写）", c.isNull(i))
            }
        }

        migrated.query("SELECT COUNT(*) FROM assets").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("存量行不能被迁移丢掉", 2, c.getInt(0))
        }

        // 回收站表同样要加上这四列（空表 SELECT 即可证明列存在）
        migrated.query("SELECT $SHELF_LIFE_COLUMNS FROM assets_recycle_bin").use { c ->
            assertEquals(0, c.count)
        }
        migrated.close()
    }

    @Test
    fun migrate14To15_onEmptyTables_succeeds() {
        val seed = helper.createDatabase(DB_EMPTY, 14)
        seed.close()
        val migrated = helper.runMigrationsAndValidate(DB_EMPTY, 15, true, MIGRATION_14_15)
        migrated.query("SELECT COUNT(*) FROM assets").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT $SHELF_LIFE_COLUMNS FROM assets_recycle_bin").use { c ->
            assertEquals(0, c.count)
        }
        migrated.close()
    }
}
