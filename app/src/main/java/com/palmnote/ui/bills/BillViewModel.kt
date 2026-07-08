package com.palmnote.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.R
import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.BudgetRepository
import com.palmnote.domain.repository.CategoryConfigRepository
import com.palmnote.domain.repository.WalletRepository
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.CategoryItem
import com.palmnote.ui.components.toComposeColor
import androidx.compose.runtime.Stable
import com.palmnote.ui.theme.AppIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
data class BillState(
    val bills: List<Bill> = emptyList(),
    val currentYearMonth: String = DateUtils.getCurrentYearMonth(),
    val monthlyExpense: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val expenseByCategory: List<CategoryTotal> = emptyList(),
    val budget: Budget? = null,
    val budgetUsagePercent: Float = 0f,
    val dailySummary: Map<Int, Pair<Double, Double>> = emptyMap(),
    val selectedBookId: Long = AccountBook.ALL_BOOKS_ID,
    val accountBooks: List<AccountBook> = emptyList(),
    val allAccountBooks: List<AccountBook> = emptyList(),
    val wallets: Map<Long, String> = emptyMap() // walletId -> walletName
)

private data class BillDataGroup(
    val bills: List<Bill>,
    val expense: Double,
    val income: Double,
    val categories: List<CategoryTotal>,
    val budget: Budget?
)

@Stable
data class AddBillFormState(
    val id: Long? = null,
    val amount: String = "",
    val type: String = "EXPENSE",
    val category: String = "",
    val subCategory: String = "",
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val walletId: Long? = null,
    val paymentMethod: String = "",
    val merchant: String = "",
    val location: String = "",
    val tags: String = "",
    val images: String = "",
    val isReimbursable: Boolean = false,
    val isTaxDeductible: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val amountError: String? = null,
    val categoryError: String? = null
)

