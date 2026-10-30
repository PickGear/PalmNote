package com.palmnote.data

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.BillType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 演示账本隔离的两条硬约束（示例模式「口径唯一」的支点）：
 *
 * 1. 示例账单必须落在**示例账本**里（账单页 / 报表 / 小组件都按账本取数，
 *    混进用户默认账本就等于隔离失效）；
 * 2. 关演示时，**演示期用户自己记的账单不能被连带删掉**——
 *    `clearDemoBills` 的谓词是「`isDemo = 1` 或账本属于示例账本」，
 *    少了毕业迁移这一步，用户演示期记的真账会随示例一起消失。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class WealthDemoGraduationTest {

    private lateinit var db: AppDatabase
    private lateinit var seeder: WealthDemoSeeder
    private val prefs = mockk<PreferencesManager>(relaxed = true)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        every { prefs.lifeDemoMode } returns flowOf(true)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun userBill(bookId: Long) = Bill(
        amount = 100L,
        type = BillType.EXPENSE,
        category = "餐饮",
        date = System.currentTimeMillis(),
        yearMonth = "2026-10",
        accountBookId = bookId,
        // v19 语义：演示期自建 = 仍属于演示数据集（isDemo = true），但来源是 USER
        isDemo = true,
        demoOrigin = com.palmnote.domain.model.DemoOrigin.USER
    )

    @Test
    fun `示例账单全部落在示例账本里`() = runBlocking {
        val realBookId = db.accountBookDao().insertBook(AccountBook(name = "我的账本", isDefault = true))

        seeder.reseed(prefs)

        val demoBookId = db.accountBookDao().demoBookId()
        assertNotNull("播种后必须存在示例账本", demoBookId)
        val demoBills = db.billDao().getAllBills().first()
        assertEquals("示例账单不该混进用户默认账本", 0, demoBills.count { it.accountBookId == realBookId })
        assertEquals("示例账单应全部在示例账本内", demoBills.size, demoBills.count { it.accountBookId == demoBookId })
    }

    @Test
    fun `关闭演示时演示期自建的账单被迁到真实账本而不是删掉`() = runBlocking {
        val realBookId = db.accountBookDao().insertBook(AccountBook(name = "我的账本", isDefault = true))
        seeder.reseed(prefs)
        val demoBookId = db.accountBookDao().demoBookId()!!

        // 演示期用户自己记的一笔（账本落在示例账本、但不是示例行）
        val mineId = db.billDao().insertBill(userBill(demoBookId))
        assertEquals(1, db.billDao().countUserBillsInDemoBooks())

        seeder.clearAll(prefs, keepUserCreated = true)

        assertNull("示例账本应被清理", db.accountBookDao().demoBookId())
        val survivor = db.billDao().getBillById(mineId)
        assertNotNull("演示期自建的账单不能随示例一起被删", survivor)
        assertEquals("自建账单应迁到真实账本", realBookId, survivor!!.accountBookId)
    }

    @Test
    fun `选一并删除时演示期自建的账单随示例清掉`() = runBlocking {
        db.accountBookDao().insertBook(AccountBook(name = "我的账本", isDefault = true))
        seeder.reseed(prefs)
        val demoBookId = db.accountBookDao().demoBookId()!!

        val mineId = db.billDao().insertBill(userBill(demoBookId))
        seeder.clearAll(prefs, keepUserCreated = false)

        assertNull(db.billDao().getBillById(mineId))
    }

    private fun userAsset() = com.palmnote.data.db.entity.Asset(
        name = "我的演示物品",
        category = "数码",
        purchasePrice = 1000L,
        acquisitionDate = System.currentTimeMillis(),
        isDemo = true,
        demoOrigin = com.palmnote.domain.model.DemoOrigin.USER
    )

    private fun userWallet() = com.palmnote.data.db.entity.Wallet(
        name = "我的演示钱包",
        type = "CASH",
        isDemo = true,
        demoOrigin = com.palmnote.domain.model.DemoOrigin.USER
    )

    @Test
    fun `关闭演示时演示期自建的物品与钱包毕业为真实行`() = runBlocking {
        db.accountBookDao().insertBook(AccountBook(name = "我的账本", isDefault = true))
        seeder.reseed(prefs)

        val assetId = db.assetDao().insertAsset(userAsset())
        val walletId = db.walletDao().insert(userWallet())
        assertEquals("毕业询问计数应覆盖物品与钱包", 2, seeder.countUserRows())

        val result = seeder.clearAll(prefs, keepUserCreated = true)

        val asset = db.assetDao().getAssetById(assetId)!!
        assertTrue("毕业物品应变成真实行", !asset.isDemo)
        assertEquals("毕业物品来源应为 NONE", com.palmnote.domain.model.DemoOrigin.NONE, asset.demoOrigin)
        val wallet = db.walletDao().getWalletById(walletId)!!
        assertTrue("毕业钱包应变成真实行", !wallet.isDemo)
        assertEquals("毕业钱包来源应为 NONE", com.palmnote.domain.model.DemoOrigin.NONE, wallet.demoOrigin)
        assertEquals("毕业结果计入保留数", 2, result.kept)
        assertEquals("示例种子照常清理（USER 不占移除数）", 0, db.assetDao().countDemoAssets())
    }

    @Test
    fun `选一并删除时演示期自建的物品与钱包清掉`() = runBlocking {
        db.accountBookDao().insertBook(AccountBook(name = "我的账本", isDefault = true))
        seeder.reseed(prefs)

        val assetId = db.assetDao().insertAsset(userAsset())
        val walletId = db.walletDao().insert(userWallet())
        val result = seeder.clearAll(prefs, keepUserCreated = false)

        assertNull("演示期自建物品应被清掉", db.assetDao().getAssetById(assetId))
        assertNull("演示期自建钱包应被清掉", db.walletDao().getWalletById(walletId))
        assertEquals("一并删除不计保留数", 0, result.kept)
    }
}
