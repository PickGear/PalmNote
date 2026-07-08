package com.palmnote.ui.bills

import com.palmnote.data.db.entity.Bill
import com.palmnote.data.repository.BillRepository
import com.palmnote.data.repository.BudgetRepository
import com.palmnote.data.repository.WalletRepository
import com.palmnote.data.repository.AccountBookRepository
import com.palmnote.data.repository.CategoryConfigRepository
import com.palmnote.data.datastore.PreferencesManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var billRepository: BillRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var walletRepository: WalletRepository
    private lateinit var accountBookRepository: AccountBookRepository
    private lateinit var categoryConfigRepository: CategoryConfigRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: BillViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        billRepository = mockk(relaxUnitFun = true)
        budgetRepository = mockk(relaxUnitFun = true)
        walletRepository = mockk(relaxUnitFun = true)
        accountBookRepository = mockk(relaxUnitFun = true)
        categoryConfigRepository = mockk(relaxUnitFun = true)
        preferencesManager = mockk(relaxUnitFun = true)

        coEvery { preferencesManager.defaultBillType } returns kotlinx.coroutines.flow.flowOf("EXPENSE")

        viewModel = BillViewModel(
            billRepository, budgetRepository, walletRepository,
            accountBookRepository, categoryConfigRepository, preferencesManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct defaults`() = runTest {
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("EXPENSE", viewModel.formState.value.type)
        assertEquals("", viewModel.formState.value.category)
        assertEquals(0.0, state.monthlyExpense, 0.01)
    }

    @Test
    fun `updateForm modifies form state`() = runTest {
        advanceUntilIdle()

        viewModel.updateForm { copy(category = "餐饮", amount = "50.0") }

        assertEquals("餐饮", viewModel.formState.value.category)
        assertEquals("50.0", viewModel.formState.value.amount)
    }

    @Test
    fun `clearAfterSave resets amount note and merchant`() = runTest {
        advanceUntilIdle()

        viewModel.updateForm {
            copy(amount = "100.0", note = "测试备注", merchant = "星巴克")
        }
        viewModel.clearAfterSave()

        val state = viewModel.formState.value
        assertEquals("", state.amount)
        assertEquals("", state.note)
        assertEquals("", state.merchant)
    }

    @Test
    fun `formState preserves type after clearAfterSave`() = runTest {
        advanceUntilIdle()

        viewModel.updateForm { copy(type = "INCOME", category = "工资") }
        viewModel.clearAfterSave()

        assertEquals("INCOME", viewModel.formState.value.type)
        assertEquals("工资", viewModel.formState.value.category)
    }

    @Test
    fun `formState preserves walletId after clearAfterSave`() = runTest {
        advanceUntilIdle()

        viewModel.updateForm { copy(walletId = 42L) }
        viewModel.clearAfterSave()

        assertEquals(42L, viewModel.formState.value.walletId)
    }

    @Test
    fun `formState preserves date after clearAfterSave`() = runTest {
        advanceUntilIdle()

        val testDate = 1700000000000L
        viewModel.updateForm { copy(date = testDate) }
        viewModel.clearAfterSave()

        assertEquals(testDate, viewModel.formState.value.date)
    }
}
