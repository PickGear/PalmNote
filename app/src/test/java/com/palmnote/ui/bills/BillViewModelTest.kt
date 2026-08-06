package com.palmnote.ui.bills

import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.BudgetRepository
import com.palmnote.domain.model.BillType
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var billRepository: BillRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var accountBookRepository: AccountBookRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var cachedWallets: MutableStateFlow<List<Wallet>>
    private lateinit var cachedCategoryConfigs: MutableStateFlow<List<CategoryConfig>>
    private lateinit var cachedAccountBooks: MutableStateFlow<List<AccountBook>>
    private lateinit var context: android.content.Context
    private lateinit var viewModel: BillViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        billRepository = mockk(relaxUnitFun = true)
        budgetRepository = mockk(relaxUnitFun = true)
        accountBookRepository = mockk(relaxUnitFun = true)
        preferencesManager = mockk(relaxUnitFun = true)
        context = mockk(relaxUnitFun = true)
        cachedWallets = MutableStateFlow(emptyList())
        cachedCategoryConfigs = MutableStateFlow(emptyList())
        cachedAccountBooks = MutableStateFlow(emptyList())

        every { billRepository.getBillsByMonth(any()) } returns flowOf(emptyList())
        every { billRepository.getBillsByBookAndMonth(any(), any()) } returns flowOf(emptyList())
        every { billRepository.getMonthlyExpense(any()) } returns flowOf(null)
        every { billRepository.getMonthlyIncome(any()) } returns flowOf(null)
        every { billRepository.getMonthlyExpenseByBook(any(), any()) } returns flowOf(null)
        every { billRepository.getMonthlyIncomeByBook(any(), any()) } returns flowOf(null)
        every { billRepository.getExpenseByCategory(any()) } returns flowOf(emptyList())
        every { billRepository.getExpenseByCategoryByBook(any(), any()) } returns flowOf(emptyList())
        every { billRepository.getCategoryUsageCounts(any()) } returns flowOf(emptyList())
        every { budgetRepository.getBudgetByMonthFlow(any()) } returns flowOf(null)
        every { accountBookRepository.getAllBooksIncludingHidden() } returns flowOf(emptyList())
        every { preferencesManager.defaultBillType } returns flowOf("EXPENSE")
        every { preferencesManager.presetCategoryOverrides } returns flowOf(emptyMap())

        viewModel = BillViewModel(
            context, SavedStateHandle(), cachedWallets, cachedCategoryConfigs, cachedAccountBooks,
            billRepository, budgetRepository, accountBookRepository, preferencesManager
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
        assertEquals(BillType.EXPENSE, viewModel.formState.value.type)
        assertEquals("", viewModel.formState.value.category)
        assertEquals(0L, state.monthlyExpense)
    }

    @Test
    fun `updateForm modifies form state`() = runTest {
        advanceUntilIdle()

        viewModel.updateForm { copy(category = "餐饮", amount = "50.0") }

        assertEquals("餐饮", viewModel.formState.value.category)
        assertEquals("50.0", viewModel.formState.value.amount)
    }

    @Test
    fun `resetForm resets amount note and merchant`() = runTest {
        advanceUntilIdle()

        viewModel.updateForm {
            copy(amount = "100.0", note = "测试备注", merchant = "星巴克")
        }
        viewModel.resetForm()
        advanceUntilIdle()

        val state = viewModel.formState.value
        assertEquals("", state.amount)
        assertEquals("", state.note)
        assertEquals("", state.merchant)
    }

    @Test
    fun `formState type resets to defaultBillType after resetForm`() = runTest {
        advanceUntilIdle()

        viewModel.updateForm { copy(type = BillType.INCOME, category = "工资") }
        viewModel.resetForm()

        assertEquals(BillType.EXPENSE, viewModel.formState.value.type)
        assertEquals("", viewModel.formState.value.category)
    }

    @Test
    fun `formState walletId resets to default wallet after resetForm`() = runTest {
        advanceUntilIdle()

        viewModel.updateForm { copy(walletId = 42L) }
        viewModel.resetForm()

        assertEquals(null, viewModel.formState.value.walletId)
    }

    @Test
    fun `formState date resets to now after resetForm`() = runTest {
        advanceUntilIdle()

        val testDate = 1700000000000L
        viewModel.updateForm { copy(date = testDate) }
        viewModel.resetForm()

        val resetDate = viewModel.formState.value.date
        assertTrue(resetDate in (System.currentTimeMillis() - 5000L)..(System.currentTimeMillis() + 5000L))
    }
}
