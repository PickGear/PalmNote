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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.Base64

/**
 * 加密 zip（传统 ZipCrypto）→ 账单预览的端到端用例。
 *
 * 覆盖此前**完全没测到**的一段：`parseFile` 认出加密条目 → 停「输入密码」态 →
 * `submitZipPassword` 解出 CSV → 继续按 CSV 解析（`ZipArchiveReader` 只有单测，
 * VM 层的 zip/密码路径一条用例都没有）。
 *
 * 夹具的 CSV 用的是支付宝 **App 版表头**（`交易时间 … 商品说明 … 收/支 …`），
 * 也就是表头判据一旦漂移就会解析成 0 条的那一类。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BillImportZipPasswordTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repo: BillRepositoryImpl
    private lateinit var ctx: Context
    private lateinit var vm: BillImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = BillRepositoryImpl(db.billDao(), db.walletDao(), db.billRecycleBinDao(), db)
        val walletId = runBlocking {
            db.walletDao().insert(Wallet(name = "现金", initialBalance = 100000, currentBalance = 100000))
        }
        val wallet = runBlocking { db.walletDao().getWalletById(walletId)!! }
        vm = BillImportViewModel(
            ctx, repo, MutableStateFlow(listOf(wallet)),
            MutableStateFlow<List<CategoryConfig>>(emptyList()),
            MutableStateFlow<List<AccountBook>>(emptyList()),
            mockk(relaxed = true), mockk(relaxed = true)
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        // 先取消 viewModelScope 再排空调度器，否则残留的 IO 任务会撞上已关闭的连接池
        vm.viewModelScope.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        db.close()
    }

    private fun settle(timeoutMs: Long = 10_000, until: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (until()) return
            Thread.sleep(5)
        }
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun fileContext(bytes: ByteArray): Context {
        val context = mockk<Context>(relaxed = true)
        val resolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        return context
    }

    private fun openZip() {
        vm.parseFile(
            fileContext(Base64.getDecoder().decode(ENCRYPTED_APP_HEADER_B64)),
            Uri.parse("content://t/cashbook.zip"),
            "记账本明细记录.zip"
        )
        settle { vm.state.value.stage == ImportStage.PASSWORD }
        assertTrue("加密 zip 应停在输入密码态", vm.state.value.zipPasswordRequired)
    }

    @Test
    fun `加密 zip 用正确密码解出后进入预览并解析出行`() {
        openZip()

        vm.submitZipPassword(PASSWORD)
        settle { vm.state.value.stage == ImportStage.PREVIEW || vm.state.value.stage == ImportStage.ERROR }

        assertEquals("正确密码应进入预览（而不是 0 条报错）", ImportStage.PREVIEW, vm.state.value.stage)
        assertEquals(2, vm.state.value.parsed.size)
        assertEquals(4500L, vm.state.value.parsed[0].amount)
    }

    @Test
    fun `密码先输错回到输入态、再输对仍能导入`() {
        openZip()

        vm.submitZipPassword("000000")
        settle { vm.state.value.zipPasswordWrong }
        assertEquals("密码错应回到输入密码态内联报错", ImportStage.PASSWORD, vm.state.value.stage)
        assertTrue(vm.state.value.zipPasswordWrong)

        vm.submitZipPassword(PASSWORD)
        settle { vm.state.value.stage == ImportStage.PREVIEW || vm.state.value.stage == ImportStage.ERROR }

        assertEquals(ImportStage.PREVIEW, vm.state.value.stage)
        assertEquals(2, vm.state.value.parsed.size)
    }

    private companion object {
        const val PASSWORD = "123456"

        /**
         * 传统 ZipCrypto 加密的 zip（DEFLATE + bit3 数据描述符，单 CSV 条目 `alipay_records.csv`，
         * 密码 123456），结构与支付宝「记账本明细」导出包一致；条目内容是合成样例数据。
         * 夹具由 Python 写出的 ZipCrypto 字节流生成，并已用 CPython 标准库 `zipfile` 反向读取校验
         * （正确密码可读、错密码报 Bad password）。此处按 88 字符换行拼接，避免超长行。
         */
        private const val ENCRYPTED_APP_HEADER_B64 =
            "UEsDBBQACQAIAMBjKV0AAAAAAAAAAAAAAAASAAAAYWxpcGF5X3JlY29yZHMuY3N2oJqlGLKF9qHRYTc051X1mYgY" +
            "jZcup42nRBULCphcOTc+eNK9BkJ4Mg1wjCJDgQMUT70sl4LIuRxw3n5jdw9tkhgs1mPtpE2lbBC2+v9ug6GUcYVD" +
            "s0RqJMPKwcpYuoqCQTQA4yPp3wnPyWNxGKKGM9l5vc5hdN+j/8OYq4Uq7HLida3Wt0Lf+qisph/8AxlIoVoxdSeW" +
            "JoxRJGtf5eQE9FuVL3+r1Ei138P2/eiO/OayeZ9E4Mm+WxHdkiWq9xjYJFNn6E2Iv3OIjUN/9in+07r3YXDO5dcG" +
            "dJu4j8PClT6Fv/G2Kj90g1BLBwhvygra6AAAABUBAABQSwECFAAUAAkACADAYyldb8oK2ugAAAAVAQAAEgAAAAAA" +
            "AAAAAAAAAAAAAAAAYWxpcGF5X3JlY29yZHMuY3N2UEsFBgAAAAABAAEAQAAAACgBAAAAAA=="
    }
}
