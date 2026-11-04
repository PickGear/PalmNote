package com.palmnote.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 独立验证 T1/T2/T3：真实 [BillRepositoryImpl] 跑在 in-memory Room 上。
 *
 * - T1：`createBillWithWalletAdjustment` 在 walletId == null（无钱包用户）时是否仍安全写入、不抛异常。
 * - T2：导入是否恰好调整一次余额（支出减、收入加）；导入→撤销后余额是否还原。
 * - T3：撤销只删除本批次的行 id；返回的 id 是真实持久化行 id。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BillImportBalanceRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BillRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BillRepositoryImpl(
            db.billDao(), db.walletDao(), db.billRecycleBinDao(), db,
            db.accountBookDao(), demoOffPrefs()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bill(amount: Long, type: BillType, walletId: Long?, txId: String = "") = Bill(
        amount = amount,
        type = type,
        category = "餐饮",
        date = 1767225600000L,
        yearMonth = "2026-01",
        walletId = walletId,
        transactionId = txId
    )

    private suspend fun balance(id: Long): Long = db.walletDao().getWalletById(id)!!.currentBalance

    // ── T1：null walletId 回归 ──

    @Test
    fun `T1 null walletId with no wallets configured still inserts and does not throw`() = runBlocking {
        val id = repo.createBillWithWalletAdjustment(bill(4500, BillType.EXPENSE, null))

        assertTrue("insert must return a persisted id", id > 0)
        val all = repo.getAllBills().first()
        assertEquals(1, all.size)
        assertNull(all[0].walletId)
        // no wallets exist -> nothing to adjust, still safe
        assertEquals(0, db.walletDao().getAllWallets().first().size)
    }

    @Test
    fun `T1 null walletId with wallets present leaves every balance untouched`() = runBlocking {
        val wid = db.walletDao().insert(Wallet(name = "现金", initialBalance = 100000, currentBalance = 100000))

        val id = repo.createBillWithWalletAdjustment(bill(4500, BillType.EXPENSE, null))

        assertTrue(id > 0)
        assertEquals(1, repo.getAllBills().first().size)
        assertEquals(100000L, balance(wid))
    }

    // ── T2：余额恰好变化一次 ──

    @Test
    fun `T2 expense import reduces wallet balance exactly once by the amount`() = runBlocking {
        val wid = db.walletDao().insert(Wallet(name = "现金", currentBalance = 100000))

        repo.createBillWithWalletAdjustment(bill(4500, BillType.EXPENSE, wid))

        assertEquals(95500L, balance(wid))
        assertEquals(1, repo.getAllBills().first().size)
    }

    @Test
    fun `T2 income import increases wallet balance exactly once by the amount`() = runBlocking {
        val wid = db.walletDao().insert(Wallet(name = "现金", currentBalance = 100000))

        repo.createBillWithWalletAdjustment(bill(500000, BillType.INCOME, wid))

        assertEquals(600000L, balance(wid))
        assertEquals(1, repo.getAllBills().first().size)
    }

    @Test
    fun `T2 expense import then undo restores the original balance`() = runBlocking {
        val wid = db.walletDao().insert(Wallet(name = "现金", currentBalance = 100000))
        val id = repo.createBillWithWalletAdjustment(bill(4500, BillType.EXPENSE, wid))
        assertEquals(95500L, balance(wid))

        repo.deleteBill(id)

        assertEquals(100000L, balance(wid))
        assertEquals(0, repo.getAllBills().first().size)
        // recycle bin keeps an audit copy (same path as manual delete)
        assertEquals(1, db.billRecycleBinDao().getCount().first())
    }

    @Test
    fun `T2 income import then undo restores the original balance`() = runBlocking {
        val wid = db.walletDao().insert(Wallet(name = "现金", currentBalance = 100000))
        val id = repo.createBillWithWalletAdjustment(bill(250000, BillType.INCOME, wid))
        assertEquals(350000L, balance(wid))

        repo.deleteBill(id)

        assertEquals(100000L, balance(wid))
    }

    // ── T3：撤销范围 / 返回 id 真实性 ──

    @Test
    fun `T3 undo removes only the targeted batch and leaves the earlier batch intact`() = runBlocking {
        val wid = db.walletDao().insert(Wallet(name = "现金", currentBalance = 0))

        val batchA = repo.createBillWithWalletAdjustment(bill(1000, BillType.EXPENSE, wid))
        val balanceAfterA = balance(wid) // -1000

        val batchB = repo.createBillWithWalletAdjustment(bill(2000, BillType.EXPENSE, wid))
        assertEquals(-3000L, balance(wid))

        // undo batch B only
        repo.deleteBill(batchB)

        assertEquals(balanceAfterA, balance(wid))
        val remaining = repo.getAllBills().first()
        assertEquals(1, remaining.size)
        assertEquals(batchA, remaining[0].id)
        assertNotNull(repo.getBillById(batchA))
        assertNull(repo.getBillById(batchB))
    }

    @Test
    fun `T3 createBillWithWalletAdjustment returns the real persisted row id`() = runBlocking {
        val wid = db.walletDao().insert(Wallet(name = "现金", currentBalance = 0))

        val id = repo.createBillWithWalletAdjustment(bill(700, BillType.EXPENSE, wid, txId = "TX-1"))

        val loaded = repo.getBillById(id)
        assertNotNull(loaded)
        assertEquals(700L, loaded!!.amount)
        assertEquals("TX-1", loaded.transactionId)
    }

    // ── T4：信用卡钱包存「欠款」（正数 = 欠钱），记账联动方向与普通钱包相反（issue #51）──

    @Test
    fun `T4 credit card expense increases the recorded debt`() = runBlocking {
        val wid = db.walletDao().insert(
            Wallet(name = "信用卡", type = Wallet.TYPE_CREDIT_CARD, currentBalance = 320000)
        )

        repo.createBillWithWalletAdjustment(bill(10000, BillType.EXPENSE, wid))

        // 刷卡消费：欠款 3200 → 3300（普通钱包是减）
        assertEquals(330000L, balance(wid))
    }

    @Test
    fun `T4 credit card income refund reduces the recorded debt`() = runBlocking {
        val wid = db.walletDao().insert(
            Wallet(name = "信用卡", type = Wallet.TYPE_CREDIT_CARD, currentBalance = 320000)
        )

        repo.createBillWithWalletAdjustment(bill(5000, BillType.INCOME, wid))

        // 退款进信用卡：欠款 3200 → 3150
        assertEquals(315000L, balance(wid))
    }

    @Test
    fun `T4 credit card expense then undo restores the original debt`() = runBlocking {
        val wid = db.walletDao().insert(
            Wallet(name = "信用卡", type = Wallet.TYPE_CREDIT_CARD, currentBalance = 320000)
        )
        val id = repo.createBillWithWalletAdjustment(bill(10000, BillType.EXPENSE, wid))
        assertEquals(330000L, balance(wid))

        repo.deleteBill(id)

        assertEquals(320000L, balance(wid))
    }

    @Test
    fun `T4 transfer into credit card repays debt and transfer out raises it`() = runBlocking {
        val credit = db.walletDao().insert(
            Wallet(name = "信用卡", type = Wallet.TYPE_CREDIT_CARD, currentBalance = 320000)
        )
        val cash = db.walletDao().insert(Wallet(name = "现金", currentBalance = 100000))

        // 现金 → 信用卡：还款，欠款下降
        repo.createBillWithWalletAdjustment(
            bill(50000, BillType.TRANSFER, cash).copy(toWalletId = credit)
        )
        assertEquals(270000L, balance(credit))
        assertEquals(50000L, balance(cash))

        // 信用卡 → 现金：取现，欠款上升
        repo.createBillWithWalletAdjustment(
            bill(20000, BillType.TRANSFER, credit).copy(toWalletId = cash)
        )
        assertEquals(290000L, balance(credit))
        assertEquals(70000L, balance(cash))
    }

    @Test
    fun `T4 editing a credit card bill reapplies the debt in the flipped direction`() = runBlocking {
        val credit = db.walletDao().insert(
            Wallet(name = "信用卡", type = Wallet.TYPE_CREDIT_CARD, currentBalance = 320000)
        )
        val cash = db.walletDao().insert(Wallet(name = "现金", currentBalance = 100000))

        // 原记在现金上的支出，改成信用卡支付：现金回补、信用卡欠款上升
        val id = repo.createBillWithWalletAdjustment(bill(8000, BillType.EXPENSE, cash))
        assertEquals(92000L, balance(cash))

        repo.updateBillWithWalletAdjustment(bill(8000, BillType.EXPENSE, credit).copy(id = id))

        assertEquals(100000L, balance(cash))
        assertEquals(328000L, balance(credit))
    }
}
