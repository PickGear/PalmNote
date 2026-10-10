package com.palmnote.domain.repository

import com.palmnote.data.db.dao.CategoryTotal
import com.palmnote.data.db.dao.CategoryTotalWithCount
import com.palmnote.data.db.dao.DailySummary
import com.palmnote.data.db.dao.ExpenseBreakdown
import com.palmnote.data.db.dao.MonthTotal
import com.palmnote.data.db.dao.ReimbursementSummary
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.BillType
import com.palmnote.domain.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * 一笔报销收入对某条支出的分摊。
 * [amount] 是本次记到该支出头上的金额（分），会累加进它已有的 `reimbursedAmount`。
 */
data class ReimbursementAllocation(
    val expenseId: Long,
    val amount: Long
)

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

    /** 单事务批量新建（导入用）：逐条按类型调整钱包余额，返回各账单 id */
    suspend fun createBillsWithWalletAdjustment(bills: List<Bill>): List<Long>

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

    // ---------------- 报销管理 ----------------

    /** 待报销的支出（含只报了一部分的，只要没报完就算待报销）。 */
    fun getUnreimbursedBills(): Flow<List<Bill>>

    /** 已报完的支出。 */
    fun getReimbursedBills(): Flow<List<Bill>>

    /** 待报销汇总：剩余可报总额 + 笔数。 */
    fun getPendingReimbursementSummary(): Flow<ReimbursementSummary?>

    /** 累计已报回金额（含部分报销部分）。 */
    fun getTotalReimbursedAmount(): Flow<Long?>

    /**
     * 一笔报销收入覆盖了哪些支出（按支出日期倒序）。
     * 方向是「支出存关联收入 id」，所以这里靠 `WHERE reimbursedByBillId = ?` 反查。
     */
    suspend fun getBillsReimbursedBy(billId: Long): List<Bill>

    /**
     * 只更新报销进度、不新记账。[paid] 是**累计**已报金额（由调用方按已有进度累加）。
     * 用于「钱已经在别处记过收入了，这里只标一下」的场景。
     */
    suspend fun applyReimbursement(expenseId: Long, paid: Long, date: Long, byBillId: Long?)

    /**
     * 撤销整笔报销：支出退回待报销，并删除系统代记的那笔报销收入（进回收站，可恢复）。
     *
     * 与 [deleteBill] 删报销收入时的联动互为一对反向操作。
     * 若那笔收入同时覆盖了别的支出，则只解绑本笔、不动收入。
     */
    suspend fun clearReimbursement(expenseId: Long)

    /**
     * 记一笔报销收入（[income]），并按 [allocations] 分摊到对应支出上，全程同一事务。
     * 返回新建的收入账单 id。
     */
    suspend fun createReimbursementIncome(income: Bill, allocations: List<ReimbursementAllocation>): Long

    // 报表「净支出」口径：净支出 = expense - reimbursed
    fun getMonthlyExpenseBreakdown(yearMonth: String): Flow<ExpenseBreakdown?>
    fun getMonthlyExpenseBreakdownByBook(bookId: Long, yearMonth: String): Flow<ExpenseBreakdown?>
    fun getWeeklyExpenseBreakdown(startDate: Long, endDate: Long): Flow<ExpenseBreakdown?>
    fun getWeeklyExpenseBreakdownByBook(bookId: Long, startDate: Long, endDate: Long): Flow<ExpenseBreakdown?>
    fun getYearlyExpenseBreakdown(year: String): Flow<ExpenseBreakdown?>
    fun getYearlyExpenseBreakdownByBook(bookId: Long, year: String): Flow<ExpenseBreakdown?>
}
