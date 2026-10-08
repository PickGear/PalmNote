package com.palmnote.data.export

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.Asset
import com.palmnote.domain.model.ShelfLifeUnit
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId

/**
 * 保质期到期日经 CSV 导出再导入后必须原样回来。
 *
 * 这条链路两头都不是「照着字段清单手抄」：导出靠反射枚举实体字段、导入靠中文表头反查字段名。
 * 所以往返测试是它唯一像样的证据——单看代码只能证明"我加了那一行"。
 *
 * 它同时钉住了导出侧「实体类型」那一列的位置：那一列一度是**顶掉**第一个字段的值而不是加在行首，
 * 于是整行少一格、第 2 列起全部左移（物品的「名称」会拿到「分类」的值，账单的「金额」会拿到「类型」的值）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AssetExpiryCsvRoundTripTest {

    private lateinit var db: AppDatabase

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 导出会读这三条偏好；relaxed mock 返回的是空 Flow，`first()` 会抛 NoSuchElementException。 */
    private fun preferences(): PreferencesManager = mockk(relaxed = true) {
        every { themeMode } returns flowOf("SYSTEM")
        every { defaultBillType } returns flowOf("EXPENSE")
        every { budgetReminderEnabled } returns flowOf(true)
    }

    private fun exporterFor(database: AppDatabase) =
        CsvDataExporter(context, database, preferences())

    private fun midnightOf(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `保质期各列往返后保留`() = runBlocking {
        val shelfLife = midnightOf(2026, 11, 20)
        val warranty = midnightOf(2027, 3, 1)
        val produced = midnightOf(2025, 11, 20)
        db.assetDao().insertAsset(
            Asset(
                name = "牛奶",
                category = "食品",
                shelfLifeExpireDate = shelfLife,
                shelfLifeProducedDate = produced,
                shelfLifeDurationValue = 12,
                shelfLifeDurationUnit = ShelfLifeUnit.MONTH.value,
                warrantyExpireDate = warranty
            )
        )
        // 对照组：直接填到期日的物品，三列期限字段必须是 NULL
        db.assetDao().insertAsset(Asset(name = "矿泉水", category = "食品", shelfLifeExpireDate = shelfLife))

        val exportUri = Uri.parse("content://test/export.zip")
        val exported = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(exportUri, exported)
        exporterFor(db).exportToUri(exportUri).getOrThrow()

        // 导进一个空库：能对上才说明字段真的过了 CSV 这一趟
        val fresh = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val importUri = Uri.parse("content://test/import.zip")
            shadowOf(context.contentResolver)
                .registerInputStream(importUri, ByteArrayInputStream(exported.toByteArray()))
            exporterFor(fresh).importFromUri(importUri).getOrThrow()

            val assets = fresh.assetDao().getAllAssets().first()
            val milk = assets.single { it.name == "牛奶" }
            assertEquals("第一个字段不能被「实体类型」顶掉", "食品", milk.category)
            assertEquals("保质期到期日必须往返保留", shelfLife, milk.shelfLifeExpireDate)
            assertEquals("生产日期必须往返保留", produced, milk.shelfLifeProducedDate)
            assertEquals("保质期时长必须往返保留", 12, milk.shelfLifeDurationValue)
            assertEquals("保质期单位必须往返保留", ShelfLifeUnit.MONTH.value, milk.shelfLifeDurationUnit)
            assertEquals("同批次的保修日期也要保留", warranty, milk.warrantyExpireDate)

            val water = assets.single { it.name == "矿泉水" }
            assertNull("没按期限录的物品，三列必须保持 NULL 而不是空串", water.shelfLifeDurationUnit)
            assertNull(water.shelfLifeDurationValue)
            assertNull(water.shelfLifeProducedDate)
        } finally {
            fresh.close()
        }
    }
}
