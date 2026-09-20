package com.palmnote.data.export

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.Bill
import com.palmnote.domain.model.BillType
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 导入判重与账实相符的回归。
 *
 * 判重钥匙是**业务键**（见 [ImportKeys]），不是自增 id：
 * - 文件内重复交易单号只导入首条
 * - 与库内已有交易单号重复的跳过
 * - 无单号行按字段指纹判重，因此同一个包再导一次也不会累加
 * - 重复导入不覆盖库里的新数据，钱包余额始终等于「初始余额 + Σ账单净额」
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class CsvDataExporterDedupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var exporter: CsvDataExporter
    private lateinit var uri: Uri

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exporter = CsvDataExporter(context, db, mockk<PreferencesManager>(relaxed = true))
        uri = Uri.parse("content://test/import.zip")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `duplicate transactionId within file and against existing bill is skipped`() = runBlocking {
        db.billDao().insertBill(billWithTx("TX-ALREADY"))

        registerInputStream(uri, billZip("a.zip", "TX-DUP", "TX-DUP", "TX-NEW", "TX-ALREADY", ""))

        val report = exporter.importFromUri(uri).getOrThrow()

        assertEquals(3, report.inserted)
        assertEquals(2, report.skipped) // 文件内重复的 TX-DUP + 与库内重复的 TX-ALREADY
        val all = db.billDao().getAllBills().first()
        assertEquals(1, all.count { it.transactionId == "TX-DUP" })
        assertEquals(1, all.count { it.transactionId == "TX-NEW" })
        assertEquals(1, all.count { it.transactionId == "TX-ALREADY" })
        assertEquals(4, all.size)
    }

    @Test
    fun `reimport of same file is idempotent for tx rows`() = runBlocking {
        val zip = billZip("b.zip", "TX-DUP", "TX-DUP", "TX-NEW2")
        registerInputStream(uri, zip)
        val first = exporter.importFromUri(uri).getOrThrow()
        assertEquals(2, first.inserted)

        // ShadowContentResolver 的 InputStream 每次 open 都复用同一实例，已关闭需重新注册
        registerInputStream(uri, zip)
        val second = exporter.importFromUri(uri).getOrThrow()
        assertEquals(0, second.inserted)

        assertEquals(2, db.billDao().getAllBills().first().size)
    }

    @Test
    fun `rows without transactionId are deduplicated by fingerprint`() = runBlocking {
        val zip = billZip("c", "TX-B", "")
        registerInputStream(uri, zip)
        val first = exporter.importFromUri(uri).getOrThrow()
        assertEquals(2, first.inserted)

        registerInputStream(uri, zip)
        val second = exporter.importFromUri(uri).getOrThrow()

        // 无单号行在第二次导入时也被字段指纹拦下 —— 重复导入自己的包不再让账目翻倍
        assertEquals(0, second.inserted)
        assertEquals(2, second.skipped)
        assertEquals(2, db.billDao().getAllBills().first().size)
    }

    @Test
    fun `balance is recomputed from bills after a merged import`() = runBlocking {
        registerInputStream(uri, walletBillZip("d"))
        val report = exporter.importFromUri(uri).getOrThrow()

        assertEquals(2, report.inserted) // 1 个钱包 + 1 笔账单
        assertEquals(1, report.walletsRecalculated)

        val wallet = db.walletDao().getAllWallets().first().single()
        // 支出 10.00、初始余额 0 → 余额必须跟着账单走（余额 == 初始余额 + Σ账单净额）
        assertEquals(-1000L, wallet.currentBalance)
    }

    @Test
    fun `reimporting an older package does not roll back newer local data`() = runBlocking {
        registerInputStream(uri, walletBillZip("e"))
        exporter.importFromUri(uri).getOrThrow()
        val walletId = db.walletDao().getAllWallets().first().single().id

        // 模拟「导包之后本机又记了一笔」：走应用自己的路径，余额一并平移
        db.billDao().insertBill(billWithTx("TX-LOCAL").copy(amount = 500L, walletId = walletId))
        db.walletDao().adjustBalance(walletId, -500L)
        assertEquals(-1500L, db.walletDao().getAllWallets().first().single().currentBalance)

        // 再导入那份「几周前的自己包」：钱包与账单都已存在 → 一律跳过，不许回滚
        registerInputStream(uri, walletBillZip("f"))
        val report = exporter.importFromUri(uri).getOrThrow()

        assertEquals(0, report.inserted)
        assertEquals(2, report.skipped)
        assertEquals(-1500L, db.walletDao().getAllWallets().first().single().currentBalance)
        assertEquals(2, db.billDao().getAllBills().first().size)
    }

    // ── helpers ──

    private val header = "实体类型,金额,类型,日期,交易单号"

    /** 生成含 N 条单据行的 CSV 文本（含 BOM）。 */
    private fun billCsv(vararg txIds: String): String {
        val rows = txIds.joinToString("\n") { tx -> "账单,10.00,EXPENSE,2026-01-01 08:00:00,$tx" }
        return listOf(header, rows).joinToString("\n")
    }

    /** 生成 ZIP，内含名为 记账.csv 的账单 CSV。 */
    private fun billZip(fileName: String, vararg txIds: String): File = zipOf(fileName, billCsv(*txIds))

    /**
     * 生成含 1 个钱包 + 1 笔账单的 ZIP。
     *
     * 账单行排在钱包行前面，刻意复刻真实导出的顺序（导出侧 Bill 在 Wallet 之前），
     * 这样「账单引用了同一个文件里稍后才出现的钱包」这条路径也在测试覆盖内。
     */
    private fun walletBillZip(fileName: String): File = zipOf(fileName, walletBillCsv())

    /**
     * 带 `编号`（id）列——真实导出一定会带它。
     *
     * 这一点必须复刻：旧实现正是因为「照着包里的 id 用 REPLACE 写库」才会覆盖本地记录。
     * 如果夹具省掉 id 列，两条实现都变成「插新行」，旧 bug 就复现不出来了。
     */
    private fun walletBillCsv(): String {
        val header = "实体类型,编号,名称,类型,金额,日期,钱包,初始余额,当前余额,交易单号"
        val billRow = "账单,1,,EXPENSE,10.00,2026-01-01 08:00:00,1,,,TX-W1"
        val walletRow = "钱包,1,现金,CASH,,,,0.00,0.00,"
        return listOf(header, billRow, walletRow).joinToString("\n")
    }

    private fun zipOf(fileName: String, csv: String): File {
        val out = tempFolder.newFile(fileName)
        ZipOutputStream(out.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("记账.csv"))
            zos.write(("\uFEFF" + csv).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return out
    }

    private fun registerInputStream(targetUri: Uri, file: File) {
        shadowOf(InstrumentationRegistry.getInstrumentation().targetContext.contentResolver)
            .registerInputStream(targetUri, FileInputStream(file))
    }

    private fun billWithTx(txId: String) = Bill(
        amount = 100L,
        type = BillType.EXPENSE,
        category = "餐饮",
        date = 1767225600000L, // 2026-01-01
        yearMonth = "2026-01",
        transactionId = txId
    )
}
