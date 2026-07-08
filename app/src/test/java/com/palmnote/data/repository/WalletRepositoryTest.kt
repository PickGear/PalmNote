package com.palmnote.data.repository

import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.Wallet
import io.mockk.coEvery
import io.mockk.coVerify
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
    private lateinit var walletRepository: WalletRepository

    @Before
    fun setUp() {
        walletDao = mockk(relaxUnitFun = true)
        walletRepository = WalletRepository(walletDao)
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
        walletRepository.updateBalance(1, 500.0)

        coVerify { walletDao.updateBalance(1, 500.0, any()) }
    }

    @Test
    fun `adjustBalance adds amount`() = runTest {
        walletRepository.adjustBalance(1, 100.0)

        coVerify { walletDao.adjustBalance(1, 100.0, any()) }
    }

    @Test
    fun `adjustBalance subtracts amount`() = runTest {
        walletRepository.adjustBalance(1, -100.0)

        coVerify { walletDao.adjustBalance(1, -100.0, any()) }
    }

    @Test
    fun `setDefault clears all defaults then sets one`() = runTest {
        walletRepository.setDefault(1)

        coVerify { walletDao.clearAllDefaults(any()) }
        coVerify { walletDao.setDefault(1, any()) }
    }

    @Test
    fun `softDelete delegates to dao`() = runTest {
        walletRepository.softDelete(1)

        coVerify { walletDao.softDelete(1, any()) }
    }

    @Test
    fun `hardDelete delegates to dao`() = runTest {
        walletRepository.hardDelete(1)

        coVerify { walletDao.hardDelete(1) }
    }

    @Test
    fun `getAllWallets returns all wallets`() = runTest {
        val wallets = listOf(
            Wallet(id = 1, name = "现金", type = "CASH"),
            Wallet(id = 2, name = "已删除钱包", type = "CASH", isDeleted = true)
        )
        coEvery { walletDao.getAllWallets() } returns flowOf(wallets)

        val result = walletRepository.getAllWallets().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `getTotalBalance returns sum`() = runTest {
        coEvery { walletDao.getTotalBalance() } returns flowOf(1500.0)

        val result = walletRepository.getTotalBalance().first()

        assertEquals(1500.0, result!!, 0.001)
    }

    @Test
    fun `initDefaultWallets does nothing when default exists`() = runTest {
        coEvery { walletDao.getDefaultWallet() } returns Wallet(id = 1, name = "现金", type = "CASH", isDefault = true)

        walletRepository.initDefaultWallets()

        coVerify(exactly = 0) { walletDao.insert(any()) }
    }

    @Test
    fun `initDefaultWallets inserts 5 defaults when none exists`() = runTest {
        coEvery { walletDao.getDefaultWallet() } returns null
        coEvery { walletDao.insert(any()) } returnsMany listOf(1L, 2L, 3L, 4L, 5L)

        walletRepository.initDefaultWallets()

        coVerify(exactly = 5) { walletDao.insert(any()) }
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
