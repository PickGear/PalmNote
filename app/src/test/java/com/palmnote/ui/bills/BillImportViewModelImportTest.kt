package com.palmnote.ui.bills

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.Wallet
import com.palmnote.data.repository.BillRepositoryImpl
import com.palmnote.domain.model.BillType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * 独立验证 T1 / T3 / T5：真实 [BillImportViewModel] 驱动真实 [BillRepositoryImpl] + in-memory Room。
 *
 * 覆盖：
 * - T1：无钱包用户（importWalletId/walletId 均为 null）导入仍成功、无崩溃、余额不动。
 * - T3：导入两次后撤销一次只回滚第二批；reset 清空内存中的撤销 id（无持久化）。
 * - T5：txId 批内去重 + 库内去重都计入 skip；解析期失败既不计导入也不计跳过。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BillImportViewModelImportTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repo: BillRepositoryImpl
    private lateinit var ctx: Context
    private var walletId: Long = 0
    private lateinit var vm: BillImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BillRepositoryImpl(db.billDao(), db.walletDao(), db.billRecycleBinDao(), db)
        walletId = runBlocking {
            db.walletDao().insert(Wallet(name = "现金", initialBalance = 100000, currentBalance = 100000))
        }
        val wallet = runBlocking { db.walletDao().getWalletById(walletId)!! }
        vm = newViewModel(MutableStateFlow(listOf(wallet)))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun newViewModel(wallets: MutableStateFlow<List<Wallet>>) = BillImportViewModel(
        ctx,
        repo,
        wallets,
        MutableStateFlow<List<CategoryConfig>>(emptyList()),
        MutableStateFlow<List<AccountBook>>(emptyList()),
        mockk(relaxed = true),
        mockk(relaxed = true)
    )

    /** 反复推进测试调度器并短暂让出真实 IO 线程，直到条件成立或超时。 */
    private fun settle(timeoutMs: Long = 10_000, until: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (until()) return
            Thread.sleep(5)
        }
        testDispatcher.scheduler.advanceUntilIdle()
        if (!until()) {
            throw AssertionError("condition not met within ${timeoutMs}ms; stage=${vm.state.value.stage}")
        }
    }

    private fun balanceNow(): Long = runBlocking { db.walletDao().getWalletById(walletId)!!.currentBalance }
    private fun billsNow() = runBlocking { db.billDao().getAllBills().first() }

    /** 模拟"用户点开文件导入"：mock 一个只提供字节流的 Context。 */
    private fun fileContext(bytes: ByteArray): Context {
        val context = mockk<Context>(relaxed = true)
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        return context
    }

    private fun wechatCsv(vararg rows: String): ByteArray {
        val header = "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号"
        return (listOf(header) + rows).joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    private fun importOneViaOcr(amount: String, merchant: String, date: String) {
        vm.updateOcrAmount(amount)
        vm.updateOcrMerchant(merchant)
        vm.updateOcrDate(date)
        vm.updateOcrCategory("餐饮")
        vm.updateOcrType(BillType.EXPENSE)
        vm.saveOcrSelected()
    }

    // ── T1：无钱包用户 ──

    @Test
    fun `T1 importing with no wallets configured still succeeds with null walletId`() {
        val noWalletVm = newViewModel(MutableStateFlow(emptyList()))
        testDispatcher.scheduler.advanceUntilIdle()

        noWalletVm.updateOcrAmount("45.00")
        noWalletVm.updateOcrMerchant("美团")
        noWalletVm.updateOcrDate("2026-07-20")
        noWalletVm.updateOcrType(BillType.EXPENSE)
        noWalletVm.saveOcrSelected()
        settle { noWalletVm.state.value.stage == ImportStage.DONE }

        assertEquals(1, noWalletVm.state.value.importCount)
        val saved = billsNow().first()
        assertNull(saved.walletId)
        // pre-existing wallet untouched (bill was not linked to it)
        assertEquals(100000L, balanceNow())
    }

    // ── T5：计数 ──

    @Test
    fun `T5 importing an identical OCR bill twice skips the second (attribute dedup)`() {
        importOneViaOcr("45.00", "美团", "2026-07-20")
        settle { vm.state.value.stage == ImportStage.DONE }
        assertEquals(1, vm.state.value.importCount)
        assertEquals(0, vm.state.value.skippedCount)

        importOneViaOcr("45.00", "美团", "2026-07-20")
        settle { vm.state.value.stage == ImportStage.DONE }
        assertEquals(0, vm.state.value.importCount)
        assertEquals(1, vm.state.value.skippedCount)
        assertEquals(1, billsNow().size)
    }

    @Test
    fun `T5 duplicate transactionId within one file is imported once and skipped once`() {
        val bytes = wechatCsv(
            "2026-07-20 12:30:00,商户消费,美团,,支出,45.00,微信支付,已支付,TX-DUP",
            "2026-07-20 13:30:00,商户消费,饿了么,,支出,30.00,微信支付,已支付,TX-DUP",
            "2026-07-21 09:00:00,商户消费,京东,,支出,20.00,微信支付,已支付,TX-NEW"
        )
        vm.parseFile(fileContext(bytes), Uri.parse("content://t/a.csv"), "a.csv")
        settle { vm.state.value.stage == ImportStage.PREVIEW }
        assertEquals(3, vm.state.value.parsed.size)

        vm.importSelected()
        settle { vm.state.value.stage == ImportStage.DONE }

        assertEquals(2, vm.state.value.importCount)
        assertEquals(1, vm.state.value.skippedCount)
        assertEquals(2, billsNow().size)
    }

    @Test
    fun `T5 re-importing the same file skips rows already in DB`() {
        val bytes = wechatCsv(
            "2026-07-20 12:30:00,商户消费,美团,,支出,45.00,微信支付,已支付,TX-A",
            "2026-07-20 13:30:00,商户消费,饿了么,,支出,30.00,微信支付,已支付,TX-B"
        )
        vm.parseFile(fileContext(bytes), Uri.parse("content://t/b.csv"), "b.csv")
        settle { vm.state.value.stage == ImportStage.PREVIEW }
        vm.importSelected()
        settle { vm.state.value.stage == ImportStage.DONE }
        assertEquals(2, vm.state.value.importCount)

        vm.parseFile(fileContext(bytes), Uri.parse("content://t/b.csv"), "b.csv")
        settle { vm.state.value.stage == ImportStage.PREVIEW }
        vm.importSelected()
        settle { vm.state.value.stage == ImportStage.DONE }

        assertEquals(0, vm.state.value.importCount)
        assertEquals(2, vm.state.value.skippedCount)
        assertEquals(2, billsNow().size)
    }

    @Test
    fun `T5 parse-time failure is neither imported nor skipped`() {
        val bytes = wechatCsv(
            "2026-07-20 12:30:00,商户消费,美团,,支出,45.00,微信支付,已支付,TX-OK",
            "2026-07-20 13:30:00,商户消费,饿了么,,支出,,微信支付,已支付,TX-BAD"
        )
        vm.parseFile(fileContext(bytes), Uri.parse("content://t/c.csv"), "c.csv")
        settle { vm.state.value.stage == ImportStage.PREVIEW }

        assertEquals(1, vm.state.value.parsed.size)
        assertEquals(1, vm.state.value.failures.size)

        vm.importSelected()
        settle { vm.state.value.stage == ImportStage.DONE }

        assertEquals(1, vm.state.value.importCount)
        assertEquals(0, vm.state.value.skippedCount)
        assertEquals(
            vm.state.value.parsed.size,
            vm.state.value.importCount + vm.state.value.skippedCount
        )
    }

    // ── T3：撤销范围 / 内存态 ──

    @Test
    fun `T3 undoImport reverts only the latest batch and restores that balance`() {
        val start = balanceNow()

        importOneViaOcr("45.00", "美团", "2026-07-20")
        settle { vm.state.value.stage == ImportStage.DONE }
        val afterA = balanceNow()
        assertEquals(start - 4500, afterA)

        importOneViaOcr("12.00", "地铁", "2026-07-21")
        settle { vm.state.value.stage == ImportStage.DONE }
        assertEquals(start - 4500 - 1200, balanceNow())

        vm.undoImport()
        settle { vm.state.value.actionMessage != null }

        assertEquals(afterA, balanceNow())
        assertEquals(1, billsNow().size)
        assertTrue(vm.state.value.importedBillIds.isEmpty())
    }

    @Test
    fun `T3 reset clears the in-memory undo ids`() {
        importOneViaOcr("45.00", "美团", "2026-07-20")
        settle { vm.state.value.stage == ImportStage.DONE }
        assertTrue(vm.state.value.importedBillIds.isNotEmpty())

        vm.reset()

        assertTrue(vm.state.value.importedBillIds.isEmpty())
    }
}
