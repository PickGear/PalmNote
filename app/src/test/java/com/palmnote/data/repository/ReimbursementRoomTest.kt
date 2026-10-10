package com.palmnote.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import com.palmnote.domain.repository.ReimbursementAllocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 报销管理的数据层验收：真实 [BillRepositoryImpl] 跑在 in-memory Room 上。
 *
 * 覆盖的都是「只看代码不容易看出对不对」的地方：
 * - 记收入与分摊是否同一事务、分摊后支出状态是否正确；
 * - 部分报销（`reimbursedAmount < amount`）是否仍算待报销；
 * - 超额报销是否被封顶（不允许 `reimbursedAmount > amount`）；
 * - 历史脏数据（收入被标了「可报销」）是否被排除在报销列表外；
 * - 删除报销收入后关联支出的解绑；
 * - 撤销报销时「代记收入」的连带删除，以及一收入对多支出时不能误伤其他支出；
 * - 报表「净支出」口径的两个分量。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ReimbursementRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BillRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BillRepositoryImpl(db.billDao(), db.walletDao(), db.billRecycleBinDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun expense(amount: Long) = Bill(
        amount = amount,
        type = BillType.EXPENSE,
        category = "差旅",
        date = DATE,
        yearMonth = YEAR_MONTH,
        isReimbursable = true
    )

    private fun reimbursementIncome(amount: Long) = Bill(
        amount = amount,
        type = BillType.INCOME,
        category = REIMBURSEMENT_CATEGORY,
        date = DATE,
        yearMonth = YEAR_MONTH
    )

    // ── 记收入 + 分摊 ──

    @Test
    fun `createReimbursementIncome records income and marks the expense fully reimbursed`() = runBlocking {
        val expenseId = repo.insertBill(expense(100000))

        val incomeId = repo.createReimbursementIncome(
            income = reimbursementIncome(100000),
            allocations = listOf(ReimbursementAllocation(expenseId, 100000))
        )

        assertTrue("必须返回真实持久化的收入 id", incomeId > 0)
        assertEquals(100000L, repo.getBillById(incomeId)?.amount)

        val saved = requireNotNull(repo.getBillById(expenseId))
        assertEquals("分摊额要落到支出上", 100000L, saved.reimbursedAmount)
        assertTrue("报满了才置 isReimbursed", saved.isReimbursed)
        assertEquals("要记下关联的收入 id", incomeId, saved.reimbursedByBillId)

        // 报满了就不再待报销
        assertTrue(repo.getUnreimbursedBills().first().isEmpty())
        assertEquals(1, repo.getReimbursedBills().first().size)
    }

    @Test
    fun `partial allocation keeps the expense pending with the remaining amount`() = runBlocking {
        val expenseId = repo.insertBill(expense(100000))

        repo.createReimbursementIncome(
            income = reimbursementIncome(40000),
            allocations = listOf(ReimbursementAllocation(expenseId, 40000))
        )

        val saved = requireNotNull(repo.getBillById(expenseId))
        assertEquals(40000L, saved.reimbursedAmount)
        assertFalse("只报了一部分，不能算已报销", saved.isReimbursed)
        assertEquals(1, repo.getUnreimbursedBills().first().size)

        val summary = requireNotNull(repo.getPendingReimbursementSummary().first())
        assertEquals("剩余可报额 = 原额 - 已报额", 60000L, summary.remaining)
        assertEquals(1, summary.count)
    }

    @Test
    fun `one income can cover several expenses and is reverse-lookup-able`() = runBlocking {
        val a = repo.insertBill(expense(100000))
        val b = repo.insertBill(expense(50000))

        val incomeId = repo.createReimbursementIncome(
            income = reimbursementIncome(150000),
            allocations = listOf(
                ReimbursementAllocation(a, 100000),
                ReimbursementAllocation(b, 50000)
            )
        )

        val covered = repo.getBillsReimbursedBy(incomeId)
        assertEquals(2, covered.size)
        assertEquals(setOf(a, b), covered.map { it.id }.toSet())
        assertTrue(repo.getUnreimbursedBills().first().isEmpty())
    }

    // ── 封顶 ──

    @Test
    fun `over-claiming is capped at the expense amount`() = runBlocking {
        val expenseId = repo.insertBill(expense(100000))

        repo.applyReimbursement(expenseId = expenseId, paid = 150000, date = DATE, byBillId = null)
        val saved = requireNotNull(repo.getBillById(expenseId))
        assertEquals("多报出来的钱不属于这笔支出，不能溢出", 100000L, saved.reimbursedAmount)
        assertTrue(saved.isReimbursed)

        val summary = requireNotNull(repo.getPendingReimbursementSummary().first())
        assertNull("报满了就没有剩余额，SUM 为 NULL", summary.remaining)
        assertEquals(0, summary.count)
    }

    // ── 历史脏数据 ──

    @Test
    fun `income flagged reimbursable is kept out of the reimbursement lists`() = runBlocking {
        repo.insertBill(expense(100000))
        // v1.4.0 的「可报销」开关无条件显示，历史数据里可能出现这种组合
        repo.insertBill(reimbursementIncome(50000).copy(isReimbursable = true))

        val pending = repo.getUnreimbursedBills().first()
        assertEquals("收入不该出现在待报销列表里", 1, pending.size)
        assertTrue(pending.all { it.type == BillType.EXPENSE })

        val summary = requireNotNull(repo.getPendingReimbursementSummary().first())
        assertEquals("汇总口径要和列表一致", 100000L, summary.remaining)
        assertEquals(1, summary.count)
    }

    // ── 删除联动 ──

    @Test
    fun `deleting the reimbursement income returns the expense to pending`() = runBlocking {
        val expenseId = repo.insertBill(expense(100000))
        val incomeId = repo.createReimbursementIncome(
            income = reimbursementIncome(100000),
            allocations = listOf(ReimbursementAllocation(expenseId, 100000))
        )
        assertTrue(requireNotNull(repo.getBillById(expenseId)).isReimbursed)

        repo.deleteBill(incomeId)

        val saved = requireNotNull(repo.getBillById(expenseId))
        assertEquals(0L, saved.reimbursedAmount)
        assertFalse(saved.isReimbursed)
        assertNull(saved.reimbursedDate)
        assertNull("不能留悬空关联", saved.reimbursedByBillId)
        assertNotNull("支出本身不能被连带删除", repo.getBillById(expenseId))

        // 退回待报销
        assertEquals(1, repo.getUnreimbursedBills().first().size)
    }

    @Test
    fun `deleting an unrelated expense does not disturb other reimbursements`() = runBlocking {
        val linked = repo.insertBill(expense(100000))
        val other = repo.insertBill(expense(30000))
        val incomeId = repo.createReimbursementIncome(
            income = reimbursementIncome(100000),
            allocations = listOf(ReimbursementAllocation(linked, 100000))
        )

        repo.deleteBill(other)

        val saved = requireNotNull(repo.getBillById(linked))
        assertTrue(saved.isReimbursed)
        assertEquals(incomeId, saved.reimbursedByBillId)
    }

    // ── 撤销 ──

    @Test
    fun `clearReimbursement returns the expense to pending and removes the income it recorded`() = runBlocking {
        val expenseId = repo.insertBill(expense(100000))
        val incomeId = repo.createReimbursementIncome(
            income = reimbursementIncome(100000),
            allocations = listOf(ReimbursementAllocation(expenseId, 100000))
        )
        assertEquals(1, repo.getReimbursedBills().first().size)

        repo.clearReimbursement(expenseId)

        val saved = requireNotNull(repo.getBillById(expenseId))
        assertEquals(0L, saved.reimbursedAmount)
        assertFalse(saved.isReimbursed)
        assertNull(saved.reimbursedByBillId)
        assertNull("代记的收入要一并撤销，不能留在账上", repo.getBillById(incomeId))
        assertEquals("支出本身不能被连带删除", expenseId, repo.getBillById(expenseId)?.id)

        // 退回待报销
        assertEquals(1, repo.getUnreimbursedBills().first().size)
        assertTrue(repo.getReimbursedBills().first().isEmpty())

        // 删除走回收站，那笔收入还能捞回来
        assertTrue(
            "撤销删掉的收入应进回收站（可恢复）",
            db.billRecycleBinDao().getAll().first().any { it.originalId == incomeId }
        )
    }

    @Test
    fun `clearReimbursement keeps an income that still covers another expense`() = runBlocking {
        val a = repo.insertBill(expense(100000))
        val b = repo.insertBill(expense(50000))
        val incomeId = repo.createReimbursementIncome(
            income = reimbursementIncome(150000),
            allocations = listOf(
                ReimbursementAllocation(a, 100000),
                ReimbursementAllocation(b, 50000)
            )
        )

        repo.clearReimbursement(a)

        assertEquals("A 退回未报销", 0L, requireNotNull(repo.getBillById(a)).reimbursedAmount)
        assertNull(requireNotNull(repo.getBillById(a)).reimbursedByBillId)
        assertNotNull("收入还被 B 指着，不能删", repo.getBillById(incomeId))
        assertEquals(
            "B 的报销不能被误伤",
            incomeId,
            requireNotNull(repo.getBillById(b)).reimbursedByBillId
        )
        assertTrue(requireNotNull(repo.getBillById(b)).isReimbursed)
    }

    @Test
    fun `clearReimbursement on a mark-only expense touches no other bill`() = runBlocking {
        val expenseId = repo.insertBill(expense(100000))
        // 「钱已经在别处记过收入了，这里只标一下」：没有代记收入
        repo.applyReimbursement(expenseId = expenseId, paid = 100000, date = DATE, byBillId = null)
        val before = repo.getAllBills().first().size

        repo.clearReimbursement(expenseId)

        assertEquals(0L, requireNotNull(repo.getBillById(expenseId)).reimbursedAmount)
        assertNull(requireNotNull(repo.getBillById(expenseId)).reimbursedByBillId)
        assertEquals("没有代记收入可删，账单总数不变", before, repo.getAllBills().first().size)
        assertTrue("没有删任何东西，回收站应为空", db.billRecycleBinDao().getAll().first().isEmpty())
    }

    @Test
    fun `revoking takes the reimbursement income back out of the wallet`() = runBlocking {
        val walletId = db.walletDao().insert(
            Wallet(name = "现金", initialBalance = 0, currentBalance = 0)
        )
        val expenseId = repo.insertBill(expense(100000).copy(walletId = walletId))
        repo.createReimbursementIncome(
            income = reimbursementIncome(60000).copy(walletId = walletId),
            allocations = listOf(ReimbursementAllocation(expenseId, 60000))
        )
        assertEquals(60000L, db.walletDao().getWalletById(walletId)?.currentBalance)

        repo.clearReimbursement(expenseId)

        assertEquals(
            "撤销后那笔进账要回吐",
            0L,
            db.walletDao().getWalletById(walletId)?.currentBalance
        )
    }

    // ── 报表净支出口径 ──

    @Test
    fun `expense breakdown splits out the reimbursed part`() = runBlocking {
        val a = repo.insertBill(expense(100000))
        repo.insertBill(expense(50000))
        repo.createReimbursementIncome(
            income = reimbursementIncome(60000),
            allocations = listOf(ReimbursementAllocation(a, 60000))
        )

        val breakdown = requireNotNull(repo.getMonthlyExpenseBreakdown(YEAR_MONTH).first())
        assertEquals("总支出不受报销影响", 150000L, breakdown.expense)
        assertEquals("已报销部分单列", 60000L, breakdown.reimbursed)
        // 净支出 = 150000 - 60000 = 90000，由 ReportData.netExpense 推出
    }

    // ── 钱包联动 ──

    @Test
    fun `reimbursement income credits the wallet it lands in`() = runBlocking {
        val walletId = db.walletDao().insert(
            Wallet(name = "现金", initialBalance = 0, currentBalance = 0)
        )

        val expenseId = repo.insertBill(expense(100000).copy(walletId = walletId))
        repo.createReimbursementIncome(
            income = reimbursementIncome(60000).copy(walletId = walletId),
            allocations = listOf(ReimbursementAllocation(expenseId, 60000))
        )

        assertEquals(
            "报销收入要真的进账",
            60000L,
            db.walletDao().getWalletById(walletId)?.currentBalance
        )
    }

    private companion object {
        /** 2026-01-01 08:00 (UTC+8)。 */
        const val DATE = 1767225600000L
        const val YEAR_MONTH = "2026-01"
        const val REIMBURSEMENT_CATEGORY = "报销"
    }
}
