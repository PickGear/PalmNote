package com.palmnote.data.repository
import javax.inject.Inject

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.BillDao
import com.palmnote.data.db.dao.BillRecycleBinDao
import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.CategoryTotalWithCount
import com.palmnote.data.db.dao.ExpenseBreakdown
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.data.db.dao.ReimbursementSummary
import com.palmnote.data.db.dao.WalletDao
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.BillRecycleBin
import com.palmnote.data.db.entity.toBill
import com.palmnote.data.db.entity.toRecycleBin
import com.palmnote.domain.model.BillType
import kotlinx.coroutines.flow.Flow
import com.palmnote.domain.repository.BillRepository
import com.palmnote.domain.repository.ReimbursementAllocation
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

    override suspend fun createBillsWithWalletAdjustment(bills: List<Bill>): List<Long> =
        appDatabase.withTransaction {
            bills.map { bill ->
                val id = billDao.insertBill(bill)
                applyNewBalance(bill)
                id
            }
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
        detachReimbursementOnDelete(bill)
        recycleBinDao.insert(bill.toRecycleBin())
        billDao.deleteById(id)
        revertOldBalance(bill)
    }

    /**
     * 账单移入回收站前的连带处理：删掉的是报销收入时，把指向它的支出整体退回待报销。
     *
     * 不必先判断这笔收入「是不是报销收入」—— [BillDao.clearReimbursementBySource] 的
     * WHERE 已按 `reimbursedByBillId = :billId` 收窄，没有支出指向它就是空操作。
     */
    private suspend fun detachReimbursementOnDelete(bill: Bill) {
        if (bill.type == BillType.INCOME) {
            billDao.clearReimbursementBySource(bill.id)
        }
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

    // ---------------- 报销管理 ----------------

    override fun getUnreimbursedBills(): Flow<List<Bill>> = billDao.getUnreimbursedBills()

    override fun getReimbursedBills(): Flow<List<Bill>> = billDao.getReimbursedBills()

    override fun getPendingReimbursementSummary(): Flow<ReimbursementSummary?> =
        billDao.getPendingReimbursementSummary()

    override fun getTotalReimbursedAmount(): Flow<Long?> = billDao.getTotalReimbursedAmount()

    override suspend fun getBillsReimbursedBy(billId: Long): List<Bill> =
        billDao.getBillsReimbursedBy(billId)

    override suspend fun applyReimbursement(expenseId: Long, paid: Long, date: Long, byBillId: Long?) {
        val expense = billDao.getBillById(expenseId) ?: return
        // 累计额封顶到原金额：多报出来的钱不属于这笔支出，不能让它溢出成负的待报额
        val capped = paid.coerceAtMost(expense.amount)
        billDao.applyReimbursement(
            id = expenseId,
            paid = capped,
            done = capped >= expense.amount,
            date = date,
            byBillId = byBillId
        )
    }

    /**
     * 撤销整笔报销：支出退回待报销，**并删掉系统代记的那笔报销收入**（进回收站，可恢复）。
     *
     * 那笔收入只是这笔支出的账面投影 —— 只清标记不删收入，收支就被凭空抬高了一笔
     * （2026-10-10 用户反馈：「记账报销撤销后，记账的报销收入记录没有撤销」）。
     * 这条路径与 [detachReimbursementOnDelete]（删报销收入 → 关联支出退回待报销）
     * 互为一对反向操作，两边保持对称。
     *
     * 例外：这笔收入若还分摊给了别的支出（录入页勾多笔时会出现「一收入对多支出」），
     * 删掉会连带抹掉那些支出的已报销状态 —— 此时只解绑本笔，收入留给用户自己处置。
     */
    override suspend fun clearReimbursement(expenseId: Long) = appDatabase.withTransaction {
        val expense = billDao.getBillById(expenseId) ?: return@withTransaction
        // 顺序要紧：先解绑本笔（清掉 reimbursedByBillId），再回头看那笔收入是否还有别的支出指着
        billDao.clearReimbursement(expenseId)

        val incomeId = expense.reimbursedByBillId ?: return@withTransaction
        if (billDao.getBillsReimbursedBy(incomeId).isEmpty()) {
            deleteBill(incomeId)
        }
    }

    override suspend fun createReimbursementIncome(
        income: Bill,
        allocations: List<ReimbursementAllocation>
    ): Long = appDatabase.withTransaction {
        val incomeId = billDao.insertBill(income)
        applyNewBalance(income)
        // 分摊与记收入同一事务：中途失败不会留下「收入记了、支出没标」的半截状态
        allocations.forEach { allocation ->
            val expense = billDao.getBillById(allocation.expenseId) ?: return@forEach
            val paid = (expense.reimbursedAmount + allocation.amount).coerceAtMost(expense.amount)
            if (paid > 0) {
                billDao.applyReimbursement(
                    id = expense.id,
                    paid = paid,
                    done = paid >= expense.amount,
                    date = income.date,
                    byBillId = incomeId
                )
            }
        }
        incomeId
    }

    override fun getMonthlyExpenseBreakdown(yearMonth: String): Flow<ExpenseBreakdown?> =
        billDao.getMonthlyExpenseBreakdown(yearMonth)

    override fun getMonthlyExpenseBreakdownByBook(bookId: Long, yearMonth: String): Flow<ExpenseBreakdown?> =
        billDao.getMonthlyExpenseBreakdownByBook(bookId, yearMonth)

    override fun getWeeklyExpenseBreakdown(startDate: Long, endDate: Long): Flow<ExpenseBreakdown?> =
        billDao.getWeeklyExpenseBreakdown(startDate, endDate)

    override fun getWeeklyExpenseBreakdownByBook(bookId: Long, startDate: Long, endDate: Long): Flow<ExpenseBreakdown?> =
        billDao.getWeeklyExpenseBreakdownByBook(bookId, startDate, endDate)

    override fun getYearlyExpenseBreakdown(year: String): Flow<ExpenseBreakdown?> =
        billDao.getYearlyExpenseBreakdown(year)

    override fun getYearlyExpenseBreakdownByBook(bookId: Long, year: String): Flow<ExpenseBreakdown?> =
        billDao.getYearlyExpenseBreakdownByBook(bookId, year)

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

    /**
     * 批量删除账单（按分类/钱包/账本）：与单条 [deleteBill] 保持一致 ——
     * 进回收站、回滚钱包余额，事务提交后再删除图片文件。
     */
    private suspend fun deleteBillsToRecycleBin(
        select: suspend () -> List<Bill>,
        delete: suspend () -> Unit
    ) {
        // 查询必须在事务内：先查后删的两段式会让"查询之后、删除之前"新写入的
        // 目标账单被硬删（无回收站行、余额不回滚）
        val bills = appDatabase.withTransaction {
            val selected = select()
            selected.forEach { bill ->
                detachReimbursementOnDelete(bill)
                recycleBinDao.insert(bill.toRecycleBin())
                revertOldBalance(bill)
            }
            delete()
            selected
        }
        // 事务提交成功后再清理图片文件，避免回滚时误删
        bills.forEach { deleteImageFiles(it.images) }
    }

    override suspend fun deleteByCategory(category: String) =
        deleteBillsToRecycleBin({ billDao.getBillsByCategoryOnce(category) }) {
            billDao.deleteByCategory(category)
        }

    override suspend fun countByWallet(walletId: Long): Int =
        billDao.countByWallet(walletId)

    override suspend fun deleteByWallet(walletId: Long) =
        deleteBillsToRecycleBin({ billDao.getBillsByWalletOnce(walletId) }) {
            billDao.deleteByWallet(walletId)
        }

    override suspend fun countByBook(bookId: Long): Int =
        billDao.countByBook(bookId)

    override suspend fun deleteByBook(bookId: Long) =
        deleteBillsToRecycleBin({ billDao.getBillsByBookOnce(bookId) }) {
            billDao.deleteByBook(bookId)
        }
}
