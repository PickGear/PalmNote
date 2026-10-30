package com.palmnote.data.repository

import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.AccountBookDao
import com.palmnote.data.db.dao.AssetDao
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.AssetStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「演示模式 = 全 App 口径唯一」的取数层守卫。
 *
 * 这条规则靠三处仓库的同一套过滤实现：**开关为真只看示例行、为假只看真实行**
 * （`isDemo == demo` 的对称写法）。写成 `if (demo) 过滤 else 原样` 会在
 * 「关演示后仍有残留示例行」（清理失败 / 从旧备份恢复）时露馅——列表里冒出示例账本，
 * 而里面的示例账单/物品已经被隐藏，页面自相矛盾。
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
}
