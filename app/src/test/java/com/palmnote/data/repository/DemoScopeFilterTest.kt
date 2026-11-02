package com.palmnote.data.repository

import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.AssetStatus
import com.palmnote.domain.model.BillType
import com.palmnote.domain.model.DemoOrigin
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「演示模式 = 全 App 口径唯一」的仓库层守卫。
 *
 * 取数侧：**开关为真只看示例行、为假只看真实行**（`isDemo == demo` 的对称写法）。
 * 写成 `if (demo) 过滤 else 原样` 会在「关演示后仍有残留示例行」（清理失败 / 从旧备份恢复）
 * 时露馅——列表里冒出示例账本，而里面的示例账单/物品已经被隐藏，页面自相矛盾。
 *
 * 写入侧（2026-11-02 补）：编辑表单重建的实体不带 isDemo/demoOrigin（默认真实行），
 * 三处整实体 update（物品/账单/钱包）必须**原地保持示例行的身份**，
 * 否则演示列表瞬间丢行、真实数据凭空多行——用户实测「演示模式加的物品时隐时现」。
 */
class DemoScopeFilterTest {

    private fun prefs(demo: Boolean): PreferencesManager =
        mockk { every { lifeDemoMode } returns flowOf(demo) }

    private fun books() = listOf(
        AccountBook(id = 1, name = "我的账本", isDefault = true),
        AccountBook(id = 2, name = "示例账本", isDemo = true)
    )

    private fun accountBookRepo(demo: Boolean): AccountBookRepositoryImpl {
        val dao = mockk<AccountBookDao>(relaxUnitFun = true)
        every { dao.getAllBooks() } returns flowOf(books())
        return AccountBookRepositoryImpl(dao, mockk(), mockk(), mockk(), prefs(demo))
    }

    @Test
    fun `演示模式下账本列表只剩示例账本`() = runBlocking {
        val ids = accountBookRepo(demo = true).getAllBooks().first().map { it.id }
        assertEquals(listOf(2L), ids)
    }

    @Test
    fun `关演示后残留的示例账本同样不显示`() = runBlocking {
        val ids = accountBookRepo(demo = false).getAllBooks().first().map { it.id }
        assertEquals(listOf(1L), ids)
    }

    private fun assets() = listOf(
        Asset(id = 1, name = "我的相机", category = "DIGITAL", purchasePrice = 100L, status = AssetStatus.HELD),
        Asset(
            id = 2, name = "示例相机", category = "DIGITAL", purchasePrice = 200L,
            status = AssetStatus.HELD, isDemo = true
        )
    )

    private fun assetRepo(demo: Boolean): AssetRepositoryImpl {
        val dao = mockk<AssetDao>(relaxUnitFun = true)
        every { dao.getAllAssetsSortedByStatus() } returns flowOf(assets())
        return AssetRepositoryImpl(
            dao, mockk(), mockk(), mockk(), prefs(demo)
        )
    }

    @Test
    fun `演示模式下物品列表只剩示例物品`() = runBlocking {
        val ids = assetRepo(demo = true).getAllAssetsSortedByStatus().first().map { it.id }
        assertEquals(listOf(2L), ids)
    }

    @Test
    fun `关演示后残留的示例物品同样不显示`() = runBlocking {
        val ids = assetRepo(demo = false).getAllAssetsSortedByStatus().first().map { it.id }
        assertEquals(listOf(1L), ids)
    }

    private fun wallets() = listOf(
        Wallet(id = 1, name = "我的零钱", type = "E_WALLET", currentBalance = 100L),
        Wallet(id = 2, name = "示例零钱", type = "E_WALLET", currentBalance = 200L, isDemo = true)
    )

    private fun walletRepo(demo: Boolean): WalletRepositoryImpl {
        val dao = mockk<WalletDao>(relaxUnitFun = true)
        every { dao.getEnabledWallets() } returns flowOf(wallets())
        return WalletRepositoryImpl(dao, mockk(), mockk(), mockk(), mockk(), prefs(demo))
    }

    @Test
    fun `演示模式下钱包列表只剩示例钱包`() = runBlocking {
        val ids = walletRepo(demo = true).getEnabledWallets().first().map { it.id }
        assertEquals(listOf(2L), ids)
    }

