package com.palmnote.data.repository

import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.CategoryTotalWithCount
import com.palmnote.data.db.dao.DailySummary
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.data.db.entity.Bill
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.BillRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao
) : BillRepository {
    override fun getAllBills(): Flow<List<Bill>> = billDao.getAllBills()

    override suspend fun getBillById(id: Long): Bill? = billDao.getBillById(id)

    override fun getBillsByMonth(yearMonth: String): Flow<List<Bill>> = billDao.getBillsByMonth(yearMonth)

    override fun getBillsByMonthAndType(yearMonth: String, type: String): Flow<List<Bill>> =
        billDao.getBillsByMonthAndType(yearMonth, type)

    override fun getMonthlyBillCount(yearMonth: String): Flow<Int> = billDao.getMonthlyBillCount(yearMonth)

    override fun getMonthlyExpense(yearMonth: String): Flow<Double?> = billDao.getMonthlyExpense(yearMonth)

    override fun getMonthlyIncome(yearMonth: String): Flow<Double?> = billDao.getMonthlyIncome(yearMonth)

    override fun getTotalExpense(): Flow<Double?> = billDao.getTotalExpense()

    override fun getTotalIncome(): Flow<Double?> = billDao.getTotalIncome()

    override fun getExpenseByCategory(yearMonth: String): Flow<List<CategoryTotal>> =
        billDao.getExpenseByCategory(yearMonth)

    override fun getIncomeByCategory(yearMonth: String): Flow<List<CategoryTotal>> =
        billDao.getIncomeByCategory(yearMonth)

    override fun getMonthlyExpenseTrend(): Flow<List<MonthTotal>> = billDao.getMonthlyExpenseTrend()

    override fun getMonthlyIncomeTrend(): Flow<List<MonthTotal>> = billDao.getMonthlyIncomeTrend()

    override fun getDailySummary(yearMonth: String): Flow<List<DailySummary>> = billDao.getDailySummary(yearMonth)

    override fun getBillsByDate(date: Long): Flow<List<Bill>> = billDao.getBillsByDate(date)

    override fun getDeletedBills(): Flow<List<Bill>> = billDao.getDeletedBills()

    override fun getAllYearMonths(): Flow<List<String>> = billDao.getAllYearMonths()

    override suspend fun insertBill(bill: Bill): Long = billDao.insertBill(bill)

    override suspend fun updateBill(bill: Bill) = billDao.updateBill(bill)

    override suspend fun softDeleteBill(id: Long) = billDao.softDeleteBill(id)

    override suspend fun restoreBill(id: Long) = billDao.restoreBill(id)

    override suspend fun hardDeleteBill(id: Long) = billDao.hardDeleteBill(id)

    override suspend fun search(query: String): List<Bill> = billDao.search(query)

    override fun getYearlyExpenseByCategory(year: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getYearlyExpenseByCategory(year)

    override fun getYearlyIncomeByCategory(year: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getYearlyIncomeByCategory(year)

    override fun getYearlyExpense(year: String): Flow<Double?> = billDao.getYearlyExpense(year)

    override fun getYearlyIncome(year: String): Flow<Double?> = billDao.getYearlyIncome(year)

    override fun getYearlyExpenseTrend(year: String): Flow<List<MonthTotal>> =
        billDao.getYearlyExpenseTrend(year)

    override fun getYearlyIncomeTrend(year: String): Flow<List<MonthTotal>> =
        billDao.getYearlyIncomeTrend(year)

    override fun getWeeklyExpense(startDate: Long, endDate: Long): Flow<Double?> =
        billDao.getWeeklyExpense(startDate, endDate)

    override fun getWeeklyIncome(startDate: Long, endDate: Long): Flow<Double?> =
        billDao.getWeeklyIncome(startDate, endDate)

    override fun getWeeklyBillCount(startDate: Long, endDate: Long): Flow<Int> =
        billDao.getWeeklyBillCount(startDate, endDate)

    override fun getWeeklyExpenseByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>> =
        billDao.getWeeklyExpenseByCategory(startDate, endDate)

    override fun getWeeklyIncomeByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>> =
        billDao.getWeeklyIncomeByCategory(startDate, endDate)

    override fun getWeeklyDailySummary(startDate: Long, endDate: Long): Flow<List<DailySummary>> =
        billDao.getWeeklyDailySummary(startDate, endDate)

    override fun getBillsByBookAndMonth(bookId: Long, yearMonth: String): Flow<List<Bill>> =
        billDao.getBillsByBookAndMonth(bookId, yearMonth)

    override fun getMonthlyBillCountByBook(bookId: Long, yearMonth: String): Flow<Int> =
        billDao.getMonthlyBillCountByBook(bookId, yearMonth)

    override fun getMonthlyExpenseByBook(bookId: Long, yearMonth: String): Flow<Double?> =
        billDao.getMonthlyExpenseByBook(bookId, yearMonth)

    override fun getMonthlyIncomeByBook(bookId: Long, yearMonth: String): Flow<Double?> =
        billDao.getMonthlyIncomeByBook(bookId, yearMonth)

    override fun getExpenseByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>> =
        billDao.getExpenseByCategoryByBook(bookId, yearMonth)

    override fun getIncomeByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>> =
        billDao.getIncomeByCategoryByBook(bookId, yearMonth)

    override fun getDailySummaryByBook(bookId: Long, yearMonth: String): Flow<List<DailySummary>> =
        billDao.getDailySummaryByBook(bookId, yearMonth)

    override fun getWeeklyExpenseByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Double?> =
        billDao.getWeeklyExpenseByBook(bookId, startDate, endDate)

    override fun getWeeklyIncomeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Double?> =
        billDao.getWeeklyIncomeByBook(bookId, startDate, endDate)

    override fun getWeeklyBillCountByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Int> =
        billDao.getWeeklyBillCountByBook(bookId, startDate, endDate)

    override fun getWeeklyExpenseByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>> =
        billDao.getWeeklyExpenseByCategoryByBook(bookId, startDate, endDate)

    override fun getWeeklyIncomeByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>> =
        billDao.getWeeklyIncomeByCategoryByBook(bookId, startDate, endDate)

    override fun getWeeklyDailySummaryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<DailySummary>> =
        billDao.getWeeklyDailySummaryByBook(bookId, startDate, endDate)

    override fun getYearlyExpenseByBook(bookId: Long, year: String): Flow<Double?> =
        billDao.getYearlyExpenseByBook(bookId, year)

    override fun getYearlyIncomeByBook(bookId: Long, year: String): Flow<Double?> =
        billDao.getYearlyIncomeByBook(bookId, year)

    override fun getYearlyExpenseTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>> =
        billDao.getYearlyExpenseTrendByBook(bookId, year)

    override fun getYearlyIncomeTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>> =
        billDao.getYearlyIncomeTrendByBook(bookId, year)

    override fun getYearlyExpenseByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getYearlyExpenseByCategoryByBook(bookId, year)

    override fun getYearlyIncomeByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getYearlyIncomeByCategoryByBook(bookId, year)
}
