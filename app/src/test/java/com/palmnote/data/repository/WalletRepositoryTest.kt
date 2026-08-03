package com.palmnote.data.repository

import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.Wallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WalletRepositoryTest {

    private lateinit var walletDao: WalletDao
    private lateinit var walletRepository: WalletRepositoryImpl
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        walletDao = mockk(relaxUnitFun = true)
        context = mockk(relaxUnitFun = true)

        // 模拟Context.getString()
        every { context.getString(any()) } returns ""
        every { context.getString(any(), any()) } returns ""

        // appDatabase 仅占位：withTransaction 类方法不在本单测覆盖（迁移测试在 androidTest 层）
        walletRepository = WalletRepositoryImpl(walletDao, mockk(), mockk<AppDatabase>(relaxUnitFun = true), context)
    }

    @Test
    fun `getEnabledWallets returns only enabled wallets`() = runTest {
        val wallets = listOf(
            Wallet(id = 1, name = "现金", type = "CASH", isEnabled = true),
            Wallet(id = 2, name = "微信", type = "E_WALLET", isEnabled = true)
        )
        coEvery { walletDao.getEnabledWallets() } returns flowOf(wallets)

        val result = walletRepository.getEnabledWallets().first()

        assertEquals(2, result.size)
        assertEquals("现金", result[0].name)
        coVerify { walletDao.getEnabledWallets() }
    }

    @Test
    fun `getWalletById returns wallet when exists`() = runTest {
        val wallet = Wallet(id = 1, name = "现金", type = "CASH")
        coEvery { walletDao.getWalletById(1) } returns wallet

        val result = walletRepository.getWalletById(1)

        assertNotNull(result)
        assertEquals("现金", result?.name)
    }

    @Test
    fun `getWalletById returns null when not exists`() = runTest {
        coEvery { walletDao.getWalletById(999) } returns null

        val result = walletRepository.getWalletById(999)

        assertNull(result)
    }

    @Test
    fun `insert returns generated id`() = runTest {
        val wallet = Wallet(name = "新钱包", type = "CASH")
        coEvery { walletDao.insert(wallet) } returns 42L

        val id = walletRepository.insert(wallet)

        assertEquals(42L, id)
        coVerify { walletDao.insert(wallet) }
    }

    @Test
    fun `update delegates to dao`() = runTest {
        val wallet = Wallet(id = 1, name = "现金", type = "CASH")

        walletRepository.update(wallet)

        coVerify { walletDao.update(wallet) }
    }

    @Test
    fun `updateBalance delegates to dao`() = runTest {
        walletRepository.updateBalance(1, 50000)

        coVerify { walletDao.updateBalance(1, 50000, any()) }
    }

    @Test
    fun `adjustBalance adds amount`() = runTest {
        walletRepository.adjustBalance(1, 10000)

        coVerify { walletDao.adjustBalance(1, 10000, any()) }
    }

    @Test
    fun `adjustBalance subtracts amount`() = runTest {
        walletRepository.adjustBalance(1, -10000)

        coVerify { walletDao.adjustBalance(1, -10000, any()) }
    }

    @Test
    fun `setDefault calls transactional setAsDefault`() = runTest {
        walletRepository.setDefault(1)

        coVerify { walletDao.setAsDefault(1, any()) }
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        walletRepository.delete(1)

        coVerify { walletDao.deleteWallet(1) }
    }

    @Test
    fun `getAllWallets returns all wallets`() = runTest {
        val wallets = listOf(
            Wallet(id = 1, name = "现金", type = "CASH"),
            Wallet(id = 2, name = "微信", type = "E_WALLET")
        )
        coEvery { walletDao.getAllWallets() } returns flowOf(wallets)

        val result = walletRepository.getAllWallets().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `getTotalBalance returns sum`() = runTest {
        coEvery { walletDao.getTotalBalance() } returns flowOf(150000L)

        val result = walletRepository.getTotalBalance().first()

        assertEquals(150000L, result)
    }

    @Test
    fun `setEnabled delegates to dao`() = runTest {
        walletRepository.setEnabled(1, false)

        coVerify { walletDao.setEnabled(1, false, any()) }
    }

    @Test
    fun `getEnabledWalletCount returns count`() = runTest {
        coEvery { walletDao.getEnabledWalletCount() } returns flowOf(3)

        val result = walletRepository.getEnabledWalletCount().first()

        assertEquals(3, result)
    }
}