    @Test
    fun `关演示后残留的示例钱包同样不显示`() = runBlocking {
        val ids = walletRepo(demo = false).getEnabledWallets().first().map { it.id }
        assertEquals(listOf(1L), ids)
    }

    // ==== 编辑口径：演示模式下编辑示例行必须原地保持示例身份 ====

    /** 建一个对齐测试仓库：`demo=true` 时预置库中已有行 [existing]，写库参数收进返回的列表。 */
    private fun assetRepoForUpdate(demo: Boolean, existing: Asset?): Pair<AssetRepositoryImpl, MutableList<Asset>> {
        val dao = mockk<AssetDao>(relaxUnitFun = true)
        if (demo) coEvery { dao.getAssetById(existing?.id ?: 0) } returns existing
        val saved = mutableListOf<Asset>()
        coEvery { dao.updateAsset(any()) } answers { saved.add(firstArg()) }
        return AssetRepositoryImpl(dao, mockk(), mockk(), mockk(), prefs(demo)) to saved
    }

    @Test
    fun `演示模式下编辑示例物品保持示例身份`() = runBlocking {
        val (repo, saved) = assetRepoForUpdate(
            demo = true,
            existing = Asset(id = 2, name = "示例相机", category = "DIGITAL", isDemo = true, demoOrigin = DemoOrigin.SEED)
        )
        repo.updateAsset(Asset(id = 2, name = "改名了", category = "DIGITAL"))
        assertEquals(true, saved.single().isDemo)
        assertEquals(DemoOrigin.SEED, saved.single().demoOrigin)
        assertEquals("改名了", saved.single().name)
    }

    @Test
    fun `演示模式下编辑真实物品不翻成示例`() = runBlocking {
        val (repo, saved) = assetRepoForUpdate(
            demo = true,
            existing = Asset(id = 1, name = "我的相机", category = "DIGITAL")
        )
        repo.updateAsset(Asset(id = 1, name = "改名了", category = "DIGITAL"))
        assertEquals(false, saved.single().isDemo)
        assertEquals(DemoOrigin.NONE, saved.single().demoOrigin)
    }

    @Test
    fun `关演示后编辑物品不做演示对齐`() = runBlocking {
        val (repo, saved) = assetRepoForUpdate(demo = false, existing = null)
        val edited = Asset(id = 1, name = "改名了", category = "DIGITAL")
        repo.updateAsset(edited)
        assertEquals(edited, saved.single())
    }

    @Test
    fun `演示模式下编辑示例账单保持示例身份`() = runBlocking {
        val dao = mockk<BillDao>(relaxUnitFun = true)
        val repo = BillRepositoryImpl(dao, mockk(), mockk(), mockk(), mockk(), prefs(demo = true))
        coEvery { dao.getBillById(2) } returns demoBill()
        val saved = mutableListOf<Bill>()
        coEvery { dao.updateBill(any()) } answers { saved.add(firstArg()) }
        repo.updateBill(Bill(id = 2, amount = 999, type = BillType.EXPENSE, category = "餐饮", date = 0, yearMonth = "2026-11"))
        assertEquals(true, saved.single().isDemo)
        assertEquals(DemoOrigin.USER, saved.single().demoOrigin)
    }

    @Test
    fun `演示模式下编辑示例钱包保持示例身份`() = runBlocking {
        val dao = mockk<WalletDao>(relaxUnitFun = true)
        val repo = WalletRepositoryImpl(dao, mockk(), mockk(), mockk(), mockk(), prefs(demo = true))
        coEvery { dao.getWalletById(2) } returns Wallet(
            id = 2, name = "示例零钱", type = "E_WALLET", currentBalance = 200L,
            isDemo = true, demoOrigin = DemoOrigin.SEED
        )
        val saved = mutableListOf<Wallet>()
        coEvery { dao.update(any()) } answers { saved.add(firstArg()) }
        repo.update(Wallet(id = 2, name = "改名了", type = "E_WALLET", currentBalance = 300L))
        assertEquals(true, saved.single().isDemo)
        assertEquals(DemoOrigin.SEED, saved.single().demoOrigin)
        assertEquals("改名了", saved.single().name)
    }

    private fun demoBill() = Bill(
        id = 2, amount = 100, type = BillType.EXPENSE, category = "餐饮",
        date = 0, yearMonth = "2026-11", isDemo = true, demoOrigin = DemoOrigin.USER
    )
}
