package com.palmnote.ui.settings

import app.cash.turbine.test
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.repository.WalletRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var walletRepository: WalletRepository
    private lateinit var viewModel: WalletViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        walletRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `wallets exposes repository data`() = runTest {
        val wallets = listOf(
            Wallet(id = 1, name = "现金", type = "CASH"),
            Wallet(id = 2, name = "微信", type = "E_WALLET")
        )
        coEvery { walletRepository.getAllWallets() } returns MutableStateFlow(wallets)
        coEvery { walletRepository.getTotalBalance() } returns MutableStateFlow(null)

        viewModel = WalletViewModel(walletRepository)

        viewModel.wallets.test {
            assertEquals(emptyList<Wallet>(), awaitItem())
            val items = awaitItem()
            assertEquals(2, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wallets emits updated list after add`() = runTest {
        val walletFlow = MutableStateFlow(emptyList<Wallet>())
        coEvery { walletRepository.getAllWallets() } returns walletFlow
        coEvery { walletRepository.getTotalBalance() } returns MutableStateFlow(null)

        viewModel = WalletViewModel(walletRepository)

        viewModel.wallets.test {
            assertEquals(emptyList<Wallet>(), awaitItem())
            walletFlow.value = listOf(Wallet(id = 1, name = "新钱包", type = "CASH"))
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("新钱包", items[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totalBalance exposes repository data`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns MutableStateFlow(emptyList())
        coEvery { walletRepository.getTotalBalance() } returns MutableStateFlow(2500.0)

        viewModel = WalletViewModel(walletRepository)

        viewModel.totalBalance.test {
            assertEquals(null, awaitItem())
            val balance = awaitItem()
            assertEquals(2500.0, balance!!, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addWallet inserts wallet via repository`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns MutableStateFlow(emptyList())
        coEvery { walletRepository.getTotalBalance() } returns MutableStateFlow(null)

        viewModel = WalletViewModel(walletRepository)

        val wallet = Wallet(name = "支付宝", type = "E_WALLET")
        viewModel.addWallet(wallet)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { walletRepository.insert(wallet) }
    }

    @Test
    fun `deleteWallet soft-deletes via repository`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns MutableStateFlow(emptyList())
        coEvery { walletRepository.getTotalBalance() } returns MutableStateFlow(null)

        viewModel = WalletViewModel(walletRepository)

        viewModel.deleteWallet(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { walletRepository.softDelete(1L) }
    }

    @Test
    fun `setDefault delegates to repository`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns MutableStateFlow(emptyList())
        coEvery { walletRepository.getTotalBalance() } returns MutableStateFlow(null)

        viewModel = WalletViewModel(walletRepository)

        viewModel.setDefault(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { walletRepository.setDefault(1L) }
    }

    @Test
    fun `updateWallet updates with new timestamp`() = runTest {
        coEvery { walletRepository.getAllWallets() } returns MutableStateFlow(emptyList())
        coEvery { walletRepository.getTotalBalance() } returns MutableStateFlow(null)

        viewModel = WalletViewModel(walletRepository)

        val wallet = Wallet(id = 1, name = "现金", type = "CASH")
        viewModel.updateWallet(wallet)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { walletRepository.update(match { it.name == "现金" && it.updatedAt > 0 }) }
    }
}
