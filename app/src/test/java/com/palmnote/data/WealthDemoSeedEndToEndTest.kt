package com.palmnote.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.model.DemoOrigin
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * 演示播种的**端到端**验证：类型化种子 → 播种器 → 内存 Room（Robolectric + 真实 assets）。
 *
 * 种子对象层的结构/不变量由 [DemoDataIntegrityTest] 守住；这里钉的是「跑通真实链路」：
 * - **英文环境**下播种后，库里的展示值必须是映射后的英文（DemoTexts 层真实生效，
 *   而不是只在映射表里存在）；
 * - 全部账单落在**本月**（预算预警的月钳制在真实写入路径上成立）；
 * - 示例图真的从 assets 落到了 filesDir（WebP 语义名 ↔ [DemoImageStore.materialize] 对得上）；
 * - 打标（isDemo / demoOrigin=SEED）与笔数与种子一一对应。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "en")
class WealthDemoSeedEndToEndTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var seeder: WealthDemoSeeder
    private val prefs = mockk<PreferencesManager>(relaxed = true)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = WealthDemoSeeder(
            context = context,
            billDao = db.billDao(),
            assetDao = db.assetDao(),
            walletDao = db.walletDao(),
            budgetDao = db.budgetDao(),
            bookDao = db.accountBookDao(),
            accountBookDao = db.accountBookDao(),
            billRecycleBinDao = db.billRecycleBinDao(),
            assetRecycleBinDao = db.assetRecycleBinDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seeding writes localized english rows end to end`() = runBlocking {
        val inserted = seeder.reseed(prefs)
        // 账单 22 + 物品 13 + 预算 2（本月总预算 + 餐饮分类预算；钱包 / 账本不计入返回值）
        assertEquals(demoBillSeeds.size + demoAssetSeeds.size + 2, inserted)

        val thisMonth = "%04d-%02d".format(LocalDate.now().year, LocalDate.now().monthValue)

        // 账单：笔数一致、打标正确、全部在本月、商户已映射成英文
        val bills = db.billDao().getAllBills().first()
        assertEquals(demoBillSeeds.size, bills.size)
        assertTrue(bills.all { it.isDemo && it.demoOrigin == DemoOrigin.SEED })
        assertTrue(
            "账单划出了本月：${bills.filter { it.yearMonth != thisMonth }.map { it.merchant }}",
            bills.all { it.yearMonth == thisMonth }
        )
        assertTrue("英文环境下商户仍是中文", bills.none { it.merchant.contains('早') || it.merchant.contains('餐') })
        assertTrue("映射后的英文商户缺失", bills.any { it.merchant == "Breakfast stall" })

        // 物品：13 件、英文名、示例图全部落到 filesDir（demo_ 前缀）
        val assets = db.assetDao().getAllAssets().first()
        assertEquals(demoAssetSeeds.size, assets.size)
        assertTrue(assets.all { it.isDemo && it.demoOrigin == DemoOrigin.SEED })
        assertTrue("映射后的英文物品名缺失", assets.any { it.name == "Badminton racket" })
        assertTrue("英文环境下物品名仍是中文", assets.none { it.name.contains('拍') || it.name.contains('猫') })
        val withImages = assets.filter { it.images.isNotBlank() }
        assertEquals("应每件物品都有示例图", demoAssetSeeds.size, withImages.size)

        val imageDir = File(context.filesDir, "images")
        val materialized = imageDir.listFiles()?.filter { it.name.startsWith("demo_") } ?: emptyList()
        val uniqueNames = demoAssetSeeds.flatMap { it.images }.toSet()
        assertEquals("落地的示例图数量与种子引用不一致", uniqueNames.size, materialized.size)

        // 示例账本与钱包也是英文（DemoTexts 既有路径）
        val books = db.accountBookDao().getAllBooksIncludingHidden().first()
        assertTrue(books.any { it.isDemo && it.name == "Sample book" })
        val wallets = db.walletDao().getAllWallets().first()
        assertTrue(wallets.any { it.isDemo && it.name == "WeChat Balance" })
    }
}
