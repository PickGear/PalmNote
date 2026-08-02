package com.palmnote.data.db.dao

import androidx.room.*
import com.palmnote.data.db.entity.Bill
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills WHERE isDeleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id AND isDeleted = 0")
    suspend fun getBillById(id: Long): Bill?

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillByIdIncludingDeleted(id: Long): Bill?

    @Query(
        "SELECT * FROM bills WHERE yearMonth = :yearMonth AND isDeleted = 0 " +
            "ORDER BY date DESC, createdAt DESC LIMIT 5000"
    )
    fun getBillsByMonth(yearMonth: String): Flow<List<Bill>>

    @Query(
        "SELECT * FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND isDeleted = 0 " +
            "ORDER BY date DESC, createdAt DESC LIMIT 5000"
    )
    fun getBillsByBookAndMonth(bookId: Long, yearMonth: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE yearMonth = :yearMonth AND type = :type AND isDeleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getBillsByMonthAndType(yearMonth: String, type: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE category = :category AND isDeleted = 0 ORDER BY date DESC")
    fun getBillsByCategory(category: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE paymentMethod = :method AND isDeleted = 0 ORDER BY date DESC")
    fun getBillsByPaymentMethod(method: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE merchant LIKE '%' || :merchant || '%' AND isDeleted = 0 ORDER BY date DESC")
    fun getBillsByMerchant(merchant: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE isReimbursable = 1 AND isReimbursed = 0 AND isDeleted = 0 ORDER BY date DESC")
    fun getUnreimbursedBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE recurringId IS NOT NULL AND isDeleted = 0 ORDER BY date DESC")
    fun getRecurringBills(): Flow<List<Bill>>

    @Query("SELECT COUNT(*) FROM bills WHERE yearMonth = :yearMonth AND isDeleted = 0")
    fun getMonthlyBillCount(yearMonth: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND isDeleted = 0")
    fun getMonthlyBillCountByBook(bookId: Long, yearMonth: String): Flow<Int>

    @Query("SELECT SUM(amount) FROM bills WHERE yearMonth = :yearMonth AND type = 'EXPENSE' AND isDeleted = 0")
    fun getMonthlyExpense(yearMonth: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'EXPENSE' AND isDeleted = 0")
    fun getMonthlyExpenseByBook(bookId: Long, yearMonth: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE yearMonth = :yearMonth AND type = 'INCOME' AND isDeleted = 0")
    fun getMonthlyIncome(yearMonth: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'INCOME' AND isDeleted = 0")
    fun getMonthlyIncomeByBook(bookId: Long, yearMonth: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND isDeleted = 0")
    fun getTotalExpense(): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND isDeleted = 0")
    fun getTotalIncome(): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE yearMonth = :yearMonth AND type = 'EXPENSE' AND paymentMethod = :method AND isDeleted = 0")
    fun getMonthlyExpenseByPaymentMethod(yearMonth: String, method: String): Flow<Long?>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'EXPENSE' AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getExpenseByCategory(yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'EXPENSE' AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getExpenseByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'INCOME' AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getIncomeByCategory(yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE accountBookId = :bookId AND yearMonth = :yearMonth AND type = 'INCOME' AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getIncomeByCategoryByBook(bookId: Long, yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT subCategory, SUM(amount) as total
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'EXPENSE' AND category = :category AND isDeleted = 0
        GROUP BY subCategory
        ORDER BY total DESC
    """)
    fun getSubCategoryTotals(yearMonth: String, category: String): Flow<List<SubCategoryTotal>>

    @Query("""
        SELECT paymentMethod, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'EXPENSE' AND isDeleted = 0
        GROUP BY paymentMethod
        ORDER BY total DESC
    """)
    fun getExpenseByPaymentMethod(yearMonth: String): Flow<List<PaymentMethodTotal>>

    @Query("""
        SELECT merchant, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE yearMonth = :yearMonth AND type = 'EXPENSE' AND isDeleted = 0
        GROUP BY merchant
        ORDER BY total DESC
        LIMIT 10
    """)
    fun getTopMerchants(yearMonth: String): Flow<List<MerchantTotal>>

    @Query("""
        SELECT yearMonth, SUM(amount) as total
        FROM bills
        WHERE type = 'EXPENSE' AND isDeleted = 0
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlyExpenseTrend(): Flow<List<MonthTotal>>

    @Query("""
        SELECT yearMonth, SUM(amount) as total
        FROM bills
        WHERE type = 'INCOME' AND isDeleted = 0
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlyIncomeTrend(): Flow<List<MonthTotal>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'EXPENSE' AND yearMonth >= :startMonth AND yearMonth <= :endMonth AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getExpenseByCategoryRange(startMonth: String, endMonth: String): Flow<List<CategoryTotalWithCount>>

    @Query("SELECT * FROM bills WHERE date = :date AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getBillsByDate(date: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE date >= :startDate AND date <= :endDate AND isDeleted = 0 ORDER BY date DESC")
    fun getBillsByDateRange(startDate: Long, endDate: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE accountBookId = :bookId AND date >= :startDate AND date <= :endDate AND isDeleted = 0 ORDER BY date DESC")
    fun getBillsByDateRangeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedBills(): Flow<List<Bill>>

    @Query("SELECT DISTINCT yearMonth FROM bills WHERE isDeleted = 0 ORDER BY yearMonth DESC")
    fun getAllYearMonths(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM bills WHERE isDeleted = 0 ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT merchant FROM bills WHERE merchant != '' AND isDeleted = 0 ORDER BY merchant ASC")
    fun getAllMerchants(): Flow<List<String>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'EXPENSE' AND yearMonth >= :year || '-01' AND yearMonth <= :year || '-12' AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getYearlyExpenseByCategory(year: String): Flow<List<CategoryTotalWithCount>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'INCOME' AND yearMonth >= :year || '-01' AND yearMonth <= :year || '-12' AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getYearlyIncomeByCategory(year: String): Flow<List<CategoryTotalWithCount>>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND yearMonth >= :year || '-01' AND yearMonth <= :year || '-12' AND isDeleted = 0")
    fun getYearlyExpense(year: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND yearMonth >= :year || '-01' AND yearMonth <= :year || '-12' AND isDeleted = 0")
    fun getYearlyIncome(year: String): Flow<Long?>

    @Query("""
        SELECT yearMonth, 
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as total
        FROM bills
        WHERE yearMonth >= :year || '-01' AND yearMonth <= :year || '-12' AND isDeleted = 0
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getYearlyExpenseTrend(year: String): Flow<List<MonthTotal>>

    @Query("""
        SELECT yearMonth, 
               SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as total
        FROM bills
        WHERE yearMonth >= :year || '-01' AND yearMonth <= :year || '-12' AND isDeleted = 0
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getYearlyIncomeTrend(year: String): Flow<List<MonthTotal>>

    @Query("SELECT bills.* FROM bills LEFT JOIN wallets ON bills.walletId = wallets.id WHERE bills.isDeleted = 0 AND (bills.note LIKE '%' || :query || '%' OR bills.merchant LIKE '%' || :query || '%' OR bills.location LIKE '%' || :query || '%' OR bills.category LIKE '%' || :query || '%' OR printf('%.2f', bills.amount / 100.0) LIKE '%' || :query || '%' OR wallets.name LIKE '%' || :query || '%') ORDER BY bills.date DESC, bills.createdAt DESC LIMIT 50")
    suspend fun search(query: String): List<Bill>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)

    @Query("UPDATE bills SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteBill(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bills SET isDeleted = 0, deletedAt = null, updatedAt = :restoredAt WHERE id = :id")
    suspend fun restoreBill(id: Long, restoredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun hardDeleteBill(id: Long)

    @Query("UPDATE bills SET isReimbursed = 1, reimbursedDate = :date, updatedAt = :now WHERE id = :id")
    suspend fun markReimbursed(id: Long, date: Long = System.currentTimeMillis(), now: Long = System.currentTimeMillis())

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate AND isDeleted = 0")
    fun getWeeklyExpense(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate AND isDeleted = 0")
    fun getWeeklyIncome(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM bills WHERE date >= :startDate AND date <= :endDate AND isDeleted = 0")
    fun getWeeklyBillCount(startDate: Long, endDate: Long): Flow<Int>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getWeeklyExpenseByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getWeeklyIncomeByCategory(startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("DELETE FROM bills")
    suspend fun deleteAll()

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate AND accountBookId = :bookId AND isDeleted = 0")
    fun getWeeklyExpenseByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate AND accountBookId = :bookId AND isDeleted = 0")
    fun getWeeklyIncomeByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM bills WHERE date >= :startDate AND date <= :endDate AND accountBookId = :bookId AND isDeleted = 0")
    fun getWeeklyBillCountByBook(bookId: Long, startDate: Long, endDate: Long): Flow<Int>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate AND accountBookId = :bookId AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getWeeklyExpenseByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM bills
        WHERE type = 'INCOME' AND date >= :startDate AND date <= :endDate AND accountBookId = :bookId AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getWeeklyIncomeByCategoryByBook(bookId: Long, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId AND isDeleted = 0")
    fun getYearlyExpenseByBook(bookId: Long, year: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'INCOME' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId AND isDeleted = 0")
    fun getYearlyIncomeByBook(bookId: Long, year: String): Flow<Long?>

    @Query("""
        SELECT yearMonth, 
               SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as total
        FROM bills
        WHERE substr(yearMonth,1,4) = :year AND accountBookId = :bookId AND isDeleted = 0
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getYearlyExpenseTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>>

    @Query("""
        SELECT yearMonth, 
               SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as total
        FROM bills
        WHERE substr(yearMonth,1,4) = :year AND accountBookId = :bookId AND isDeleted = 0
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getYearlyIncomeTrendByBook(bookId: Long, year: String): Flow<List<MonthTotal>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'EXPENSE' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getYearlyExpenseByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM bills
        WHERE type = 'INCOME' AND substr(yearMonth,1,4) = :year AND accountBookId = :bookId AND isDeleted = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getYearlyIncomeByCategoryByBook(bookId: Long, year: String): Flow<List<CategoryTotalWithCount>>

    @Query("""
        SELECT category, 0 as total, COUNT(*) as count
        FROM bills
        WHERE type = :type AND isDeleted = 0
        GROUP BY category
        ORDER BY count DESC
    """)
    fun getCategoryUsageCounts(type: String): Flow<List<CategoryTotalWithCount>>

    @Query("UPDATE bills SET category = :newName WHERE category = :oldName AND isDeleted = 0")
    suspend fun updateCategoryName(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM bills WHERE category = :category AND isDeleted = 0")
    suspend fun countByCategory(category: String): Int

    @Query("UPDATE bills SET isDeleted = 1, deletedAt = :now WHERE category = :category AND isDeleted = 0")
    suspend fun softDeleteByCategory(category: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM bills WHERE (walletId = :walletId OR toWalletId = :walletId) AND isDeleted = 0")
    suspend fun countByWallet(walletId: Long): Int

    @Query("UPDATE bills SET isDeleted = 1, deletedAt = :now WHERE (walletId = :walletId OR toWalletId = :walletId) AND isDeleted = 0")
    suspend fun softDeleteByWallet(walletId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM bills WHERE accountBookId = :bookId AND isDeleted = 0")
    suspend fun countByBook(bookId: Long): Int

    @Query("UPDATE bills SET isDeleted = 1, deletedAt = :now WHERE accountBookId = :bookId AND isDeleted = 0")
    suspend fun softDeleteByBook(bookId: Long, now: Long = System.currentTimeMillis())
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
