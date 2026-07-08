package com.palmnote.domain.repository

import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.CategoryTotalWithCount
import com.palmnote.data.db.dao.DailySummary
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.data.db.entity.Bill
import kotlinx.coroutines.flow.Flow

interface BillRepository {
    fun getAllBills(): Flow<List<Bill>>
    suspend fun getBillById(id: Long): Bill?
    fun getBillsByMonth(yearMonth: String): Flow<List<Bill>>
    fun getBillsByMonthAndType(yearMonth: String, type: String): Flow<List<Bill>>
    fun getMonthlyBillCount(yearMonth: String): Flow<Int>
    fun getMonthlyExpense(yearMonth: String): Flow<Double?>
    fun getMonthlyIncome(yearMonth: String): Flow<Double?>
    fun getTotalExpense(): Flow<Double?>
    fun getTotalIncome(): Flow<Double?>
    fun getExpenseByCategory(yearMonth: String): Flow<List<CategoryTotal>>
    fun getIncomeByCategory(yearMonth: String): Flow<List<CategoryTotal>>
    fun getMonthlyExpenseTrend(): Flow<List<MonthTotal>>
    fun getMonthlyIncomeTrend(): Flow<List<MonthTotal>>
    fun getDailySummary(yearMonth: String): Flow<List<DailySummary>>
    fun getBillsByDate(date: Long): Flow<List<Bill>>
    fun getDeletedBills(): Flow<List<Bill>>
    fun getAllYearMonths(): Flow<List<String>>
    suspend fun insertBill(bill: Bill): Long
    suspend fun updateBill(bill: Bill)
    suspend fun softDeleteBill(id: Long)
    suspend fun restoreBill(id: Long)
    suspend fun hardDeleteBill(id: Long)
    suspend fun search(query: String): List<Bill>
    fun getYearlyExpenseByCategory(year: String): Flow<List<CategoryTotalWithCount>>
    fun getYearlyIncomeByCategory(year: String): Flow<List<CategoryTotalWithCount>>
    fun getYearlyExpense(year: String): Flow<Double?>
    fun getYearlyIncome(year: String): Flow<Double?>
    fun getYearlyExpenseTrend(year: String): Flow<List<MonthTotal>>
    fun getYearlyIncomeTrend(year: String): Flow<List<MonthTotal>>
    fun getWeeklyExpense(startDate: Long, endDate: Long): Flow<Double?>
    fun getWeeklyIncome(startDate: Long, endDate: Long): Flow<Double?>
    fun getWeeklyBillCount(startDate: Long, endDate: Long): Flow<Int>
    fun getWeeklyExpenseByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>>
    fun getWeeklyIncomeByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>>
    fun getWeeklyDailySummary(startDate: Long, endDate: Long): Flow<List<DailySummary>>
    fun getBillsByBookAndMonth(bookId: Long, yearMonth: String): Flow<List<Bill>>
    fun getMonthlyBillCountByBook(bookId: Long, yearMonth: String): Flow<Int>
    fun getMonthlyExpenseByBook(bookId: Long, yearMonth: String): Flow<Double?>
    fun getMonthlyIncomeByBook(bookId: Long, yearMonth: String): Flow<Double?>
    fun getExpenseByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>>
    fun getIncomeByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>>
    fun getDailySummaryByBook(bookId: Long, yearMonth: String): Flow<List<DailySummary>>
    fun getWeeklyExpenseByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Double?>
    fun getWeeklyIncomeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Double?>
    fun getWeeklyBillCountByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Int>
    fun getWeeklyExpenseByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>
    fun getWeeklyIncomeByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>
    fun getWeeklyDailySummaryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<DailySummary>>
    fun getYearlyExpenseByBook(bookId: Long, year: String): Flow<Double?>
    fun getYearlyIncomeByBook(bookId: Long, year: String): Flow<Double?>
    fun getYearlyExpenseTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>>
    fun getYearlyIncomeTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>>
    fun getYearlyExpenseByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>>
    fun getYearlyIncomeByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>>
}
