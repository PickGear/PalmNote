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
            "ORDER BY date DESC, createdAt DESC LIMIT 5000"
    )
    fun getBillsByMonth(yearMonth: String): Flow<List<Bill>>

    @Query(
        "SELECT * FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth " +
            "ORDER BY date DESC, createdAt DESC LIMIT 5000"
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

    @Query("SELECT * FROM bills WHERE isReimbursable = 1 AND isReimbursed = 0 ORDER BY date DESC")
    fun getUnreimbursedBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE recurringId IS NOT NULL ORDER BY date DESC")
    fun getRecurringBills(): Flow<List<Bill>>

    @Query("SELECT COUNT(*) FROM bills WHERE yearMonth = :yearMonth")
    fun getMonthlyBillCount(yearMonth: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM bills WHERE accountBookId = :bookId AND yearMonth = :yearMonth")
    fun getMonthlyBillCountByBook(bookId: Long, yearMonth: String): Flow<Int>

    @Query("SELECT SUM(amount) FROM bills WHERE yearMonth = :yearMonth AND type = 'EXPENSE'")
    fun getMonthlyExpense(yearMonth: String): Flow<Long?>

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



    @Query("UPDATE bills SET isReimbursed = 1, reimbursedDate = :date, updatedAt = :now WHERE id = :id")
    suspend fun markReimbursed(id: Long, date: Long = System.currentTimeMillis(), now: Long = System.currentTimeMillis())

    @Query("SELECT SUM(amount) FROM bills WHERE type = 'EXPENSE' AND date >= :startDate AND date <= :endDate")
    fun getWeeklyExpense(startDate: Long, endDate: Long): Flow<Long?>

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
