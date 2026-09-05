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
 * importFromUri transactionId 去重回归：
 * - 文件内重复交易单号只导入首条
 * - 与库内已有交易单号重复的跳过
 * - 无交易单号行不参与去重（重复导入会累加，符合现有语义）
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

        val count = exporter.importFromUri(uri).getOrThrow()

        assertEquals(3, count)
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
        assertEquals(2, first)

        // ShadowContentResolver 的 InputStream 每次 open 都复用同一实例，已关闭需重新注册
        registerInputStream(uri, zip)
        val second = exporter.importFromUri(uri).getOrThrow()
        assertEquals(0, second)

        assertEquals(2, db.billDao().getAllBills().first().size)
    }

    @Test
    fun `rows without transactionId are not deduplicated`() = runBlocking {
        val zip = billZip("c", "TX-B", "")
        registerInputStream(uri, zip)
        val first = exporter.importFromUri(uri).getOrThrow()

        registerInputStream(uri, zip)
        val second = exporter.importFromUri(uri).getOrThrow()

        // 首条仍去重 TX，但无单号行每次导入都累加
        assertEquals(2, first)
        assertEquals(1, second)
        assertEquals(3, db.billDao().getAllBills().first().size)
    }

    // ── helpers ──

    private val header = "实体类型,金额,类型,日期,交易单号"

    /** 生成含 N 条单据行的 CSV 文本（含 BOM）。 */
    private fun billCsv(vararg txIds: String): String {
        val rows = txIds.joinToString("\n") { tx -> "账单,10.00,EXPENSE,2026-01-01 08:00:00,$tx" }
        return listOf(header, rows).joinToString("\n")
    }

    /** 生成 ZIP，内含名为 记账.csv 的账单 CSV。 */
    private fun billZip(fileName: String, vararg txIds: String): File {
        val out = tempFolder.newFile(fileName)
        ZipOutputStream(out.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("记账.csv"))
            zos.write(("\uFEFF" + billCsv(*txIds)).toByteArray(Charsets.UTF_8))
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
