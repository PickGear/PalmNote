package com.palmnote.domain.repository

import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.CategoryTotalWithCount
import com.palmnote.data.db.dao.DailySummary
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.BillType
import com.palmnote.domain.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * 按本地日聚合账单为每日收支。
 * 参考主流记账 App（Cashew/Veri Fin）做法：存完整时间戳、在应用层按本地时区分组，
 * 避免 SQL 按 UTC 日分组导致的跨时区错位。
 */
fun List<Bill>.groupToDailySummaries(): List<DailySummary> =
    groupBy { DateUtils.millisToLocalDate(it.date) }
        .map { (day, bills) ->
            DailySummary(
                date = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                expense = bills.filter { it.type == BillType.EXPENSE }.sumOf { it.amount },
                income = bills.filter { it.type == BillType.INCOME }.sumOf { it.amount }
            )
        }
        .sortedBy { it.date }

interface BillRepository {
    fun getAllBills(): Flow<List<Bill>>
    suspend fun getBillById(id: Long): Bill?
    fun getBillsByMonth(yearMonth: String): Flow<List<Bill>>
    fun getBillsByMonthAndType(yearMonth: String, type: BillType): Flow<List<Bill>>
    fun getMonthlyBillCount(yearMonth: String): Flow<Int>
    fun getMonthlyExpense(yearMonth: String): Flow<Long?>
    fun getMonthlyIncome(yearMonth: String): Flow<Long?>
    fun getTotalExpense(): Flow<Long?>
    fun getTotalIncome(): Flow<Long?>
    fun getExpenseByCategory(yearMonth: String): Flow<List<CategoryTotal>>
    fun getIncomeByCategory(yearMonth: String): Flow<List<CategoryTotal>>
    fun getMonthlyExpenseTrend(): Flow<List<MonthTotal>>
    fun getMonthlyIncomeTrend(): Flow<List<MonthTotal>>
    fun getBillsByDate(date: Long): Flow<List<Bill>>
    fun getBillsByDateRange(startDate: Long, endDate: Long): Flow<List<Bill>>
    fun getBillsByDateRangeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<Bill>>
    fun getAllYearMonths(): Flow<List<String>>
    suspend fun insertBill(bill: Bill): Long
    suspend fun updateBill(bill: Bill)
    suspend fun deleteBill(id: Long)

    /** 事务：写账单 + 按类型调整钱包余额（新建） */
    suspend fun createBillWithWalletAdjustment(bill: Bill): Long

    /** 事务：更新账单 + 回滚旧余额 + 应用新余额（仅金额/类型/钱包变化时） */
    suspend fun updateBillWithWalletAdjustment(newBill: Bill)
    suspend fun search(query: String): List<Bill>
    fun getYearlyExpenseByCategory(year: String): Flow<List<CategoryTotalWithCount>>
    fun getYearlyIncomeByCategory(year: String): Flow<List<CategoryTotalWithCount>>
    fun getYearlyExpense(year: String): Flow<Long?>
    fun getYearlyIncome(year: String): Flow<Long?>
    fun getYearlyExpenseTrend(year: String): Flow<List<MonthTotal>>
    fun getYearlyIncomeTrend(year: String): Flow<List<MonthTotal>>
    fun getWeeklyExpense(startDate: Long, endDate: Long): Flow<Long?>
    fun getWeeklyIncome(startDate: Long, endDate: Long): Flow<Long?>
    fun getWeeklyBillCount(startDate: Long, endDate: Long): Flow<Int>
    fun getWeeklyExpenseByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>>
    fun getWeeklyIncomeByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>>
    fun getBillsByBookAndMonth(bookId: Long, yearMonth: String): Flow<List<Bill>>
    fun getMonthlyBillCountByBook(bookId: Long, yearMonth: String): Flow<Int>
    fun getMonthlyExpenseByBook(bookId: Long, yearMonth: String): Flow<Long?>
    fun getMonthlyIncomeByBook(bookId: Long, yearMonth: String): Flow<Long?>
    fun getExpenseByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>>
    fun getIncomeByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>>
    fun getWeeklyExpenseByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Long?>
    fun getWeeklyIncomeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Long?>
    fun getWeeklyBillCountByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Int>
    fun getWeeklyExpenseByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>
    fun getWeeklyIncomeByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>
    fun getYearlyExpenseByBook(bookId: Long, year: String): Flow<Long?>
    fun getYearlyIncomeByBook(bookId: Long, year: String): Flow<Long?>
    fun getYearlyExpenseTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>>
    fun getYearlyIncomeTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>>
    fun getYearlyExpenseByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>>
    fun getYearlyIncomeByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>>
    fun getCategoryUsageCounts(type: String): Flow<List<CategoryTotalWithCount>>
    suspend fun updateCategoryNameInBills(oldName: String, newName: String)
    suspend fun countByCategory(category: String): Int
    suspend fun deleteByCategory(category: String)
    suspend fun countByWallet(walletId: Long): Int
    suspend fun deleteByWallet(walletId: Long)
    suspend fun countByBook(bookId: Long): Int
    suspend fun deleteByBook(bookId: Long)
    suspend fun restoreBill(id: Long)
    suspend fun hardDeleteBill(id: Long)
}
