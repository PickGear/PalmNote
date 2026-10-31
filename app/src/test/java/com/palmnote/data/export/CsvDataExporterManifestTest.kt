package com.palmnote.data.export

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 交换包清单（manifest.json）的构建与读取：
 * 导出侧写 formatVersion/计数，导入侧据此协商版本——「过新明确报错、无清单尽力导入」。
 * 这两个行为是格式演进的地基：清单错一次，以后每个版本的包都会被错误地接受或拒绝。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class CsvDataExporterManifestTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var exporter: CsvDataExporter

    @Before
    fun setup() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exporter = CsvDataExporter(context, db, mockk<PreferencesManager>(relaxed = true))
    }

    @Test
    fun `清单含当前格式版本与计数`() {
        val manifest = exporter.buildManifestJson(totalRecords = 41, imageCount = 14)
        val obj = Json.parseToJsonElement(manifest).jsonObject
        assertEquals(CsvDataExporter.FORMAT_VERSION, obj["formatVersion"]?.jsonPrimitive?.intOrNull)
        assertEquals(41, obj["totalRecords"]?.jsonPrimitive?.intOrNull)
        assertEquals(14, obj["images"]?.jsonPrimitive?.intOrNull)
        assertEquals(1, CsvDataExporter.FORMAT_VERSION)
    }

    @Test
    fun `清单可被读取侧解析出版本`() {
        val bytes = exporter.buildManifestJson(3, 0).toByteArray()
        assertEquals(CsvDataExporter.FORMAT_VERSION, exporter.manifestFormatVersion(bytes))
    }

    @Test
    fun `无清单返回 null 按当前格式尽力导入`() {
        assertNull(exporter.manifestFormatVersion(null))
    }

    @Test
    fun `损坏的清单不抛错 返回 null 尽力导入`() {
        assertNull(exporter.manifestFormatVersion("不是json".toByteArray()))
        assertNull(exporter.manifestFormatVersion(byteArrayOf(0x00, 0x01)))
    }
}
