package com.palmnote.ui.bills

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
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
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import java.nio.charset.Charset

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

    /** 本测建过的所有 VM：tearDown 先取消它们的 viewModelScope，再关库。 */
    private val createdViewModels = mutableListOf<BillImportViewModel>()

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
        // 顺序要紧：先取消 VM 的 viewModelScope（里面挂着 stateIn 分享协程与
        // withContext(IO) 的导入任务），再排空调度器，最后才关库。
        // 反过来的话，还在跑的 IO 任务会撞上已关闭的连接池，在真实线程上抛未捕获异常，
        // 记到下一个测试头上（UncaughtExceptionsBeforeTest）。
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        testDispatcher.scheduler.advanceUntilIdle()
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
    ).also { createdViewModels += it }

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

    /**
     * 模拟"用户点开文件导入"：只替换文件流来源。
     * 用 spyk 而不是纯 mock：parseFile 会把传入的 context 用于 getString（错误/诊断文案），
     * 纯 mock 的 getString 返回空串，会让「文案对不对」这类断言失去意义。
     */
    private fun fileContext(bytes: ByteArray): Context {
        val context = spyk(ctx)
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

    /**
     * 旧版 Excel（.xls，BIFF8）是 OLE2 复合文档：既不是 zip 也不是文本。
     * 按文本硬解只会得到乱码、最后报一句「未能解析」。这里断言给出的是一条能照做的指引。
     */
    @Test
    fun `legacy BIFF xls gets an actionable message instead of a vague parse failure`() {
        val ole2 = byteArrayOf(
            0xD0.toByte(),
            0xCF.toByte(),
            0x11,
            0xE0.toByte(),
            0xA1.toByte(),
            0xB1.toByte(),
            0x1A,
            0xE1.toByte()
        ) + ByteArray(64)

        vm.parseFile(fileContext(ole2), Uri.parse("content://t/legacy.xls"), "legacy.xls")
        settle { vm.state.value.stage == ImportStage.ERROR }

        assertEquals(
            ctx.getString(com.palmnote.app.R.string.bill_import_error_legacy_xls),
            vm.state.value.error
        )
        assertTrue(vm.state.value.diagnostic.contains("OLE2"))
    }

    /**
     * 银行网页导出的「.xls」是 HTML 表格套壳：走完整链路验证 VM 里 normalizeLines 的接线。
     */
    @Test
    fun `html table export with xls name imports through the whole pipeline`() {
        val html = (
            "<html><body><table>" +
                "<tr><td>交易日期</td><td>摘要</td><td>交易金额</td><td>收支</td><td>对方户名</td></tr>" +
                "<tr><td>2026-07-20</td><td>消费</td><td>45.00</td><td>支出</td><td>星巴克</td></tr>" +
                "</table></body></html>"
            ).toByteArray(Charsets.UTF_8)

        vm.parseFile(fileContext(html), Uri.parse("content://t/bank.xls"), "bank.xls")
        settle { vm.state.value.stage == ImportStage.PREVIEW }

        assertEquals(1, vm.state.value.parsed.size)
        assertEquals(4500L, vm.state.value.parsed[0].amount)
        assertEquals("星巴克", vm.state.value.parsed[0].merchant)
    }

    // ── 编码：支付宝在 Windows 导出的 .csv 是 GBK，不是 UTF-8 ──
    /**
     * issue#1 的原始报障场景：「支付宝下载的 .csv 无法解析」。
     * 实测原因之一是编码——按 UTF-8 硬解 GBK 字节会得到乱码，表头（含「支付宝」「交易创建时间」）
     * 匹配不上，整份文件被判为未知格式而报「未能解析」。这条测试锁住解码回退按 GBK 走通。
     */
    @Test
    fun `GBK 编码的支付宝 CSV 仍能识别为支付宝账单并解析出行`() {
        val header =
            "交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方," +
                "商品名称,金额（元）,收/支,交易状态,服务费（元）,成功退款（元）,备注,资金状态"
        val row =
            "TEST_GBK_0001,ORD_1,2026-07-20 12:30:00,2026-07-20 12:31:00,2026-07-20 12:31:00," +
                "其他,即时到账-商户,肯德基,汉堡套餐,45.00,支出,交易成功,0.00,,,已支出"
        val bytes = listOf("支付宝交易记录明细查询", header, row)
            .joinToString("\r\n")
            .toByteArray(Charset.forName("GBK"))

        vm.parseFile(fileContext(bytes), Uri.parse("content://t/gbk.csv"), "gbk.csv")
        settle { vm.state.value.stage == ImportStage.PREVIEW }

        assertEquals("GBK 编码不该被判为未知格式（解析出 0 条）", 1, vm.state.value.parsed.size)
        val bill = vm.state.value.parsed.single()
        assertEquals("肯德基", bill.merchant)
        assertEquals("汉堡套餐", bill.note)
        assertEquals(4500L, bill.amount)
        assertEquals("ALIPAY", bill.paymentMethod)
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
