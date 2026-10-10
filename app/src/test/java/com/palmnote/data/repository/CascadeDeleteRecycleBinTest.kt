package com.palmnote.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Asset
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
 * 「删除xx（连同账单/物品）」三个级联入口的回收站验收：真实 Repository 跑在 in-memory Room 上。
 *
 * 2026-10-11 回收站覆盖度审计发现的三个硬删漏洞，这里逐个钉死回归：
 * - 钱包管理「删除钱包（连同账单）」此前直调 `billDao.deleteByWallet`，账单无法恢复；
 * - 账本管理「删除账本（连同账单）」此前直调 `billDao.deleteByBook`，同病；
 * - 分类管理「删除分类」的物品部分此前直调 `assetDao.deleteByCategory`，物品全没。
 * 修法都是走各自的回收站包装，本测试验证「删得掉、进得去、捞得回、别误伤」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class CascadeDeleteRecycleBinTest {

    private lateinit var db: AppDatabase
    private lateinit var billRepo: BillRepositoryImpl
    private lateinit var walletRepo: WalletRepositoryImpl
    private lateinit var bookRepo: AccountBookRepositoryImpl
    private lateinit var assetRepo: AssetRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        billRepo = BillRepositoryImpl(db.billDao(), db.walletDao(), db.billRecycleBinDao(), db)
        walletRepo = WalletRepositoryImpl(db.walletDao(), billRepo, db, context)
        bookRepo = AccountBookRepositoryImpl(db.accountBookDao(), billRepo, db, context)
        assetRepo = AssetRepositoryImpl(db.assetDao(), db.assetRecycleBinDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun expense(amount: Long) = Bill(
        amount = amount,
        type = BillType.EXPENSE,
        category = "餐饮",
        date = DATE,
        yearMonth = YEAR_MONTH
    )

    private fun income(amount: Long) = Bill(
        amount = amount,
        type = BillType.INCOME,
        category = "工资",
        date = DATE,
        yearMonth = YEAR_MONTH
    )

    // ── 钱包级联 ──

    @Test
    fun `deleteWalletWithData sends its bills to the recycle bin and spares other wallets`() = runBlocking {
        val walletId = db.walletDao().insert(Wallet(name = "现金", initialBalance = 0, currentBalance = 0))
        val otherId = db.walletDao().insert(Wallet(name = "银行卡", initialBalance = 0, currentBalance = 0))
        val e1 = billRepo.insertBill(expense(100000).copy(walletId = walletId))
        val i1 = billRepo.insertBill(income(50000).copy(walletId = walletId))
        val e2 = billRepo.insertBill(expense(20000).copy(walletId = otherId))

        walletRepo.deleteWalletWithData(walletId)

        assertNull("钱包本体要被删掉", db.walletDao().getWalletById(walletId))
        assertNotNull("别的钱包不能被误伤", db.walletDao().getWalletById(otherId))

        val bin = db.billRecycleBinDao().getAll().first()
        assertEquals("该钱包的账单全部进回收站", setOf(e1, i1), bin.map { it.originalId }.toSet())
        assertEquals("账单表只剩别的钱包的", listOf(e2), billRepo.getAllBills().first().map { it.id })
    }

    @Test
    fun `bills recycled by wallet cascade can be restored`() = runBlocking {
        val walletId = db.walletDao().insert(Wallet(name = "现金", initialBalance = 0, currentBalance = 0))
        val e1 = billRepo.insertBill(expense(100000).copy(walletId = walletId))

        walletRepo.deleteWalletWithData(walletId)
        val binId = db.billRecycleBinDao().getAll().first().single { it.originalId == e1 }.id

        billRepo.restoreBill(binId)

        assertNotNull("回收站恢复后账单要回来", billRepo.getBillById(e1))
    }

    // ── 账本级联 ──

    @Test
    fun `deleteAccountBookWithData sends its bills to the recycle bin and spares other books`() = runBlocking {
        val bookId = db.accountBookDao().insertBook(AccountBook(name = "旅行"))
        val otherBookId = db.accountBookDao().insertBook(AccountBook(name = "日常"))
        val b1 = billRepo.insertBill(expense(100000).copy(accountBookId = bookId))
        val b2 = billRepo.insertBill(expense(30000).copy(accountBookId = otherBookId))

        bookRepo.deleteAccountBookWithData(bookId)

        assertNull("账本本体要被删掉", db.accountBookDao().getBookById(bookId))
        assertNotNull("别的账本不能被误伤", db.accountBookDao().getBookById(otherBookId))

        val bin = db.billRecycleBinDao().getAll().first()
        assertEquals("该账本的账单全部进回收站", listOf(b1), bin.map { it.originalId })
        assertEquals("账单表只剩别的账本的", listOf(b2), billRepo.getAllBills().first().map { it.id })
    }

    // ── 物品按分类删除 ──

    @Test
    fun `deleteByCategory recycles only that category and is restorable`() = runBlocking {
        val a1 = db.assetDao().insertAsset(Asset(name = "牛奶", category = "食品"))
        val a2 = db.assetDao().insertAsset(Asset(name = "酸奶", category = "食品"))
        val a3 = db.assetDao().insertAsset(Asset(name = "耳机", category = "数码"))

        assetRepo.deleteByCategory("食品")

        assertNull(db.assetDao().getAssetById(a1))
        assertNull(db.assetDao().getAssetById(a2))
        assertNotNull("别的分类不能被误伤", db.assetDao().getAssetById(a3))

        val bin = db.assetRecycleBinDao().getAll().first()
        assertEquals("该分类的物品全部进回收站", setOf(a1, a2), bin.map { it.originalId }.toSet())

        assetRepo.restoreAsset(bin.single { it.originalId == a1 }.id)
        assertNotNull("回收站恢复后物品要回来", db.assetDao().getAssetById(a1))
        assertTrue("恢复后回收站里该物品的行要清掉", db.assetRecycleBinDao().getAll().first().none { it.originalId == a1 })
    }

    private companion object {
        /** 2026-01-01 08:00 (UTC+8)。 */
        const val DATE = 1767225600000L
        const val YEAR_MONTH = "2026-01"
    }
}
