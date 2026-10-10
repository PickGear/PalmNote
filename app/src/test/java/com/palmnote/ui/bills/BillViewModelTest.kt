package com.palmnote.ui.bills

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.palmnote.PalmNoteApp
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.BudgetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // 报销关联：VM 构造时即订阅待报销列表，未打桩会抛 no answer found
        every { billRepository.getUnreimbursedBills() } returns flowOf(emptyList())
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
        // ViewModel 持有 stateIn(viewModelScope, WhileSubscribed(5000)) 的分享协程。
        // 必须与各测试的 runTest 共用 testDispatcher.scheduler：否则 runTest 结束时
        // Main 调度器上残留的 5 秒延迟任务会漏到下一测，表现为 UncaughtExceptionsBeforeTest。
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
            testDispatcher.scheduler.advanceUntilIdle()
        }
        Dispatchers.resetMain()
    }

    /** 全部测试共用 Main 的 scheduler，避免双调度器把协程残留给下一测。 */
    private fun runTestOnMain(block: suspend TestScope.() -> Unit) = runTest(testDispatcher.scheduler, testBody = block)

    @Test
    fun `initial state has correct defaults`() = runTestOnMain {
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(BillType.EXPENSE, viewModel.formState.value.type)
        assertEquals("", viewModel.formState.value.category)
        assertEquals(0L, state.monthlyExpense)
    }

    @Test
    fun `updateForm modifies form state`() = runTestOnMain {
        advanceUntilIdle()

        viewModel.updateForm { copy(category = "餐饮", amount = "50.0") }

        assertEquals("餐饮", viewModel.formState.value.category)
        assertEquals("50.0", viewModel.formState.value.amount)
    }

    @Test
    fun `resetForm resets amount note and merchant`() = runTestOnMain {
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
    fun `formState type resets to defaultBillType after resetForm`() = runTestOnMain {
        advanceUntilIdle()

        viewModel.updateForm { copy(type = BillType.INCOME, category = "工资") }
        viewModel.resetForm()

        assertEquals(BillType.EXPENSE, viewModel.formState.value.type)
        assertEquals("", viewModel.formState.value.category)
    }

    @Test
    fun `formState walletId resets to default wallet after resetForm`() = runTestOnMain {
        advanceUntilIdle()

        viewModel.updateForm { copy(walletId = 42L) }
        viewModel.resetForm()

        assertEquals(null, viewModel.formState.value.walletId)
    }

    @Test
    fun `formState date resets to now after resetForm`() = runTestOnMain {
        advanceUntilIdle()

        val testDate = 1700000000000L
        viewModel.updateForm { copy(date = testDate) }
        viewModel.resetForm()

        val resetDate = viewModel.formState.value.date
        assertTrue(resetDate in (System.currentTimeMillis() - 5000L)..(System.currentTimeMillis() + 5000L))
    }

    /**
     * issue#1 补充问题：从非默认账本点「记一笔」时，新账单会落进默认账本。
     * 成因是新建的 VM 里 selectedBookId 是 ALL_BOOKS 的默认值，init 里的 collect 会把它改成默认账本；
     * 修复是让 BillScreen 的 FAB 用一次性字段把用户当前账本交给 resetForm，save 时直接读它。
     */
    @Test
    fun `新账单写入当前选中的账本而不是默认账本`() = runTestOnMain {
        // 复现 issue#1 的场景：存在一个「日常」默认账本，而用户当前在另一个账本里点记一笔
        cachedAccountBooks.value = listOf(
            AccountBook(id = 1L, name = "日常", isDefault = true),
            AccountBook(id = 42L, name = "旅行")
        )
        coEvery { billRepository.createBillWithWalletAdjustment(any()) } returns 1L
        val saved = slot<Bill>()
        PalmNoteApp.pendingAddBillBookId = 42L

        viewModel.resetForm()
        viewModel.updateForm { copy(amount = "50.00", category = "餐饮") }
        viewModel.saveBill()
        advanceUntilIdle()

        coVerify { billRepository.createBillWithWalletAdjustment(capture(saved)) }
        assertEquals(42L, saved.captured.accountBookId)
        // 一次性：消费后必须清空，否则会影响下一个 VM
        assertNull(PalmNoteApp.pendingAddBillBookId)
    }
}
