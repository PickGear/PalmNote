package com.palmnote.ui.bills

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.DailySummary
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar


@Stable
data class ReportData(
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val billCount: Int = 0,
    val avgDaily: Double = 0.0,
    val categories: List<CategoryTotal> = emptyList(),
    val dailySummary: List<DailySummary> = emptyList(),
    val monthlyTrend: List<MonthTotal> = emptyList()
)

@Stable
data class ReportState(
    val periodTab: Int = 1,
    val incomeExpenseTab: Int = 0,
    val currentYearMonth: String = DateUtils.getCurrentYearMonth(),
    val currentYear: String = Calendar.getInstance().get(Calendar.YEAR).toString(),
    val weekStart: Long = DateUtils.getWeekStart(),
    val weekEnd: Long = DateUtils.getWeekEnd(),
    val data: ReportData = ReportData(),
    val isLoading: Boolean = false,
    val selectedBookId: Long = -1L
)

class ReportViewModel(
    private val billRepository: BillRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    private var dataJob: Job? = null

    init { loadData() }

    fun setPeriodTab(tab: Int) { _state.value = _state.value.copy(periodTab = tab); loadData() }
    fun setIncomeExpenseTab(tab: Int) { _state.value = _state.value.copy(incomeExpenseTab = tab); loadData() }
    fun setSelectedBookId(bookId: Long) { _state.value = _state.value.copy(selectedBookId = bookId); loadData() }

    fun previousMonth() {
        // Bug fix: validate format before parsing to avoid IndexOutOfBoundsException
        val parts = _state.value.currentYearMonth.split("-")
        if (parts.size != 2) return
        val year = parts[0].toIntOrNull() ?: return
        val month = parts[1].toIntOrNull() ?: return
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.add(Calendar.MONTH, -1)
        _state.value = _state.value.copy(currentYearMonth = DateUtils.formatYearMonth(cal.timeInMillis))
        loadData()
    }

    fun nextMonth() {
        val parts = _state.value.currentYearMonth.split("-")
        if (parts.size != 2) return
        val year = parts[0].toIntOrNull() ?: return
        val month = parts[1].toIntOrNull() ?: return
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.add(Calendar.MONTH, 1)
        _state.value = _state.value.copy(currentYearMonth = DateUtils.formatYearMonth(cal.timeInMillis))
        loadData()
    }

    fun previousWeek() {
        val cal = Calendar.getInstance().apply { timeInMillis = _state.value.weekStart }
        cal.add(Calendar.WEEK_OF_YEAR, -1)
        _state.value = _state.value.copy(
            weekStart = DateUtils.getWeekStartForDate(cal.timeInMillis),
            weekEnd = DateUtils.getWeekEndForDate(cal.timeInMillis)
        )
        loadData()
    }

    fun nextWeek() {
        val cal = Calendar.getInstance().apply { timeInMillis = _state.value.weekEnd }
        cal.add(Calendar.DAY_OF_MONTH, 1)
        _state.value = _state.value.copy(
            weekStart = DateUtils.getWeekStartForDate(cal.timeInMillis),
            weekEnd = DateUtils.getWeekEndForDate(cal.timeInMillis)
        )
        loadData()
    }

    fun previousYear() {
        _state.update { it.copy(currentYear = (it.currentYear.toIntOrNull()?.minus(1)?.toString() ?: it.currentYear)) }
        loadData()
    }

    fun nextYear() {
        _state.update { it.copy(currentYear = (it.currentYear.toIntOrNull()?.plus(1)?.toString() ?: it.currentYear)) }
        loadData()
    }

    private fun loadData() {
        dataJob?.cancel()
        dataJob = viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                val s = _state.value
                val isExpense = s.incomeExpenseTab == 0
                when (s.periodTab) {
                    0 -> loadWeeklyData(isExpense)
                    1 -> loadMonthlyData(isExpense)
                    2 -> loadYearlyData(isExpense)
                }
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadWeeklyData(isExpense: Boolean) {
        val s = _state.value.weekStart; val e = _state.value.weekEnd
        val bookId = _state.value.selectedBookId
        val isAllBooks = bookId == -1L
        try {
            combine(
                if (isAllBooks) billRepository.getWeeklyBillCount(s, e) else billRepository.getWeeklyBillCountByBook(bookId, s, e),
                if (isAllBooks) billRepository.getWeeklyExpense(s, e) else billRepository.getWeeklyExpenseByBook(bookId, s, e),
                if (isAllBooks) billRepository.getWeeklyIncome(s, e) else billRepository.getWeeklyIncomeByBook(bookId, s, e),
                if (isAllBooks) {
                    if (isExpense) billRepository.getWeeklyExpenseByCategory(s, e) else billRepository.getWeeklyIncomeByCategory(s, e)
                } else {
                    if (isExpense) billRepository.getWeeklyExpenseByCategoryByBook(bookId, s, e) else billRepository.getWeeklyIncomeByCategoryByBook(bookId, s, e)
                },
                if (isAllBooks) billRepository.getWeeklyDailySummary(s, e) else billRepository.getWeeklyDailySummaryByBook(bookId, s, e)
            ) { billCount, expense, income, categories, daily ->
                val days = daily.filter { if (isExpense) it.expense > 0 else it.income > 0 }
                val avg = if (days.isNotEmpty()) (if (isExpense) expense ?: 0.0 else income ?: 0.0) / days.size else 0.0
                ReportData(expense ?: 0.0, income ?: 0.0, billCount, avg, categories, daily)
            }.collect { reportData -> _state.update { it.copy(data = reportData) } }
        } catch (e: Exception) { /* Log but don't crash */ }
    }

    private suspend fun loadMonthlyData(isExpense: Boolean) {
        val ym = _state.value.currentYearMonth
        val bookId = _state.value.selectedBookId
        val isAllBooks = bookId == -1L
        try {
            combine(
                if (isAllBooks) billRepository.getMonthlyBillCount(ym) else billRepository.getMonthlyBillCountByBook(bookId, ym),
                if (isAllBooks) billRepository.getMonthlyExpense(ym) else billRepository.getMonthlyExpenseByBook(bookId, ym),
                if (isAllBooks) billRepository.getMonthlyIncome(ym) else billRepository.getMonthlyIncomeByBook(bookId, ym),
                if (isAllBooks) {
                    if (isExpense) billRepository.getExpenseByCategory(ym) else billRepository.getIncomeByCategory(ym)
                } else {
                    if (isExpense) billRepository.getExpenseByCategoryByBook(bookId, ym) else billRepository.getIncomeByCategoryByBook(bookId, ym)
                },
                if (isAllBooks) billRepository.getDailySummary(ym) else billRepository.getDailySummaryByBook(bookId, ym)
            ) { billCount, expense, income, categories, daily ->
                val days = daily.filter { if (isExpense) it.expense > 0 else it.income > 0 }
                val avg = if (days.isNotEmpty()) (if (isExpense) expense ?: 0.0 else income ?: 0.0) / days.size else 0.0
                ReportData(expense ?: 0.0, income ?: 0.0, billCount, avg, categories, daily)
            }.collect { reportData -> _state.update { it.copy(data = reportData) } }
        } catch (e: Exception) { /* Log but don't crash */ }
    }

    private suspend fun loadYearlyData(isExpense: Boolean) {
        val year = _state.value.currentYear
        val bookId = _state.value.selectedBookId
        val isAllBooks = bookId == -1L
        try {
            combine(
                if (isAllBooks) billRepository.getYearlyExpense(year) else billRepository.getYearlyExpenseByBook(bookId, year),
                if (isAllBooks) billRepository.getYearlyIncome(year) else billRepository.getYearlyIncomeByBook(bookId, year),
                if (isAllBooks) billRepository.getYearlyExpenseTrend(year) else billRepository.getYearlyExpenseTrendByBook(bookId, year),
                if (isAllBooks) billRepository.getYearlyIncomeTrend(year) else billRepository.getYearlyIncomeTrendByBook(bookId, year),
                if (isAllBooks) {
                    if (isExpense) billRepository.getYearlyExpenseByCategory(year) else billRepository.getYearlyIncomeByCategory(year)
                } else {
                    if (isExpense) billRepository.getYearlyExpenseByCategoryByBook(bookId, year) else billRepository.getYearlyIncomeByCategoryByBook(bookId, year)
                }
            ) { expense, income, expTrend, incTrend, categories ->
                ReportData(
                    expense ?: 0.0, income ?: 0.0, 0, 0.0,
                    categories.map { CategoryTotal(it.category, it.total) },
                    emptyList(),
                    if (isExpense) expTrend else incTrend
                )
            }.collect { reportData -> _state.update { it.copy(data = reportData) } }
        } catch (e: Exception) {
            // Log but don't crash
        }
    }
}
