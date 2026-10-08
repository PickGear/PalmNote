package com.palmnote.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.Asset
import com.palmnote.domain.model.ShelfLifeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「删除 → 回收站 → 恢复」这一趟，保质期那四列必须原样跟着走。
 *
 * 两个 mapper（`toRecycleBin` / `toAsset`）是逐字段手写的：漏掉一个字段的后果是
 * 「删了再恢复，保质期就没了」——静默丢数据，界面上看不出来，所以这里用真实 Room 跑一遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AssetRecycleBinShelfLifeTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: AssetRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AssetRepositoryImpl(db.assetDao(), db.assetRecycleBinDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `删除再恢复后保质期四列都还在`() = runBlocking {
        val assetId = db.assetDao().insertAsset(
            Asset(
                name = "牛奶",
                category = "食品",
                shelfLifeExpireDate = 1_800_000_000_000,
                shelfLifeProducedDate = 1_700_000_000_000,
                shelfLifeDurationValue = 12,
                shelfLifeDurationUnit = ShelfLifeUnit.MONTH.value
            )
        )

        repo.deleteAsset(assetId)
        assertNull("删除后物品表里不该还有它", db.assetDao().getAssetById(assetId))
        val binId = db.assetRecycleBinDao().getAll().first().single().id

        repo.restoreAsset(binId)

        val restored = db.assetDao().getAssetById(assetId)
        assertNotNull("恢复后应当回到物品表，且用原来的 id", restored)
        assertEquals("到期日", 1_800_000_000_000, restored!!.shelfLifeExpireDate)
        assertEquals("生产日期", 1_700_000_000_000, restored.shelfLifeProducedDate)
        assertEquals("时长", 12, restored.shelfLifeDurationValue)
        assertEquals("单位", ShelfLifeUnit.MONTH.value, restored.shelfLifeDurationUnit)
    }

    @Test
    fun `没填保质期的物品恢复后这四列仍是 null`() = runBlocking {
        val assetId = db.assetDao().insertAsset(Asset(name = "耳机", category = "DIGITAL"))
        repo.deleteAsset(assetId)
        val binId = db.assetRecycleBinDao().getAll().first().single().id

        repo.restoreAsset(binId)

        val restored = db.assetDao().getAssetById(assetId)!!
        assertNull(restored.shelfLifeExpireDate)
        assertNull(restored.shelfLifeProducedDate)
        assertNull(restored.shelfLifeDurationValue)
        assertNull(restored.shelfLifeDurationUnit)
    }
}
