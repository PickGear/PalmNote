package com.palmnote.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.R
import com.palmnote.data.DataCache
import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.BudgetRepository
import com.palmnote.domain.repository.WalletRepository
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.components.CategoryItem
import com.palmnote.ui.components.toComposeColor
import androidx.compose.runtime.Stable
import com.palmnote.ui.theme.AppIcon
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch


@Stable
data class BillState(
    val bills: List<Bill> = emptyList(),
    val filteredBills: List<Bill> = emptyList(),
    val currentYearMonth: String = DateUtils.getCurrentYearMonth(),
    val selectedDay: Int? = null,
    val monthlyExpense: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val expenseByCategory: List<CategoryTotal> = emptyList(),
    val budget: Budget? = null,
    val budgetUsagePercent: Float = 0f,
    val dailySummary: Map<Int, Pair<Double, Double>> = emptyMap(),
    val selectedBookId: Long = AccountBook.ALL_BOOKS_ID,
    val accountBooks: List<AccountBook> = emptyList(),
    val allAccountBooks: List<AccountBook> = emptyList(),
    val wallets: Map<Long, String> = emptyMap(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val currentFilter: BillFilter = BillFilter(),
    val showFilterSheet: Boolean = false
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
    val toWalletId: Long? = null,
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

@OptIn(ExperimentalCoroutinesApi::class)
class BillViewModel(
    private val context: Context,
    private val cachedWallets: StateFlow<List<Wallet>>,
    private val cachedCategoryConfigs: StateFlow<List<CategoryConfig>>,
    private val cachedAccountBooks: StateFlow<List<AccountBook>>,
    private val billRepository: BillRepository,
    private val budgetRepository: BudgetRepository,
    private val walletRepository: WalletRepository,
    private val accountBookRepository: AccountBookRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _formState = MutableStateFlow(AddBillFormState())
    val formState: StateFlow<AddBillFormState> = _formState.asStateFlow()

    // ============ Ephemeral state (user actions) ============

    private val _selectedBookId = MutableStateFlow(AccountBook.ALL_BOOKS_ID)
    private val _currentYearMonth = MutableStateFlow(DateUtils.getCurrentYearMonth())
    private val _selectedDay = MutableStateFlow<Int?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _currentFilter = MutableStateFlow(BillFilter())
    private val _showFilterSheet = MutableStateFlow(false)
    private val _searchResults = MutableStateFlow<List<Bill>>(emptyList())

    // ============ Derived reactive data ============

    val wallets: StateFlow<List<Wallet>> = cachedWallets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customExpenseCategories: StateFlow<List<CategoryItem>> = cachedCategoryConfigs
        .map { configs -> configs.filter { it.type == "BILL_EXPENSE" && it.isEnabled }
            .map { CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customIncomeCategories: StateFlow<List<CategoryItem>> = cachedCategoryConfigs
        .map { configs -> configs.filter { it.type == "BILL_INCOME" && it.isEnabled }
            .map { CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomExpenseCategories: StateFlow<List<CategoryItem>> = cachedCategoryConfigs
        .map { configs -> configs.filter { it.type == "BILL_EXPENSE" }
            .map { CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomIncomeCategories: StateFlow<List<CategoryItem>> = cachedCategoryConfigs
        .map { configs -> configs.filter { it.type == "BILL_INCOME" }
            .map { CategoryItem(it.name, it.icon.imageVector, it.color.toComposeColor()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryUsageCounts: StateFlow<Map<String, Int>> = combine(
        billRepository.getCategoryUsageCounts("EXPENSE"),
        billRepository.getCategoryUsageCounts("INCOME")
    ) { expense, income ->
        (expense + income).groupBy({ it.category }, { it.count }).mapValues { it.value.sum() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val walletNames: StateFlow<Map<Long, String>> = wallets
        .map { it.associate { w -> w.id to com.palmnote.ui.components.getLocalizedWalletDisplayName(w, context) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val allAccountBooks: StateFlow<List<AccountBook>> = accountBookRepository
        .getAllBooksIncludingHidden()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val billData: StateFlow<BillDataGroup?> = combine(_selectedBookId, _currentYearMonth) { a, b -> a to b }
        .flatMapLatest { (bookId, ym) ->
            val isAll = bookId == AccountBook.ALL_BOOKS_ID
            combine(
                if (isAll) billRepository.getBillsByMonth(ym) else billRepository.getBillsByBookAndMonth(bookId, ym),
                if (isAll) billRepository.getMonthlyExpense(ym) else billRepository.getMonthlyExpenseByBook(bookId, ym),
                if (isAll) billRepository.getMonthlyIncome(ym) else billRepository.getMonthlyIncomeByBook(bookId, ym),
                if (isAll) billRepository.getExpenseByCategory(ym) else billRepository.getExpenseByCategoryByBook(bookId, ym),
                budgetRepository.getBudgetByMonthFlow(ym)
            ) { bills, expense, income, categories, budget ->
                BillDataGroup(bills, expense ?: 0.0, income ?: 0.0, categories, budget)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val dailySummary: StateFlow<Map<Int, Pair<Double, Double>>> = combine(_selectedBookId, _currentYearMonth) { a, b -> a to b }
        .flatMapLatest { (bookId, ym) ->
            val isAll = bookId == AccountBook.ALL_BOOKS_ID
            (if (isAll) billRepository.getDailySummary(ym) else billRepository.getDailySummaryByBook(bookId, ym))
                .map { daily ->
                    val cal = java.util.Calendar.getInstance()
                    daily.associate {
                        cal.timeInMillis = it.date
                        cal.get(java.util.Calendar.DAY_OF_MONTH) to Pair(it.expense, it.income)
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ============ Combined state ============

    val state: StateFlow<BillState> = combine(
        combine(_selectedBookId, _currentYearMonth, _selectedDay) { a, b, c -> Triple(a, b, c) },
        combine(_searchQuery, _isSearching) { a, b -> Pair(a, b) },
        combine(_currentFilter, _showFilterSheet) { a, b -> Pair(a, b) },
        combine(walletNames, cachedAccountBooks, allAccountBooks) { a, b, c -> Triple(a, b, c) },
        combine(billData, dailySummary, _searchResults) { a, b, c -> Triple(a, b, c) },
    ) { nav, searchPair, filterPair, walletPart, dataTriple ->
        val (bookId, ym, day) = nav
        val (query, searching) = searchPair
        val (filter, sheet) = filterPair
        val (wNames, books, allBooks) = walletPart
        val (data, daily, searchRes) = dataTriple

        val budgetPercent = if (data?.budget != null && data.budget.totalBudget > 0)
            (data.expense / data.budget.totalBudget).toFloat() else 0f

        val filterActive = filter.type != null || filter.category != null || filter.paymentMethod != null || filter.amountMin != null || filter.amountMax != null
        val filtered = if (searching) searchRes
        else if (!filterActive && query.isBlank()) emptyList()
        else (data?.bills ?: emptyList()).filter { bill ->
            (filter.type == null || bill.type == filter.type) &&
            (filter.category == null || bill.category == filter.category) &&
            (filter.paymentMethod == null || bill.paymentMethod == filter.paymentMethod) &&
            (filter.amountMin == null || bill.amount >= filter.amountMin) &&
            (filter.amountMax == null || bill.amount <= filter.amountMax) &&
            (query.isBlank() || bill.note.contains(query, true) || bill.merchant.contains(query, true) ||
             bill.location.contains(query, true) || bill.category.contains(query, true) || bill.amount.toString().contains(query, true))
        }

        BillState(
            bills = data?.bills ?: emptyList(),
            filteredBills = filtered,
            currentYearMonth = ym,
            selectedDay = day,
            monthlyExpense = data?.expense ?: 0.0,
            monthlyIncome = data?.income ?: 0.0,
            expenseByCategory = data?.categories ?: emptyList(),
            budget = data?.budget,
            budgetUsagePercent = budgetPercent,
            dailySummary = daily,
            selectedBookId = bookId,
            accountBooks = books,
            allAccountBooks = allBooks,
            wallets = wNames,
            searchQuery = query,
            isSearching = searching,
            currentFilter = filter,
            showFilterSheet = sheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillState())

    private var defaultBillType = "EXPENSE"
    private var searchJob: Job? = null

    init {
        DataCache.get<BillState>("bill")?.let { cached ->
            _currentYearMonth.value = cached.currentYearMonth
            _selectedDay.value = cached.selectedDay
            _selectedBookId.value = cached.selectedBookId
            _searchQuery.value = cached.searchQuery
            _currentFilter.value = cached.currentFilter
        }
        if (_selectedDay.value == null && _currentYearMonth.value == DateUtils.getCurrentYearMonth()) {
            _selectedDay.value = DateUtils.getDayOfMonth(System.currentTimeMillis())
        }
        viewModelScope.launch {
            preferencesManager.defaultBillType.collect { defaultBillType = it }
        }
        viewModelScope.launch {
            cachedAccountBooks.collect { books ->
                val selectedId = _selectedBookId.value
                if (selectedId == AccountBook.ALL_BOOKS_ID) {
                    val default = books.find { it.isDefault } ?: books.firstOrNull()
                    if (default != null) _selectedBookId.value = default.id
                } else if (books.isNotEmpty() && books.none { it.id == selectedId }) {
                    val default = books.find { it.isDefault } ?: books.first()
                    _selectedBookId.value = default.id
                }
            }
        }
        viewModelScope.launch {
            state.drop(1).collect { DataCache.set("bill", it) }
        }
    }

    fun selectAccountBook(bookId: Long) {
        _selectedBookId.value = bookId
        _currentFilter.value = BillFilter()
        _searchResults.value = emptyList()
    }

    fun addAccountBook(name: String, icon: AppIcon, color: String, description: String = "", bookType: String = "CUSTOM") {
        viewModelScope.launch {
            val book = AccountBook(name = name, icon = icon, color = color, description = description, bookType = bookType)
            _selectedBookId.value = accountBookRepository.insertBook(book)
        }
    }

    fun updateAccountBook(book: AccountBook) {
        viewModelScope.launch { accountBookRepository.updateBook(book) }
    }

    fun hideAccountBook(bookId: Long) {
        viewModelScope.launch { accountBookRepository.setHidden(bookId, true) }
    }

    fun unhideAccountBook(bookId: Long) {
        viewModelScope.launch { accountBookRepository.setHidden(bookId, false) }
    }

    fun deleteAccountBookWithData(bookId: Long) {
        viewModelScope.launch {
            if (bookId == AccountBook.ALL_BOOKS_ID) return@launch
            billRepository.softDeleteByBook(bookId)
            accountBookRepository.softDeleteBook(bookId)
        }
    }

    fun setDefaultBook(bookId: Long) {
        viewModelScope.launch { accountBookRepository.setDefault(bookId) }
    }

    fun setMonth(yearMonth: String) {
        val day = if (yearMonth == DateUtils.getCurrentYearMonth()) DateUtils.getDayOfMonth(System.currentTimeMillis()) else null
        _currentYearMonth.value = yearMonth
        _selectedDay.value = day
        _currentFilter.value = BillFilter()
    }

    fun setSelectedDay(day: Int?) { _selectedDay.value = day }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _isSearching.value = query.isNotBlank()
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
        } else {
            searchJob = viewModelScope.launch {
                delay(300)
                _searchResults.value = billRepository.search(query)
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _isSearching.value = false
        _searchResults.value = emptyList()
        searchJob?.cancel()
    }

    fun setFilterType(type: String) {
        _currentFilter.value = _currentFilter.value.copy(type = if (type == "ALL") null else type)
    }

    fun toggleFilterSheet() { _showFilterSheet.value = !_showFilterSheet.value }

    fun syncDateFromSaved(date: Long) {
        _currentYearMonth.value = DateUtils.formatYearMonth(date)
        _selectedDay.value = DateUtils.getDayOfMonth(date)
    }

    fun applyFilter(filter: BillFilter) { _currentFilter.value = filter }

    fun clearFilter() { _currentFilter.value = BillFilter() }

    fun deleteBill(billId: Long) {
        viewModelScope.launch { billRepository.softDeleteBill(billId) }
    }

    fun initFormForEdit(billId: Long) {
        viewModelScope.launch {
            val bill = billRepository.getBillById(billId) ?: return@launch
            _selectedBookId.value = bill.accountBookId
            _formState.value = AddBillFormState(
                id = bill.id,
                amount = bill.amount.toString(),
                type = bill.type,
                category = bill.category,
                subCategory = bill.subCategory,
                note = bill.note,
                date = bill.date,
                walletId = bill.walletId,
                toWalletId = bill.toWalletId,
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

    fun resetForm(selectedDate: Long? = null) {
        val date = selectedDate ?: System.currentTimeMillis()
        val walletId = cachedWallets.value.find { it.isDefault }?.id ?: cachedWallets.value.firstOrNull()?.id
        _formState.value = AddBillFormState(date = date, type = defaultBillType, walletId = walletId)
    }

    fun updateForm(update: AddBillFormState.() -> AddBillFormState) {
        _formState.value = _formState.value.update()
    }

    fun saveBill() {
        val form = _formState.value
        if (form.amount.isBlank() || form.amount.replace(",", "").toDoubleOrNull() == null) {
            _formState.value = form.copy(amountError = context.getString(R.string.bill_error_amount_required))
            return
        }
        if (form.category.isBlank() && form.type != "TRANSFER") {
            _formState.value = form.copy(categoryError = context.getString(R.string.bill_error_category_required))
            return
        }
        if (form.type == "TRANSFER" && form.walletId == null) {
            _formState.value = form.copy(amountError = context.getString(R.string.transfer_from_required))
            return
        }
        if (form.type == "TRANSFER" && form.toWalletId == null) {
            _formState.value = form.copy(amountError = context.getString(R.string.transfer_to_required))
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
                    accountBookId = state.value.selectedBookId,
                    walletId = form.walletId,
                    toWalletId = if (form.type == "TRANSFER") form.toWalletId else null,
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
                                "EXPENSE" -> { if (oldBill.walletId != null) walletRepository.adjustBalance(oldBill.walletId, oldBill.amount) }
                                "INCOME" -> { if (oldBill.walletId != null) walletRepository.adjustBalance(oldBill.walletId, -oldBill.amount) }
                                "TRANSFER" -> {
                                    if (oldBill.walletId != null) walletRepository.adjustBalance(oldBill.walletId, oldBill.amount)
                                    if (oldBill.toWalletId != null) walletRepository.adjustBalance(oldBill.toWalletId, -oldBill.amount)
                                }
                            }
                            when (form.type) {
                                "EXPENSE" -> form.walletId?.let { walletRepository.adjustBalance(it, -amount) }
                                "INCOME" -> form.walletId?.let { walletRepository.adjustBalance(it, amount) }
                                "TRANSFER" -> {
                                    form.walletId?.let { walletRepository.adjustBalance(it, -amount) }
                                    form.toWalletId?.let { walletRepository.adjustBalance(it, amount) }
                                }
                            }
                        }
                    }
                } else {
                    billRepository.insertBill(bill)
                    when (form.type) {
                        "EXPENSE" -> form.walletId?.let { walletRepository.adjustBalance(it, -amount) }
                        "INCOME" -> form.walletId?.let { walletRepository.adjustBalance(it, amount) }
                        "TRANSFER" -> {
                            form.walletId?.let { walletRepository.adjustBalance(it, -amount) }
                            form.toWalletId?.let { walletRepository.adjustBalance(it, amount) }
                        }
                    }
                }

                _formState.value = form.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(isSaving = false)
            }
        }
    }

    fun saveBudget(budget: Budget) {
        viewModelScope.launch {
            if (budget.id > 0) budgetRepository.updateBudget(budget)
            else budgetRepository.insertBudget(budget)
        }
    }
}
