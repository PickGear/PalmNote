package com.palmnote.data.export

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.data.db.entity.Anniversary
import com.palmnote.data.db.entity.Asset
import com.palmnote.data.db.entity.Bill
import com.palmnote.data.db.entity.Budget
import com.palmnote.data.db.entity.CategoryConfig
import com.palmnote.data.db.entity.CategoryMapping
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.data.db.entity.Moment
import com.palmnote.data.db.entity.Wallet
import com.palmnote.domain.model.BillType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream

/**
 * 交换包**全链路往返演练**：造一套覆盖全部组的合成数据 → 导出 → 空库导入 → 逐字段比对。
 *
 * 这是导出通道的「恢复演练」——表头映射、图片回填、ID 改派、钱包余额守恒、账本落位、
 * manifest/格式说明，任何一环断了都会在这里当场抓住。分段测试（判重/业务键/迁移兼容）
 * 各自钉住局部，这里钉的是整条链。
 *
 * 数据全部为合成值，不含真实用户数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ExchangeRoundTripTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase

    private fun preferences(): PreferencesManager = mockk(relaxed = true) {
        every { themeMode } returns flowOf("SYSTEM")
        every { defaultBillType } returns flowOf("EXPENSE")
        every { budgetReminderEnabled } returns flowOf(true)
    }

    private fun exporterFor(database: AppDatabase) =
        CsvDataExporter(context, database, preferences())

    private fun ts(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seed() {
        runBlocking {
            db.accountBookDao().insertBook(AccountBook(id = 1, name = "默认账本", isDefault = true))
            val w1 = db.walletDao().insert(
                Wallet(name = "现金", isDefault = true, initialBalance = 100_000, currentBalance = 88_000)
            )
            val w2 = db.walletDao().insert(
                Wallet(name = "微信", initialBalance = 50_000, currentBalance = 57_000)
            )
            // 余额守恒：w1 = 100000 - 10000 - 2000 = 88000；w2 = 50000 + 5000 + 2000(转入) = 57000
            db.billDao().insertBill(
                Bill(
                    amount = 10_000, type = BillType.EXPENSE, category = "餐饮", note = "午餐",
                    date = ts(2026, 10, 28), yearMonth = "2026-10", accountBookId = 1, walletId = w1,
                    createdAt = ts(2026, 10, 28), updatedAt = ts(2026, 10, 28)
                )
            )
            db.billDao().insertBill(
                Bill(
                    amount = 5_000, type = BillType.INCOME, category = "兼职",
                    date = ts(2026, 10, 29), yearMonth = "2026-10", accountBookId = 1, walletId = w2,
                    createdAt = ts(2026, 10, 29), updatedAt = ts(2026, 10, 29)
                )
            )
            db.billDao().insertBill(
                Bill(
                    amount = 2_000, type = BillType.TRANSFER, category = "转账",
                    date = ts(2026, 10, 30), yearMonth = "2026-10", accountBookId = 1,
                    walletId = w1, toWalletId = w2,
                    createdAt = ts(2026, 10, 30), updatedAt = ts(2026, 10, 30)
                )
            )
            db.budgetDao().insertBudget(Budget(yearMonth = "2026-10", totalBudget = 300_000))
            db.categoryConfigDao().insert(CategoryConfig(type = "BILL_EXPENSE", name = "餐饮"))
            db.categoryMappingDao().insertMapping(CategoryMapping(assetCategory = "数码", billCategory = "电子"))

            val imgDir = File(context.filesDir, "images").apply { mkdirs() }
            val img = File(imgDir, "rt_test.jpg").apply { writeBytes(ByteArray(64) { 7 }) }
            // Windows 下 absolutePath 含反斜杠，手拼 JSON 会造出非法转义——按 JSON 规则转义
            val escapedPath = img.absolutePath.replace("\\", "\\\\")
            db.assetDao().insertAsset(
                Asset(
                    name = "相机", category = "数码", purchasePrice = 100_000,
                    images = """["$escapedPath"]"""
                )
            )

            db.anniversaryDao().insertAnniversary(Anniversary(title = "结婚纪念日", solarDate = ts(2027, 5, 20)))
            db.momentDao().insertMoment(Moment(content = "今天天气不错", mood = "开心"))
            val lt = db.lifeTemplateDao().insertTemplate(
                LifeTemplate(
                    name = "打卡", category = "记录", icon = "calendar_month", color = "#3F51B5",
                    fieldsConfig = "[]", layoutType = "card",
                    availableLayouts = """["card","list"]""", statusFlowConfig = "{}",
                    linkConfig = "{}", reminderConfig = "{}"
                )
            )
            db.lifeItemDao().insertItem(LifeItem(templateId = lt, title = "今天打卡"))
        }
    }

    private suspend fun exportAll(): ByteArray {
        val exportUri = Uri.parse("content://test/roundtrip.zip")
        val exported = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(exportUri, exported)
        exporterFor(db).exportToUri(exportUri).getOrThrow()
        return exported.toByteArray()
    }

    private fun entryNamesOf(bytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                names.add(e.name)
                e = zis.nextEntry
            }
        }
        return names
    }

    private fun freshDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private suspend fun importAll(fresh: AppDatabase, exported: ByteArray): ImportReport {
        val importUri = Uri.parse("content://test/roundtrip-in.zip")
        shadowOf(context.contentResolver).registerInputStream(
            importUri, ByteArrayInputStream(exported)
        )
        return exporterFor(fresh).importFromUri(importUri).getOrThrow()
    }

    private fun assertPackageEntries(bytes: ByteArray) {
        val entryNames = entryNamesOf(bytes)
        assertTrue("清单应随包", entryNames.contains("manifest.json"))
        assertTrue("格式说明应随包", entryNames.contains("格式说明.txt"))
    }

    private suspend fun assertWallets(fresh: AppDatabase) {
        val wallets = fresh.walletDao().getAllWallets().first().sortedBy { it.id }
        assertEquals(2, wallets.size)
        val cash = wallets.first { it.name == "现金" }
        assertEquals(100_000, cash.initialBalance)
        assertEquals("余额应守恒", 88_000, cash.currentBalance)
        assertTrue(cash.isDefault)
        val wechat = wallets.first { it.name == "微信" }
        assertEquals("余额应守恒", 57_000, wechat.currentBalance)
    }

    private suspend fun assertBills(fresh: AppDatabase, cashId: Long, wechatId: Long) {
        val bills = fresh.billDao().getAllBills().first().sortedBy { it.id }
        assertEquals(3, bills.size)
        val expense = bills.first { it.type == BillType.EXPENSE }
        assertEquals(10_000, expense.amount)
        assertEquals("餐饮", expense.category)
        assertEquals("午餐", expense.note)
        assertEquals(ts(2026, 10, 28), expense.date)
        assertEquals("2026-10", expense.yearMonth)
        assertEquals("账本列在本机不存在时应回落默认账本", 1, expense.accountBookId)
        assertEquals(cashId, expense.walletId)
        val income = bills.first { it.type == BillType.INCOME }
        assertEquals(5_000, income.amount)
        assertEquals(wechatId, income.walletId)
        val transfer = bills.first { it.type == BillType.TRANSFER }
        assertEquals(2_000, transfer.amount)
        assertEquals(cashId, transfer.walletId)
        assertEquals(wechatId, transfer.toWalletId)
    }

    private suspend fun assertBudgetAndCategories(fresh: AppDatabase) {
        val budgets = fresh.budgetDao().getAllBudgets().first()
        assertEquals(1, budgets.size)
        assertEquals("2026-10", budgets[0].yearMonth)
        assertEquals(300_000, budgets[0].totalBudget)
        assertEquals(1, fresh.categoryConfigDao().getAllCategories().first().size)
        assertEquals(1, fresh.categoryMappingDao().getAllMappings().first().size)
    }

    private suspend fun assertAssetImages(fresh: AppDatabase) {
        val assets = fresh.assetDao().getAllAssets().first()
        assertEquals(1, assets.size)
        assertEquals("相机", assets[0].name)
        assertEquals(100_000, assets[0].purchasePrice)
        val imgJson = assets[0].images
        assertTrue("图片引用不应为空", imgJson.isNotBlank())
        val localImg = File(imgJson.removeSurrounding("[\"", "\"]"))
        assertTrue("回填的图片应真实存在", localImg.exists())
    }

    private suspend fun assertLife(fresh: AppDatabase) {
        assertEquals(1, fresh.anniversaryDao().getAllAnniversaries().first().size)
        assertEquals(1, fresh.momentDao().getAllMoments().first().size)
        val templates = fresh.lifeTemplateDao().getAllTemplates().first()
        assertEquals(1, templates.size)
        assertEquals("打卡", templates[0].name)
        val items = fresh.lifeItemDao().getAllItems().first()
        assertEquals(1, items.size)
        assertEquals("今天打卡", items[0].title)
        assertEquals(templates[0].id, items[0].templateId)
    }

    @Test
    fun `全套数据导出再导入空库后逐字段一致`() = runBlocking {
        seed()
        val exported = exportAll()
        assertPackageEntries(exported)

        val fresh = freshDb()
        try {
            val report = importAll(fresh, exported)
            assertTrue("合成数据应全部导入", report.inserted > 0)

            assertWallets(fresh)
            val wallets = fresh.walletDao().getAllWallets().first().sortedBy { it.id }
            assertBills(fresh, cashId = wallets[0].id, wechatId = wallets[1].id)
            assertBudgetAndCategories(fresh)
            assertAssetImages(fresh)
            assertLife(fresh)

            // 判重演练：同一包再导入一次应全部跳过、零重复
            val second = importAll(fresh, exported)
            assertEquals("重复导入应全部跳过", 0, second.inserted)
        } finally {
            fresh.close()
        }
    }
}
