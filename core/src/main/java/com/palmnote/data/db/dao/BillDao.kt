package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.domain.model.BillType
import com.palmnote.data.db.entity.Bill
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY date DESC, createdAt DESC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillById(id: Long): Bill?

    @Query(
        "SELECT * FROM bills WHERE yearMonth = :yearMonth " +
            "ORDER BY date DESC, createdAt DESC LIMIT 20000"
    )
    fun getBillsByMonth(yearMonth: String): Flow<List<Bill>>

    @Query(
        "SELECT * FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth " +
            "ORDER BY date DESC, createdAt DESC LIMIT 20000"
    )
    fun getBillsByBookAndMonth(bookId: Long, yearMonth: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE yearMonth = :yearMonth AND type = :type ORDER BY date DESC, createdAt DESC")
    fun getBillsByMonthAndType(yearMonth: String, type: BillType): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE category = :category ORDER BY date DESC")
    fun getBillsByCategory(category: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE paymentMethod = :method ORDER BY date DESC")
    fun getBillsByPaymentMethod(method: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE merchant LIKE '%' || :merchant || '%' ORDER BY date DESC")
    fun getBillsByMerchant(merchant: String): Flow<List<Bill>>

    /**
     * 待报销的支出（含只报了一部分的，只要没报完就算待报销）。
     * 必须带 `type = 'EXPENSE'`：v1.4.0 的「可报销」开关是无条件显示的，历史数据里可能
     * 存在被标了可报销的收入/转账账单，不排掉会混进报销列表。
     */
    @Query("SELECT * FROM bills WHERE isReimbursable = 1 AND isReimbursed = 0 AND type = 'EXPENSE' ORDER BY date DESC")
    fun getUnreimbursedBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE recurringId IS NOT NULL ORDER BY date DESC")
    fun getRecurringBills(): Flow<List<Bill>>

    @Query("SELECT COUNT(*) FROM bills WHERE yearMonth = :yearMonth")
    fun getMonthlyBillCount(yearMonth: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth")
    fun getMonthlyBillCountByBook(bookId: Long, yearMonth: String): Flow<Int>

    @Query("SELECT SUM(amount) FROM bills WHERE yearMonth = :yearMonth AND type = 'EXPENSE'")
    fun getMonthlyExpense(yearMonth: String): Flow<Long?>

    /**
     * 月度支出 + 其中已报销部分。一次查询拿两个数，报表不必为「净支出」再挂一条流
     * （Kotlin 的 combine 只到 5 个参数，报表页已经把额度用满了）。
     */
    @Query(
        "SELECT SUM(amount) AS expense, SUM(reimbursedAmount) AS reimbursed FROM bills " +
            "WHERE yearMonth = :yearMonth AND type = 'EXPENSE'"
    )
    fun getMonthlyExpenseBreakdown(yearMonth: String): Flow<ExpenseBreakdown?>

    @Query(
        "SELECT SUM(amount) AS expense, SUM(reimbursedAmount) AS reimbursed FROM bills " +
            "WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'EXPENSE'"
    )
    fun getMonthlyExpenseBreakdownByBook(bookId: Long, yearMonth: String): Flow<ExpenseBreakdown?>

    /**
     * 近一段时间的每日支出合计（组件里的「近 7 天柱状图」用；`date` 是毫秒时间戳）。
     * 复用既有的 [DailySummary] 投影，收入那列恒 0（这里只关心支出）。
     */
    @Query(
        "SELECT date, SUM(amount) AS expense, 0 AS income FROM bills " +
            "WHERE type = 'EXPENSE' AND date >= :fromMillis GROUP BY date ORDER BY date"
    )
    fun getDailyExpenseSince(fromMillis: Long): Flow<List<DailySummary>>

    @Query(
        "SELECT category AS category, SUM(amount) AS total FROM bills " +
            "WHERE yearMonth = :yearMonth AND type = 'EXPENSE' " +
            "GROUP BY category ORDER BY total DESC LIMIT 1"
    )
    suspend fun getTopExpenseCategory(yearMonth: String): CategoryTotal?

    @Query("SELECT SUM(amount) FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'EXPENSE'")
    fun getMonthlyExpenseByBook(bookId: Long, yearMonth: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE yearMonth = :yearMonth AND type = 'INCOME'")
    fun getMonthlyIncome(yearMonth: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'INCOME'")
    fun getMonthlyIncomeByBook(bookId: Long, yearMonth: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE'")
    fun getTotalExpense(): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE yearMonth = :yearMonth AND type = 'EXPENSE' AND paymentMethod = :method")
    fun getMonthlyExpenseByPaymentMethod(yearMonth: String, method: String): Flow<Long?>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'EXPENSE'
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getExpenseByCategory(yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'EXPENSE'
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getExpenseByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'INCOME'
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getIncomeByCategory(yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'INCOME'
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getIncomeByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT subCategory, SUM(amount) as total
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'EXPENSE' AND category = :category
        GROUP BY subCategory
        ORDER BY total DESC
    """)
    fun getSubCategoryTotals(yearMonth: String, category: String): Flow<List<SubCategoryTotal>>

    @Query("""
        SELECT paymentMethod, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'EXPENSE'
        GROUP BY paymentMethod
        ORDER BY total DESC
    """)
    fun getExpenseByPaymentMethod(yearMonth: String): Flow<List<PaymentMethodTotal>>

    @Query("""
        SELECT merchant, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'EXPENSE'
        GROUP BY merchant
        ORDER BY total DESC
        LIMIT 10
    """)
    fun getTopMerchants(yearMonth: String): Flow<List<MerchantTotal>>

    @Query("""
        SELECT yearMonth, SUM(amount) as total
        FROM bills
        WHERE type = 'EXPENSE'
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlyExpenseTrend(): Flow<List<MonthTotal>>

    @Query("""
        SELECT yearMonth, SUM(amount) as total
        FROM bills
        WHERE type = 'INCOME'
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlyIncomeTrend(): Flow<List<MonthTotal>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'EXPENSE' AND yearMonth >= :startMonth AND yearMonth <= :endMonth
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getExpenseByCategoryRange(startMonth: String, endMonth: String): Flow<List<CategoryTotalWithCount>>

    @Query("SELECT * FROM bills WHERE date = :date ORDER BY createdAt DESC")
    fun getBillsByDate(date: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getBillsByDateRange(startDate: Long, endDate: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE accountBookId = :bookId AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getBillsByDateRangeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<Bill>>


    @Query("SELECT DISTINCT yearMonth FROM bills ORDER BY yearMonth DESC")
    fun getAllYearMonths(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM bills ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT merchant FROM bills WHERE merchant != '' ORDER BY merchant ASC")
    fun getAllMerchants(): Flow<List<String>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'EXPENSE' AND yearMonth >= :year || '-01' AND yearMonth <= :year || '-12'
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getYearlyExpenseByCategory(year: String): Flow<List<CategoryTotalWithCount>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'INCOME' AND yearMonth >= :year || '-01' AND yearMonth <= :year || '-12'
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getYearlyIncomeByCategory(year: String): Flow<List<CategoryTotalWithCount>>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND yearMonth >= :year || '-01' AND yearMonth <= :year || '-12'")
    fun getYearlyExpense(year: String): Flow<Long?>

    @Query(
        "SELECT SUM(amount) AS expense, SUM(reimbursedAmount) AS reimbursed FROM bills " +
            "WHERE type = 'EXPENSE' AND yearMonth >= :year || '-01' " +
            "AND yearMonth <= :year || '-12'"
    )
    fun getYearlyExpenseBreakdown(year: String): Flow<ExpenseBreakdown?>

    @Query(
        "SELECT SUM(amount) AS expense, SUM(reimbursedAmount) AS reimbursed FROM bills " +
            "WHERE type = 'EXPENSE' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId"
    )
    fun getYearlyExpenseBreakdownByBook(bookId: Long, year: String): Flow<ExpenseBreakdown?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND yearMonth >= :year || '-01' AND yearMonth <= :year || '-12'")
    fun getYearlyIncome(year: String): Flow<Long?>

    @Query("""
        SELECT yearMonth, 
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as total
        FROM bills
        WHERE yearMonth >= :year || '-01' AND yearMonth <= :year || '-12'
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getYearlyExpenseTrend(year: String): Flow<List<MonthTotal>>

    @Query("""
        SELECT yearMonth, 
               SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as total
        FROM bills
        WHERE yearMonth >= :year || '-01' AND yearMonth <= :year || '-12'
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getYearlyIncomeTrend(year: String): Flow<List<MonthTotal>>

    @Query("SELECT * FROM bills WHERE note LIKE '%' || :query || '%' OR merchant LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY date DESC")
    suspend fun search(query: String): List<Bill>

    @RawQuery(observedEntities = [Bill::class])
    fun searchBills(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<Bill>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)



    /**
     * 写入报销进度。
     *
     * [paid] 是**累计**已报金额（调用方按「原值 + 本次」算好，支持分次部分报销）；
     * [done] 由 Kotlin 侧判定 `paid >= amount` 后传入 —— 不写在 SQL 表达式里是为了避免
     * 参数名与列名 `amount` 在 UPDATE 语句中产生歧义。
     */
    @Query(
        "UPDATE bills SET reimbursedAmount = :paid, isReimbursed = :done, " +
            "reimbursedDate = :date, reimbursedByBillId = :byBillId, updatedAt = :now WHERE id = :id"
    )
    suspend fun applyReimbursement(
        id: Long,
        paid: Long,
        done: Boolean,
        date: Long?,
        byBillId: Long?,
        now: Long = System.currentTimeMillis()
    )

    /** 撤销报销：整笔退回未报销状态（含解绑收入）。 */
    @Query(
        "UPDATE bills SET reimbursedAmount = 0, isReimbursed = 0, reimbursedDate = NULL, " +
            "reimbursedByBillId = NULL, updatedAt = :now WHERE id = :id"
    )
    suspend fun clearReimbursement(id: Long, now: Long = System.currentTimeMillis())

    /** 已报完的支出，按最近报销时间倒序（报销管理页「已报销」分页）。 */
    @Query("SELECT * FROM bills WHERE isReimbursable = 1 AND isReimbursed = 1 AND type = 'EXPENSE' ORDER BY reimbursedDate DESC, date DESC")
    fun getReimbursedBills(): Flow<List<Bill>>

    /**
     * 待报销汇总：剩余可报总额 + 笔数。
     * 剩余额按 `amount - reimbursedAmount` 逐笔累加，所以部分报销的只贡献差额。
     * WHERE 必须与 [getUnreimbursedBills] 完全一致，否则卡片数字与列表条数会对不上。
     */
    @Query(
        "SELECT SUM(amount - reimbursedAmount) AS remaining, COUNT(*) AS count FROM bills " +
            "WHERE isReimbursable = 1 AND isReimbursed = 0 AND type = 'EXPENSE'"
    )
    fun getPendingReimbursementSummary(): Flow<ReimbursementSummary?>

    /** 累计已报回金额（含部分报销部分），报销页统计用。 */
    @Query("SELECT SUM(reimbursedAmount) FROM bills WHERE isReimbursable = 1 AND type = 'EXPENSE'")
    fun getTotalReimbursedAmount(): Flow<Long?>

    /** 某笔报销收入覆盖了哪些支出。 */
    @Query("SELECT * FROM bills WHERE reimbursedByBillId = :billId ORDER BY date DESC")
    suspend fun getBillsReimbursedBy(billId: Long): List<Bill>

    /**
     * 报销收入被删除/移入回收站时解绑：把指向它的支出整体退回未报销。
     *
     * 语义刻意从简 —— 支出上只记「最近一次报销来源」，所以删掉该来源等价于撤销那次报销。
     * 不做按份额回扣，是因为记录里没有分次明细，硬算只会得到更不可解释的数字。
     */
    @Query(
        "UPDATE bills SET reimbursedByBillId = NULL, reimbursedAmount = 0, isReimbursed = 0, " +
            "reimbursedDate = NULL, updatedAt = :now WHERE reimbursedByBillId = :billId"
    )
    suspend fun clearReimbursementBySource(billId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate")
    fun getWeeklyExpense(startDate: Long, endDate: Long): Flow<Long?>

    @Query(
        "SELECT SUM(amount) AS expense, SUM(reimbursedAmount) AS reimbursed FROM bills " +
            "WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate"
    )
    fun getWeeklyExpenseBreakdown(startDate: Long, endDate: Long): Flow<ExpenseBreakdown?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate")
    fun getWeeklyIncome(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM bills WHERE date >= :startDate AND date <= :endDate")
    fun getWeeklyBillCount(startDate: Long, endDate: Long): Flow<Int>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getWeeklyExpenseByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getWeeklyIncomeByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bills WHERE category = :category")
    suspend fun getBillsByCategoryOnce(category: String): List<Bill>

    @Query("SELECT * FROM bills WHERE walletId = :walletId OR toWalletId = :walletId")
    suspend fun getBillsByWalletOnce(walletId: Long): List<Bill>

    @Query("SELECT * FROM bills WHERE accountBookId = :bookId")
    suspend fun getBillsByBookOnce(bookId: Long): List<Bill>

    @Query("DELETE FROM bills WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("DELETE FROM bills WHERE walletId = :walletId OR toWalletId = :walletId")
    suspend fun deleteByWallet(walletId: Long)

    @Query("DELETE FROM bills WHERE accountBookId = :bookId")
    suspend fun deleteByBook(bookId: Long)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate AND accountBookId = :bookId")
    fun getWeeklyExpenseByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Long?>

    @Query(
        "SELECT SUM(amount) AS expense, SUM(reimbursedAmount) AS reimbursed FROM bills " +
            "WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate " +
            "AND accountBookId = :bookId"
    )
    fun getWeeklyExpenseBreakdownByBook(bookId: Long, startDate: Long, endDate: Long): Flow<ExpenseBreakdown?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate AND accountBookId = :bookId")
    fun getWeeklyIncomeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM bills WHERE date >= :startDate AND date <= :endDate AND accountBookId = :bookId")
    fun getWeeklyBillCountByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Int>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate AND accountBookId = :bookId
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getWeeklyExpenseByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate AND accountBookId = :bookId
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getWeeklyIncomeByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId")
    fun getYearlyExpenseByBook(bookId: Long, year: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId")
    fun getYearlyIncomeByBook(bookId: Long, year: String): Flow<Long?>

    @Query("""
        SELECT yearMonth, 
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as total
        FROM bills
        WHERE substr(yearMonth,1,4) = :year AND accountBookId = :bookId
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getYearlyExpenseTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>>

    @Query("""
        SELECT yearMonth, 
               SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as total
        FROM bills
        WHERE substr(yearMonth,1,4) = :year AND accountBookId = :bookId
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getYearlyIncomeTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'EXPENSE' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getYearlyExpenseByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'INCOME' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getYearlyIncomeByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>>

    @Query("""
        SELECT category, 0 as total, COUNT(*) as count
        FROM bills
        WHERE type = :type
        GROUP BY category
        ORDER BY count DESC
    """)
    fun getCategoryUsageCounts(type: BillType): Flow<List<CategoryTotalWithCount>>

    @Query("UPDATE bills SET category = :newName WHERE category = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM bills WHERE category = :category")
    suspend fun countByCategory(category: String): Int


    @Query("SELECT COUNT(*) FROM bills WHERE (walletId = :walletId OR toWalletId = :walletId)")
    suspend fun countByWallet(walletId: Long): Int


    @Query("SELECT COUNT(*) FROM bills WHERE accountBookId = :bookId")
    suspend fun countByBook(bookId: Long): Int


    /** 演示数据清理（v14 isDemo 列）：演示模式关闭时物理删除示例行。 */
    @Query("DELETE FROM bills WHERE isDemo = 1 OR accountBookId IN (SELECT id FROM account_books WHERE isDemo = 1)")
    suspend fun clearDemoBills()

    /** 当前示例行数（关演示反馈 / 幂等判断用）。 */
    @Query("SELECT COUNT(*) FROM bills WHERE isDemo = 1")
    suspend fun countDemoBills(): Int

    /** 演示期用户在示例账本里自建的账单数（关演示「毕业询问」的保留规模）。 */
    @Query("SELECT COUNT(*) FROM bills WHERE accountBookId IN (SELECT id FROM account_books WHERE isDemo = 1) AND isDemo = 0")
    suspend fun countUserBillsInDemoBooks(): Int

    /** 毕业：把示例账本里用户自建的账单迁到真实账本（关演示选「保留」时）。 */
    @Query("UPDATE bills SET accountBookId = :targetBookId WHERE accountBookId IN (SELECT id FROM account_books WHERE isDemo = 1) AND isDemo = 0")
    suspend fun graduateUserBillsFromDemoBooks(targetBookId: Long)
}

data class CategoryTotal(
    val category: String,
    val total: Long
)

data class SubCategoryTotal(
    val subCategory: String,
    val total: Long
)

data class MonthTotal(
    val yearMonth: String,
    val total: Long
)

data class PaymentMethodTotal(
    val paymentMethod: String,
    val total: Long,
    val count: Int
)

data class MerchantTotal(
    val merchant: String,
    val total: Long,
    val count: Int
)

data class CategoryTotalWithCount(
    val category: String,
    val total: Long,
    val count: Int
)

data class DailySummary(
    val date: Long,
    val expense: Long,
    val income: Long
)

/**
 * 支出 + 其中已报销部分。字段可空是因为无匹配行时 `SUM` 返回 NULL —— 调用方统一按
 * `?: 0L` 收敛，不要把「查不到」压成业务上的 0。
 * 净支出（真正自己花的钱）= `expense - reimbursed`。
 */
data class ExpenseBreakdown(
    val expense: Long? = null,
    val reimbursed: Long? = null
)

/** 待报销汇总：剩余可报总额（分）+ 待报销笔数。无待报销项时 [remaining] 为 null。 */
data class ReimbursementSummary(
    val remaining: Long? = null,
    val count: Int? = null
)
