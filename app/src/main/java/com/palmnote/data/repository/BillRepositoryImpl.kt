package com.palmnote.data.repository
import javax.inject.Inject

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BillRecycleBinDao
import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.CategoryTotalWithCount
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.BillRecycleBin
import com.palmnote.data.db.entity.toBill
import com.palmnote.data.db.entity.toRecycleBin
import com.palmnote.domain.model.BillType
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.BillRepository
import kotlinx.serialization.json.Json
import java.io.File

private val billImageJson = Json { ignoreUnknownKeys = true }

private fun String.toImagePathList(): List<String> {
    if (isEmpty()) return emptyList()
    return try {
        billImageJson.decodeFromString<List<String>>(this)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun deleteImageFiles(images: String) {
    images.toImagePathList().forEach { path ->
        runCatching { File(path).delete() }
    }
}

class BillRepositoryImpl @Inject constructor(
    private val billDao: BillDao,
    private val walletDao: WalletDao,
    private val recycleBinDao: BillRecycleBinDao,
    private val appDatabase: AppDatabase
) : BillRepository {
    override fun getAllBills(): Flow<List<Bill>> = billDao.getAllBills()

    override suspend fun getBillById(id: Long): Bill? = billDao.getBillById(id)

    override fun getBillsByMonth(yearMonth: String): Flow<List<Bill>> = billDao.getBillsByMonth(yearMonth)

    override fun getBillsByMonthAndType(yearMonth: String, type: BillType): Flow<List<Bill>> =
        billDao.getBillsByMonthAndType(yearMonth, type)

    override fun getMonthlyBillCount(yearMonth: String): Flow<Int> = billDao.getMonthlyBillCount(yearMonth)

    override fun getMonthlyExpense(yearMonth: String): Flow<Long?> = billDao.getMonthlyExpense(yearMonth)

    override fun getMonthlyIncome(yearMonth: String): Flow<Long?> = billDao.getMonthlyIncome(yearMonth)

    override fun getTotalExpense(): Flow<Long?> = billDao.getTotalExpense()

    override fun getTotalIncome(): Flow<Long?> = billDao.getTotalIncome()

    override fun getExpenseByCategory(yearMonth: String): Flow<List<CategoryTotal>> =
        billDao.getExpenseByCategory(yearMonth)

    override fun getIncomeByCategory(yearMonth: String): Flow<List<CategoryTotal>> =
        billDao.getIncomeByCategory(yearMonth)

    override fun getMonthlyExpenseTrend(): Flow<List<MonthTotal>> = billDao.getMonthlyExpenseTrend()

    override fun getMonthlyIncomeTrend(): Flow<List<MonthTotal>> = billDao.getMonthlyIncomeTrend()

    override fun getBillsByDate(date: Long): Flow<List<Bill>> = billDao.getBillsByDate(date)
    override fun getBillsByDateRange(startDate: Long, endDate: Long): Flow<List<Bill>> = billDao.getBillsByDateRange(startDate, endDate)
    override fun getBillsByDateRangeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<Bill>> = billDao.getBillsByDateRangeByBook(bookId, startDate, endDate)


    override fun getAllYearMonths(): Flow<List<String>> = billDao.getAllYearMonths()

    override suspend fun insertBill(bill: Bill): Long = billDao.insertBill(bill)

    override suspend fun updateBill(bill: Bill) = billDao.updateBill(bill)

    override suspend fun createBillWithWalletAdjustment(bill: Bill): Long = appDatabase.withTransaction {
        val id = billDao.insertBill(bill)
        applyNewBalance(bill)
        id
    }

    override suspend fun updateBillWithWalletAdjustment(newBill: Bill) = appDatabase.withTransaction {
        val oldBill = billDao.getBillById(newBill.id)
        billDao.updateBill(newBill)
        if (oldBill != null) {
            val amountChanged = oldBill.amount != newBill.amount
            val typeChanged = oldBill.type != newBill.type
            val walletChanged = oldBill.walletId != newBill.walletId || oldBill.toWalletId != newBill.toWalletId
            if (amountChanged || typeChanged || walletChanged) {
                revertOldBalance(oldBill)
                applyNewBalance(newBill)
            }
        }
    }

    private suspend fun revertOldBalance(bill: Bill) {
        when (bill.type) {
            BillType.EXPENSE -> bill.walletId?.let { walletDao.adjustBalance(it, bill.amount) }
            BillType.INCOME -> bill.walletId?.let { walletDao.adjustBalance(it, -bill.amount) }
            BillType.TRANSFER -> {
                bill.walletId?.let { walletDao.adjustBalance(it, bill.amount) }
                bill.toWalletId?.let { walletDao.adjustBalance(it, -bill.amount) }
            }
        }
    }

    private suspend fun applyNewBalance(bill: Bill) {
        when (bill.type) {
            BillType.EXPENSE -> bill.walletId?.let { walletDao.adjustBalance(it, -bill.amount) }
            BillType.INCOME -> bill.walletId?.let { walletDao.adjustBalance(it, bill.amount) }
            BillType.TRANSFER -> {
                bill.walletId?.let { walletDao.adjustBalance(it, -bill.amount) }
                bill.toWalletId?.let { walletDao.adjustBalance(it, bill.amount) }
            }
        }
    }

    override suspend fun deleteBill(id: Long) = appDatabase.withTransaction {
        val bill = billDao.getBillById(id) ?: return@withTransaction
        recycleBinDao.insert(bill.toRecycleBin())
        billDao.deleteById(id)
        revertOldBalance(bill)
    }

    override suspend fun restoreBill(id: Long) = appDatabase.withTransaction {
        val item = recycleBinDao.getById(id) ?: return@withTransaction
        val bill = item.toBill()
        billDao.insertBill(bill)
        recycleBinDao.deleteById(id)
        applyNewBalance(bill)
    }

    override suspend fun hardDeleteBill(id: Long) = appDatabase.withTransaction {
        val item = recycleBinDao.getById(id) ?: return@withTransaction
        deleteImageFiles(item.images)
        recycleBinDao.deleteById(id)
    }

    override suspend fun search(query: String): List<Bill> = billDao.search(query)

    override fun getYearlyExpenseByCategory(year: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getYearlyExpenseByCategory(year)

    override fun getYearlyIncomeByCategory(year: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getYearlyIncomeByCategory(year)

    override fun getYearlyExpense(year: String): Flow<Long?> = billDao.getYearlyExpense(year)

    override fun getYearlyIncome(year: String): Flow<Long?> = billDao.getYearlyIncome(year)

    override fun getYearlyExpenseTrend(year: String): Flow<List<MonthTotal>> =
        billDao.getYearlyExpenseTrend(year)

    override fun getYearlyIncomeTrend(year: String): Flow<List<MonthTotal>> =
        billDao.getYearlyIncomeTrend(year)

    override fun getWeeklyExpense(startDate: Long, endDate: Long): Flow<Long?> =
        billDao.getWeeklyExpense(startDate, endDate)

    override fun getWeeklyIncome(startDate: Long, endDate: Long): Flow<Long?> =
        billDao.getWeeklyIncome(startDate, endDate)

    override fun getWeeklyBillCount(startDate: Long, endDate: Long): Flow<Int> =
        billDao.getWeeklyBillCount(startDate, endDate)

    override fun getWeeklyExpenseByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>> =
        billDao.getWeeklyExpenseByCategory(startDate, endDate)

    override fun getWeeklyIncomeByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>> =
        billDao.getWeeklyIncomeByCategory(startDate, endDate)

    override fun getBillsByBookAndMonth(bookId: Long, yearMonth: String): Flow<List<Bill>> =
        billDao.getBillsByBookAndMonth(bookId, yearMonth)

    override fun getMonthlyBillCountByBook(bookId: Long, yearMonth: String): Flow<Int> =
        billDao.getMonthlyBillCountByBook(bookId, yearMonth)

    override fun getMonthlyExpenseByBook(bookId: Long, yearMonth: String): Flow<Long?> =
        billDao.getMonthlyExpenseByBook(bookId, yearMonth)

    override fun getMonthlyIncomeByBook(bookId: Long, yearMonth: String): Flow<Long?> =
        billDao.getMonthlyIncomeByBook(bookId, yearMonth)

    override fun getExpenseByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>> =
        billDao.getExpenseByCategoryByBook(bookId, yearMonth)

    override fun getIncomeByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>> =
        billDao.getIncomeByCategoryByBook(bookId, yearMonth)

    override fun getWeeklyExpenseByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Long?> =
        billDao.getWeeklyExpenseByBook(bookId, startDate, endDate)

    override fun getWeeklyIncomeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Long?> =
        billDao.getWeeklyIncomeByBook(bookId, startDate, endDate)

    override fun getWeeklyBillCountByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Int> =
        billDao.getWeeklyBillCountByBook(bookId, startDate, endDate)

    override fun getWeeklyExpenseByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>> =
        billDao.getWeeklyExpenseByCategoryByBook(bookId, startDate, endDate)

    override fun getWeeklyIncomeByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>> =
        billDao.getWeeklyIncomeByCategoryByBook(bookId, startDate, endDate)

    override fun getYearlyExpenseByBook(bookId: Long, year: String): Flow<Long?> =
        billDao.getYearlyExpenseByBook(bookId, year)

    override fun getYearlyIncomeByBook(bookId: Long, year: String): Flow<Long?> =
        billDao.getYearlyIncomeByBook(bookId, year)

    override fun getYearlyExpenseTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>> =
        billDao.getYearlyExpenseTrendByBook(bookId, year)

    override fun getYearlyIncomeTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>> =
        billDao.getYearlyIncomeTrendByBook(bookId, year)

    override fun getYearlyExpenseByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getYearlyExpenseByCategoryByBook(bookId, year)

    override fun getYearlyIncomeByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getYearlyIncomeByCategoryByBook(bookId, year)

    override fun getCategoryUsageCounts(type: String): Flow<List<CategoryTotalWithCount>> =
        billDao.getCategoryUsageCounts(BillType.from(type))

    override suspend fun updateCategoryNameInBills(oldName: String, newName: String) =
        billDao.updateCategoryName(oldName, newName)

    override suspend fun countByCategory(category: String): Int =
        billDao.countByCategory(category)

    override suspend fun deleteByCategory(category: String) =
        billDao.deleteByCategory(category)

    override suspend fun countByWallet(walletId: Long): Int =
        billDao.countByWallet(walletId)

    override suspend fun deleteByWallet(walletId: Long) =
        billDao.deleteByWallet(walletId)

    override suspend fun countByBook(bookId: Long): Int =
        billDao.countByBook(bookId)

    override suspend fun deleteByBook(bookId: Long) =
        billDao.deleteByBook(bookId)
}
