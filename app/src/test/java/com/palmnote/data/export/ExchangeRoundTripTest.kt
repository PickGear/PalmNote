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
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetScope
import com.palmnote.domain.model.DemoOrigin
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        every { budgetNotifyEnabled } returns flowOf(true)
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
            db.budgetDao().insertBudget(Budget.total(BudgetPeriod.MONTH, 300_000))
            seedCategoryBudget()
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

    /** 分类预算：维度列（period/scope/category/阈值/通知）都要能原样往返。 */
    private fun seedCategoryBudget() = runBlocking {
        db.budgetDao().insertBudget(
            Budget(
                period = BudgetPeriod.WEEK.name,
                scope = BudgetScope.CATEGORY.name,
                category = "餐饮",
                totalBudget = 80_000,
                reminderThreshold = 0.9,
                reminderEnabled = false
            )
        )
    }

    private suspend fun assertBudgetAndCategories(fresh: AppDatabase) {
        val budgets = fresh.budgetDao().getAllBudgets().first()
        assertEquals(2, budgets.size)
        val total = budgets.first { it.scope == BudgetScope.TOTAL.name }
        assertEquals(BudgetPeriod.MONTH.name, total.period)
        assertEquals(300_000, total.totalBudget)
        // 分类预算：分类名、阈值、通知开关逐字段一致（周周期一并验证）
        val category = budgets.first { it.scope == BudgetScope.CATEGORY.name }
        assertEquals(BudgetPeriod.WEEK.name, category.period)
        assertEquals("餐饮", category.category)
        assertEquals(80_000, category.totalBudget)
        assertEquals(0.9, category.reminderThreshold, 0.0001)
        assertEquals(false, category.reminderEnabled)
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

    @Test
    fun `信用卡钱包类型与欠款余额导出再导入后保真`() = runBlocking {
        db.accountBookDao().insertBook(AccountBook(id = 1, name = "默认账本", isDefault = true))
        // 信用卡钱包：余额字段存「欠款」（正数 = 欠钱，issue #51）。
        // currentBalance 必须守恒（initial − Σ普通方向 = 300000 − (−30000) = 330000），
        // 否则导入重算会按不变量把它改回去
        val credit = db.walletDao().insert(
            Wallet(
                name = "信用卡", type = Wallet.TYPE_CREDIT_CARD,
                initialBalance = 300_000, currentBalance = 330_000
            )
        )
        db.billDao().insertBill(
            Bill(
                amount = 30_000, type = BillType.EXPENSE, category = "餐饮", note = "刷卡",
                date = ts(2026, 10, 28), yearMonth = "2026-10", accountBookId = 1, walletId = credit,
                createdAt = ts(2026, 10, 28), updatedAt = ts(2026, 10, 28)
            )
        )

        val exported = exportAll()
        val fresh = freshDb()
        try {
            val report = importAll(fresh, exported)
            assertEquals(2, report.inserted)

            val wallet = fresh.walletDao().getAllWallets().first().single()
            assertEquals(Wallet.TYPE_CREDIT_CARD, wallet.type)
            // 欠款余额往返后由重算复现同一数字（方向翻转口径下）
            assertEquals(300_000, wallet.initialBalance)
            assertEquals(330_000, wallet.currentBalance)
        } finally {
            fresh.close()
        }
    }

    private fun firstCsvText(bytes: ByteArray): String {
        ZipInputStream(bytes.inputStream()).use { zis ->
            var current = zis.nextEntry
            while (current != null) {
                val entry = current
                if (entry.name.endsWith(".csv")) return String(zis.readBytes(), Charsets.UTF_8)
                current = zis.nextEntry
            }
        }
        return ""
    }

    /** 单个条目换回旧表头；非 CSV 原样返回。 */
    private fun legacyCsvEntry(name: String, data: ByteArray): ByteArray =
        if (!name.endsWith(".csv")) {
            data
        } else {
            String(data, Charsets.UTF_8)
                .replace("预算分类", "分类")
                .replace("预算通知", "提醒")
                .toByteArray(Charsets.UTF_8)
        }

    /** 把表头改回 v20 及以前的叫法，模拟旧包（新版本来就已是这套列名，替换是空操作）。 */
    private fun withLegacyHeaders(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipInputStream(bytes.inputStream()).use { zis ->
            ZipOutputStream(out).use { zos ->
                var current = zis.nextEntry
                while (current != null) {
                    // 不可变局部 val：current 被内层 lambda 捕获并重新赋值，不能智能转换
                    val entry = current
                    zos.putNextEntry(ZipEntry(entry.name))
                    zos.write(legacyCsvEntry(entry.name, zis.readBytes()))
                    zos.closeEntry()
                    current = zis.nextEntry
                }
            }
        }
        return out.toByteArray()
    }

    /** v21 迁移后真实与演示各留一条 MONTH/TOTAL，演示行混进包会撞判重键顶掉真实预算。 */
    @Test
    fun `演示预算不进交换包`() = runBlocking {
        db.budgetDao().insertBudget(Budget.total(BudgetPeriod.MONTH, 300_000))
        db.budgetDao().insertBudget(
            Budget.total(BudgetPeriod.MONTH, 500_000, demoOrigin = DemoOrigin.SEED)
        )
        val fresh = freshDb()
        try {
            importAll(fresh, exportAll())
            val budgets = fresh.budgetDao().getAllBudgets().first()
            assertEquals(1, budgets.size)
            assertEquals(300_000, budgets.single().totalBudget)
            assertEquals(DemoOrigin.NONE, budgets.single().demoOrigin)
        } finally {
            fresh.close()
        }
    }

    /** 表头是六实体共用列，预算不能把「分类」「提醒」挤掉，否则旧包导入丢字段。 */
    @Test
    fun `交换包表头保持分类与提醒且旧包导入不丢分类`() = runBlocking {
        seed()
        val exported = exportAll()
        val headers = firstCsvText(exported).lineSequence().first()
        assertTrue("分类列应沿用旧名", headers.contains("分类"))
        assertFalse("预算不得占用共用列名", headers.contains("预算分类"))

        val fresh = freshDb()
        try {
            importAll(fresh, withLegacyHeaders(exported))
            val bills = fresh.billDao().getAllBills().first()
            assertTrue("旧包「分类」表头应能反查到 category", bills.any { it.category == "餐饮" })
        } finally {
            fresh.close()
        }
    }

    /** 导入只能改派本次写入的预算，不能扫全表删本机既有（尤其隐藏账本的）预算。 */
    @Test
    fun `导入不删除本机既有的账本预算`() = runBlocking {
        seed()
        val exported = exportAll()
        val fresh = freshDb()
        try {
            fresh.accountBookDao().insertBook(
                AccountBook(id = 7, name = "隐藏账本", isHidden = true)
            )
            fresh.budgetDao().insertBudget(
                Budget(
                    period = BudgetPeriod.MONTH.name,
                    scope = BudgetScope.BOOK.name,
                    bookId = 7,
                    totalBudget = 42_000
                )
            )
            importAll(fresh, exported)
            val kept = fresh.budgetDao().getAllBudgets().first()
                .filter { it.scope == BudgetScope.BOOK.name }
            assertEquals(1, kept.size)
            assertEquals(7L, kept.single().bookId)
            assertEquals(42_000, kept.single().totalBudget)
        } finally {
            fresh.close()
        }
    }
}