@HiltViewModel
class BillViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billRepository: BillRepository,
    private val budgetRepository: BudgetRepository,
    private val walletRepository: WalletRepository,
    private val accountBookRepository: AccountBookRepository,
    private val categoryConfigRepository: CategoryConfigRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(BillState())
    val state: StateFlow<BillState> = _state.asStateFlow()

    private val _formState = MutableStateFlow(AddBillFormState())
    val formState: StateFlow<AddBillFormState> = _formState.asStateFlow()

    private val _wallets = MutableStateFlow<List<Wallet>>(emptyList())
    val wallets: StateFlow<List<Wallet>> = _wallets.asStateFlow()

    private val _customExpenseCategories = MutableStateFlow<List<CategoryItem>>(emptyList())
    val customExpenseCategories: StateFlow<List<CategoryItem>> = _customExpenseCategories.asStateFlow()

    private val _customIncomeCategories = MutableStateFlow<List<CategoryItem>>(emptyList())
    val customIncomeCategories: StateFlow<List<CategoryItem>> = _customIncomeCategories.asStateFlow()

    private var defaultBillType = "EXPENSE"
    private var billDataJob: Job? = null

    init {
        viewModelScope.launch { accountBookRepository.initDefaultBooks() }
        loadAccountBooks()
        loadAllAccountBooks()
        loadWallets()
        loadCustomCategories()
        viewModelScope.launch {
            preferencesManager.defaultBillType.collect { defaultBillType = it }
        }
    }

    private fun loadCustomCategories() {
        viewModelScope.launch {
            categoryConfigRepository.getAllCategories().collect { configs ->
                val expenseCategories = configs.filter { it.type == "BILL_EXPENSE" && it.isEnabled }
                    .map { CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
                val incomeCategories = configs.filter { it.type == "BILL_INCOME" && it.isEnabled }
                    .map { CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) }
                _customExpenseCategories.value = expenseCategories
                _customIncomeCategories.value = incomeCategories
            }
        }
    }

    private fun loadAccountBooks() {
        viewModelScope.launch {
            accountBookRepository.getAllBooks().collect { books ->
                // Bug fix: snapshot state once to avoid race condition
                val current = _state.value
                _state.value = current.copy(accountBooks = books)
                val selectedId = current.selectedBookId
                if (books.isNotEmpty()) {
                    if (selectedId == AccountBook.ALL_BOOKS_ID) {
                        val defaultBook = books.find { it.isDefault }
                        if (defaultBook != null) {
                            _state.value = _state.value.copy(selectedBookId = defaultBook.id)
                            loadBillData()
                        }
                    } else if (books.none { it.id == selectedId }) {
                        val defaultBook = books.find { it.isDefault }
                        _state.value = _state.value.copy(selectedBookId = defaultBook?.id ?: books.first().id)
                        loadBillData()
                    }
                }
            }
        }
    }

    private fun loadAllAccountBooks() {
        viewModelScope.launch {
            accountBookRepository.getAllBooksIncludingHidden().collect { books ->
                _state.value = _state.value.copy(allAccountBooks = books)
            }
        }
    }

    fun selectAccountBook(bookId: Long) {
        _state.value = _state.value.copy(selectedBookId = bookId)
        loadBillData()
    }

    fun addAccountBook(name: String, icon: AppIcon, color: String, description: String = "", bookType: String = "CUSTOM") {
        viewModelScope.launch {
            val book = AccountBook(name = name, icon = icon, color = color, description = description, bookType = bookType)
            val id = accountBookRepository.insertBook(book)
            _state.value = _state.value.copy(selectedBookId = id)
            loadBillData()
        }
    }

    fun updateAccountBook(book: AccountBook) {
        viewModelScope.launch {
            accountBookRepository.updateBook(book)
        }
    }

    fun hideAccountBook(bookId: Long) {
        viewModelScope.launch {
            accountBookRepository.setHidden(bookId, true)
            if (_state.value.selectedBookId == bookId) {
                val books = _state.value.accountBooks.filter { it.id != bookId }
                if (books.isNotEmpty()) {
                    val default = books.find { it.isDefault } ?: books.first()
                    _state.value = _state.value.copy(selectedBookId = default.id)
                    loadBillData()
                }
            }
        }
    }

    fun unhideAccountBook(bookId: Long) {
        viewModelScope.launch {
            accountBookRepository.setHidden(bookId, false)
            if (_state.value.selectedBookId == bookId) {
                loadBillData()
            }
        }
    }

    fun deleteAccountBook(bookId: Long) {
        viewModelScope.launch {
            if (bookId == AccountBook.ALL_BOOKS_ID) return@launch
            accountBookRepository.softDeleteBook(bookId)
            // Bug fix: use accountBooks (visible only) to avoid selecting hidden books
            val remaining = _state.value.accountBooks.filter { it.id != bookId }
            val selectedId = if (remaining.isNotEmpty()) {
                remaining.find { it.isDefault }?.id ?: remaining.first().id
            } else {
                AccountBook.ALL_BOOKS_ID
            }
            _state.value = _state.value.copy(selectedBookId = selectedId)
            loadBillData()
        }
    }

    fun setDefaultBook(bookId: Long) {
        viewModelScope.launch {
            accountBookRepository.setDefault(bookId)
        }
    }

    private fun loadWallets() {
        viewModelScope.launch {
            walletRepository.initDefaultWallets()
            walletRepository.getEnabledWallets().collect { wallets ->
                _wallets.value = wallets
                val walletMap = wallets.associate { it.id to com.palmnote.ui.components.getLocalizedWalletDisplayName(it, context) }
                _state.value = _state.value.copy(wallets = walletMap)
            }
        }
    }

    private fun loadBillData() {
        billDataJob?.cancel()
        // Bug fix: snapshot state once to avoid race conditions
        val currentState = _state.value
        val yearMonth = currentState.currentYearMonth
        val bookId = currentState.selectedBookId
        val isAllBooks = bookId == AccountBook.ALL_BOOKS_ID
        billDataJob = viewModelScope.launch {
            val billDataFlow = combine(
                if (isAllBooks) billRepository.getBillsByMonth(yearMonth) else billRepository.getBillsByBookAndMonth(bookId, yearMonth),
                if (isAllBooks) billRepository.getMonthlyExpense(yearMonth) else billRepository.getMonthlyExpenseByBook(bookId, yearMonth),
                if (isAllBooks) billRepository.getMonthlyIncome(yearMonth) else billRepository.getMonthlyIncomeByBook(bookId, yearMonth),
                if (isAllBooks) billRepository.getExpenseByCategory(yearMonth) else billRepository.getExpenseByCategoryByBook(bookId, yearMonth),
                budgetRepository.getBudgetByMonthFlow(yearMonth)
            ) { bills, expense, income, categories, budget ->
                BillDataGroup(bills, expense ?: 0.0, income ?: 0.0, categories, budget)
            }

            val dailyFlow = if (isAllBooks) billRepository.getDailySummary(yearMonth) else billRepository.getDailySummaryByBook(bookId, yearMonth)

            combine(billDataFlow, dailyFlow) { billData, dailySummary ->
                val budgetPercent = if (billData.budget != null && billData.budget.totalBudget > 0) {
                    (billData.expense / billData.budget.totalBudget).toFloat()
                } else 0f

                val calendar = java.util.Calendar.getInstance()
                val dailyMap = dailySummary.associate { summary ->
                    calendar.timeInMillis = summary.date
                    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                    day to Pair(summary.expense, summary.income)
                }

                // Bug fix: use _state.update to avoid overwriting accountBooks/allAccountBooks
                _state.update {
                    it.copy(
                        bills = billData.bills,
                        currentYearMonth = yearMonth,
                        monthlyExpense = billData.expense,
                        monthlyIncome = billData.income,
                        expenseByCategory = billData.categories,
                        budget = billData.budget,
                        budgetUsagePercent = budgetPercent,
                        dailySummary = dailyMap,
                        selectedBookId = bookId
                    )
                }
            }.collect { }
        }
    }

    fun setMonth(yearMonth: String) {
        _state.value = _state.value.copy(currentYearMonth = yearMonth)
        loadBillData()
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch {
            billRepository.softDeleteBill(billId)
            loadBillData()
        }
    }

    fun initFormForEdit(billId: Long) {
        viewModelScope.launch {
            val bill = billRepository.getBillById(billId) ?: return@launch
            _state.value = _state.value.copy(selectedBookId = bill.accountBookId)
            _formState.value = AddBillFormState(
                id = bill.id,
                amount = bill.amount.toString(),
                type = bill.type,
                category = bill.category,
                subCategory = bill.subCategory,
                note = bill.note,
                date = bill.date,
                walletId = bill.walletId,
                paymentMethod = bill.paymentMethod,
                merchant = bill.merchant,
                location = bill.location,
                tags = bill.tags,
                images = bill.images,
                isReimbursable = bill.isReimbursable,
                isTaxDeductible = bill.isTaxDeductible,
                latitude = bill.latitude,
                longitude = bill.longitude,
                createdAt = bill.createdAt,
                isEditing = true
            )
        }
    }

    fun resetForm() {
        viewModelScope.launch {
            val defaultWallet = walletRepository.getDefaultWallet()
            _formState.value = AddBillFormState(type = defaultBillType, walletId = defaultWallet?.id)
        }
    }

    fun updateForm(update: AddBillFormState.() -> AddBillFormState) {
        _formState.value = _formState.value.update()
    }

    fun saveBill() {
        val form = _formState.value
        // Bug fix: handle locale-formatted numbers (e.g., "1,000.50")
        if (form.amount.isBlank() || form.amount.replace(",", "").toDoubleOrNull() == null) {
            _formState.value = form.copy(amountError = context.getString(R.string.bill_error_amount_required))
            return
        }
        if (form.category.isBlank()) {
            _formState.value = form.copy(categoryError = context.getString(R.string.bill_error_category_required))
            return
        }

        _formState.value = form.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val amount = form.amount.replace(",", "").toDoubleOrNull() ?: run {
                    _formState.value = form.copy(isSaving = false, amountError = context.getString(R.string.bill_error_amount_required))
                    return@launch
                }
                val now = System.currentTimeMillis()
                val bill = Bill(
                    id = form.id ?: 0L,
                    amount = amount,
                    type = form.type,
                    category = form.category,
                    subCategory = form.subCategory,
                    note = form.note.trim(),
                    date = form.date,
                    yearMonth = DateUtils.formatYearMonth(form.date),
                    accountBookId = _state.value.selectedBookId,
                    walletId = form.walletId,
                    paymentMethod = form.paymentMethod,
                    merchant = form.merchant.trim(),
                    location = form.location.trim(),
                    tags = form.tags,
                    images = form.images,
                    isReimbursable = form.isReimbursable,
                    isTaxDeductible = form.isTaxDeductible,
                    latitude = form.latitude,
                    longitude = form.longitude,
                    createdAt = if (form.isEditing) form.createdAt else now,
                    updatedAt = now
                )

                if (form.isEditing) {
                    val oldBill = billRepository.getBillById(bill.id)
                    billRepository.updateBill(bill)
                    if (oldBill != null) {
                        val amountChanged = oldBill.amount != amount
                        val typeChanged = oldBill.type != form.type
                        val walletChanged = oldBill.walletId != form.walletId
                        if (amountChanged || typeChanged || walletChanged) {
                            when (oldBill.type) {
                                "EXPENSE" -> {
                                    if (oldBill.walletId != null) walletRepository.adjustBalance(oldBill.walletId, oldBill.amount)
                                }
                                "INCOME" -> {
                                    if (oldBill.walletId != null) walletRepository.adjustBalance(oldBill.walletId, -oldBill.amount)
                                }
                            }
                            when (form.type) {
                                "EXPENSE" -> form.walletId?.let { walletRepository.adjustBalance(it, -amount) }
                                "INCOME" -> form.walletId?.let { walletRepository.adjustBalance(it, amount) }
                            }
                        }
                    }
                } else {
                    billRepository.insertBill(bill)
                    when (form.type) {
                        "EXPENSE" -> form.walletId?.let { walletRepository.adjustBalance(it, -amount) }
                        "INCOME" -> form.walletId?.let { walletRepository.adjustBalance(it, amount) }
                    }
                }

                _formState.value = form.copy(isSaving = false, isSaved = true)
                loadBillData()
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isSaving = false)
            }
        }
    }

    fun saveBudget(budget: Budget) {
        viewModelScope.launch {
            if (budget.id > 0) {
                budgetRepository.updateBudget(budget)
            } else {
                budgetRepository.insertBudget(budget)
            }
            loadBillData()
        }
    }
}
