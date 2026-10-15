package com.palmnote.data.export

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.util.BuiltinGoalTemplate
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 旧包（≤ 1.4.1）「生活.csv」里的目标行 → 目标模板生活条目的导入适配。
 *
 * 旧包行首列是实体类型：`目标` / `打卡记录`。v1.4.2 起：
 * - 目标行**行级改写**成目标模板（kind = GOAL）下的生活记录，之后走与新版完全相同的
 *   判重 / id 冲突改派 / 写库（[CsvDataExporter.legacyGoalRowToItemRow]）；
 * - 打卡记录行整行丢弃并计数（[ImportReport.legacyCheckInsDropped]）；
 * - 习惯型目标不转 —— 旧导出把 `HABIT` 译成了中文「习惯」，两种写法都要认。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class CsvDataExporterLegacyGoalImportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var exporter: CsvDataExporter
    private lateinit var uri: Uri
    private var goalTemplateId: Long = 0

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exporter = CsvDataExporter(context, db, mockk<PreferencesManager>(relaxed = true))
        uri = Uri.parse("content://test/legacy-goal.zip")
        goalTemplateId = runBlocking {
            db.lifeTemplateDao().insertTemplate(
                LifeTemplate(
                    name = "目标", category = "计划", icon = "flag", color = "#E44444",
                    description = "", fieldsConfig = BuiltinGoalTemplate.FIELDS_CONFIG,
                    layoutType = "card", availableLayouts = BuiltinGoalTemplate.AVAILABLE_LAYOUTS,
                    statusFlowConfig = BuiltinGoalTemplate.STATUS_FLOW,
                    linkConfig = BuiltinGoalTemplate.LINK_CONFIG,
                    isBuiltin = true, isHidden = false, isSpecial = false, sortOrder = 19,
                    kind = "GOAL"
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `legacy goal rows convert to goal-template items while habits and check-ins are dropped`() =
        runBlocking {
            registerInputStream(uri, legacyGoalZip("a"))
            val report = exporter.importFromUri(uri).getOrThrow()

            // 累计进行中 + 累计已完成 + 递减型 = 3 条；习惯行/空标题行静默丢弃；打卡行丢弃并计数
            assertEquals(3, report.inserted)
            assertEquals(1, report.legacyCheckInsDropped)

            val items = db.lifeItemDao().getAllItemsIncludingHidden().first()
            assertEquals(3, items.size)
            assertTrue(items.all { it.templateId == goalTemplateId })

            val reading = items.first { it.title == "今年读完 12 本书" }
            val fields = Json.parseToJsonElement(reading.fieldsData).jsonObject
            assertEquals("12", fields.getValue("target").jsonPrimitive.content)
            assertEquals("7", fields.getValue("current").jsonPrimitive.content)
            assertEquals("本", fields.getValue("unitText").jsonPrimitive.content)
            val deadline = fields.getValue("deadline").jsonPrimitive.content.toLong()
            assertEquals(parseOldDate("2026-12-31 00:00:00"), deadline)
            assertEquals("ACTIVE", reading.status)

            assertEquals("COMPLETED", items.first { it.title == "背完单词" }.status)

            // 递减型：起始值 20 → 目标 0，当前 15（未达成），initial 必须搬过去
            val quit = items.first { it.title == "戒烟" }
            val quitFields = Json.parseToJsonElement(quit.fieldsData).jsonObject
            assertEquals("0", quitFields.getValue("target").jsonPrimitive.content)
            assertEquals("20", quitFields.getValue("initial").jsonPrimitive.content)
            assertEquals("ACTIVE", quit.status)

            assertFalse(items.any { it.title == "每日喝水" }) // 旧导出译成「习惯」，不转
        }

    @Test
    fun `reimporting the same legacy package is idempotent`() = runBlocking {
        val zip = legacyGoalZip("b")
        registerInputStream(uri, zip)
        exporter.importFromUri(uri).getOrThrow()

        // ShadowContentResolver 的 InputStream 每次 open 都复用同一实例，已关闭需重新注册
        registerInputStream(uri, zip)
        val second = exporter.importFromUri(uri).getOrThrow()

        assertEquals(0, second.inserted)
        assertEquals(3, db.lifeItemDao().getAllItemsIncludingHidden().first().size)
    }

    @Test
    fun `converted rows keep the local template id even when it collides with a package template id`() =
        runBlocking {
            // 包内生活模板的编号 = 本机目标模板 id：模板改派新 id 后，
            // 转换行的 templateId 是**本机** id，不许被收尾流程拿包内 id 表错改挂
            registerInputStream(uri, legacyGoalZip("c", packageTemplateId = goalTemplateId, goalRows = listOf(readingRow)))
            exporter.importFromUri(uri).getOrThrow()

            val items = db.lifeItemDao().getAllItemsIncludingHidden().first()
            assertEquals(listOf("今年读完 12 本书"), items.map { it.title })
            assertEquals(goalTemplateId, items.single().templateId)
            // 包内那张模板确实被改派了新 id —— 上面的断言才不是「什么都没发生」
            val templates = db.lifeTemplateDao().getAllTemplates().first()
            val imported = templates.first { it.name == "读书笔记" }
            assertTrue(imported.id != goalTemplateId)
        }

    // ── helpers ──

    /** 并集表头（复刻旧导出：一个文件多实体，首行是全部字段的并集，行按列位对齐）。 */
    private val goalHeader = "实体类型,编号,名称,标题,目标类型,分类,图标,颜色,描述," +
        "目标次数,当前次数,单位,截止日期,开始日期,优先级,direction,initialValue,创建时间,更新时间"

    /** 累计进行中（旧导出把 goalType 译成中文：累计/习惯/目标）。 */
    private val readingRow =
        "目标,51,,今年读完 12 本书,累计,阅读,,,,12,7,本,2026-12-31 00:00:00,2026-01-01 00:00:00,中," +
            "INCREASE,,2026-01-05 10:00:00,2026-02-01 09:00:00"

    /** 全套夹具：进行中 / 已完成 / 习惯（丢弃）/ 打卡记录（丢弃计数）/ 递减型 / 缺标题（丢弃）。 */
    private fun legacyGoalRows() = listOf(
        readingRow,
        // 累计已完成 → status = COMPLETED
        "目标,52,,背完单词,目标,技能,,,,30,30,词,,,中,INCREASE,,2026-01-06 10:00:00,2026-01-07 10:00:00",
        // 习惯型（译成「习惯」）→ 丢弃，不进也不计数
        "目标,53,,每日喝水,习惯,健康,,,,8,6,杯,,,低,INCREASE,,2026-01-08 10:00:00,2026-01-08 10:00:00",
        // 打卡记录 → 丢弃并计数（后续列与打卡无关，短行即可）
        "打卡记录,101,今天很顺",
        // 递减型：direction/initialValue 是**裸英文列头**（旧导出没译过这两个字段）
        "目标,54,,戒烟,累计,健康,,,,0,15,支,,,中,DECREASE,20,2026-02-01 10:00:00,2026-02-02 10:00:00",
        // 缺标题（第 4 列为空）→ 静默丢弃
        "目标,55,,,累计,健康,,,,10,3,次,,,低,INCREASE,,2026-03-01 10:00:00,2026-03-01 10:00:00"
    )

    private fun legacyGoalCsv(packageTemplateId: Long? = null, goalRows: List<String> = legacyGoalRows()): String {
        // 生活模板行：名称在「名称」列（并集表头下按列位对齐），后面的列短行缺省为空。
        // 位置必须**排在目标行之后**——复刻旧导出的组序（目标行恒在生活模板行之前），
        // 转换行先处理才不会被模板行改派出的包内 id 映射波及
        val templateRow = packageTemplateId?.let { "生活模板,$it,读书笔记,,计划,menu_book,#26A69A" }
        return listOfNotNull(goalHeader).plus(goalRows).plus(listOfNotNull(templateRow)).joinToString("\n")
    }

    /** 生成 ZIP，内含名为 生活.csv 的旧包 CSV（复刻旧导出的组名即文件名契约）。 */
    private fun legacyGoalZip(
        fileName: String,
        packageTemplateId: Long? = null,
        goalRows: List<String> = legacyGoalRows()
    ): File {
        val out = tempFolder.newFile("$fileName.zip")
        ZipOutputStream(out.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("生活.csv"))
            zos.write(("\uFEFF" + legacyGoalCsv(packageTemplateId, goalRows)).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return out
    }

    private fun registerInputStream(targetUri: Uri, file: File) {
        shadowOf(InstrumentationRegistry.getInstrumentation().targetContext.contentResolver)
            .registerInputStream(targetUri, FileInputStream(file))
    }

    /** 旧导出的日期格式（与 CsvDataExporter.dateFormatter 同款）→ 毫秒。 */
    private fun parseOldDate(value: String): Long =
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
