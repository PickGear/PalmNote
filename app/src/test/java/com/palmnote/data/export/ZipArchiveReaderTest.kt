package com.palmnote.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * [ZipArchiveReader] 的端到端测试。
 *
 * 夹具并非手写魔法数：由 `.workbuddy/audit/make_zip_fixtures.py` 里的 ZipCrypto 写入器生成，并**已用
 * CPython 标准库 `zipfile` 反向读取校验**（`ZipFile.read(name, pwd=b"123456")` 通过、错密码抛 `RuntimeError`）。
 * 之所以用 Python 生成：ZipCrypto 写入在 JVM 侧无现成实现；若用 Kotlin 自写「写入器 + 读取器」会自证循环，
 * 独立实现（CPython）反向读取才能真正校验字节流是否标准。
 *
 * 三个夹具以 Base64 内嵌，使测试自足、不依赖 test resources 或外部文件：
 *  - encrypted_123456.zip  ZipCrypto（DEFLATE + bit3 数据描述符），密码 "123456"
 *  - plain_deflate.zip     未加密（DEFLATE，method=8）
 *  - plain_store.zip       未加密（STORE，method=0）
 */
class ZipArchiveReaderTest {

    private val reader = ZipArchiveReader()

    private val encryptedZip = decode(ENCRYPTED_123456_B64)
    private val plainDeflateZip = decode(PLAIN_DEFLATE_B64)
    private val plainStoreZip = decode(PLAIN_STORE_B64)

    private val expectedCsv = EXPECTED_CSV_LINES.joinToString("\n") + "\n"
    private val csvName = "alipay_records.csv"

    @Test
    fun `isZip only matches the local file header magic`() {
        assertTrue(reader.isZip(encryptedZip))
        assertTrue(reader.isZip(plainDeflateZip))
        assertFalse(reader.isZip("记录时间,金额\n".toByteArray(Charsets.UTF_8)))
        assertFalse(reader.isZip(ByteArray(0)))
    }

    @Test
    fun `encrypted zip entry metadata is read from the central directory`() {
        val entries = reader.readEntries(encryptedZip)
        assertEquals(1, entries.size)
        val e = entries.single()
        assertEquals(csvName, e.name)
        assertTrue(e.encrypted)
        assertEquals(8, e.method)                          // DEFLATE
        assertTrue(e.flags and 0x8 == 0x8)                 // bit3 数据描述符
        assertEquals(expectedCsv.toByteArray(Charsets.UTF_8).size.toLong(), e.uncompressedSize)
        assertTrue(reader.hasEncryptedEntry(encryptedZip))
        assertFalse(reader.hasEncryptedEntry(plainDeflateZip))
    }

    @Test
    fun `correct password decrypts the encrypted csv`() {
        val entry = reader.readEntries(encryptedZip).single()
        val out = reader.extract(encryptedZip, entry, "123456".toCharArray())
        assertEquals(expectedCsv, String(out, Charsets.UTF_8))
    }

    @Test
    fun `password char array is zeroed after extract`() {
        val entry = reader.readEntries(encryptedZip).single()
        val pwd = "123456".toCharArray()
        reader.extract(encryptedZip, entry, pwd)
        assertEquals("\u0000\u0000\u0000\u0000\u0000\u0000", String(pwd))
    }

    @Test
    fun `wrong password throws WrongPasswordException`() {
        val entry = reader.readEntries(encryptedZip).single()
        assertThrows(WrongPasswordException::class.java) {
            reader.extract(encryptedZip, entry, "000000".toCharArray())
        }
    }

    @Test
    fun `encrypted entry without a password throws WrongPasswordException`() {
        val entry = reader.readEntries(encryptedZip).single()
        assertThrows(WrongPasswordException::class.java) {
            reader.extract(encryptedZip, entry, null)
        }
    }

    @Test
    fun `unencrypted deflate entry extracts with null password`() {
        val entry = reader.readEntries(plainDeflateZip).single()
        assertFalse(entry.encrypted)
        assertEquals(8, entry.method)
        assertEquals(expectedCsv, String(reader.extract(plainDeflateZip, entry, null), Charsets.UTF_8))
    }

    @Test
    fun `unencrypted store entry extracts with null password`() {
        val entry = reader.readEntries(plainStoreZip).single()
        assertFalse(entry.encrypted)
        assertEquals(0, entry.method) // STORE
        assertEquals(expectedCsv, String(reader.extract(plainStoreZip, entry, null), Charsets.UTF_8))
    }

    @Test
    fun `decrypted csv flows through BillCsvImporter end to end`() {
        val entry = reader.readEntries(encryptedZip).single()
        val csv = String(reader.extract(encryptedZip, entry, "123456".toCharArray()), Charsets.UTF_8)
        val lines = csv.lines().map { it.trimStart('\uFEFF').trim() }.filter { it.isNotBlank() }

        val importer = BillCsvImporter()
        assertEquals(BillCsvImporter.CsvFormat.ALIPAY, importer.detectFormat(lines))
        val bills = importer.parseWithFailures(lines, BillCsvImporter.CsvFormat.ALIPAY)

        assertEquals(4, bills.size)
        // 行1：餐饮美食 → 餐饮
        assertEquals(4500L, bills[0].amount)
        assertEquals("餐饮", bills[0].category)
        assertEquals("肯德基", bills[0].merchant)
        assertEquals(true, bills[0].categoryResolved)
        assertEquals(true, bills[0].defaultSelected)
        // 行2：交通出行 → 交通
        assertEquals("交通", bills[1].category)
        // 行3：不计收支 → 默认不勾选；转账 → 其他（明确解析）
        assertEquals("其他", bills[2].category)
        assertEquals(true, bills[2].categoryResolved)
        assertEquals(false, bills[2].defaultSelected)
        assertEquals(100000L, bills[2].amount)
        // 行4：餐饮美食 → 餐饮
        assertEquals("餐饮", bills[3].category)
    }

    @Test
    fun `non zip bytes are rejected as an invalid archive`() {
        assertThrows(IllegalArgumentException::class.java) {
            reader.readEntries("this is definitely not a zip archive".toByteArray(Charsets.UTF_8))
        }
    }

    private fun decode(b64: String): ByteArray = Base64.getDecoder().decode(b64)

    private companion object {
        // 由 make_zip_fixtures.py 生成并 CPython 反向校验；此处按 96 字符换行拼接，避免超长行
        private const val ENCRYPTED_123456_B64 =
            "UEsDBBQACQAIAMBjKV0AAAAAAAAAAAAAAAASAAAAYWxpcGF5X3JlY29yZHMuY3N2oJqlGLKF9qHRYTc0/2R+aA1FJeUwuFBB" +
            "aKhm2QjYq2faNRODfLsCSktz+nUzXf4qaBxU1ffhn4TQA5DIGeKtmrhyHaY+GdFcCHBfwQ84fpUDQdcKzD0MwAjltjUHINxV" +
            "Zj0Cziu/j57zPs8v6TmiD7jbM6HPU5KNkWxhoxTXJ0CoZZnmDhJmoOBQJJFXz2iLH6sXrdjmDc+05n6Px6+lvDhRayyXhltu" +
            "qGoCclplIfbdc+ArhfBpa1fzvBmZfVISsGATyckjvy5ljtinFlKTuHQoeYbenXCKwlZMBqQ1ktIFm/cZn5mwm7MLnaDBSZ5R" +
            "kT1Nt/Ussrhcf70wmaYt6FBLBwh4xdxUAAEAAIYBAABQSwECFAAUAAkACADAYyldeMXcVAABAACGAQAAEgAAAAAAAAAAAAAA" +
            "AAAAAAAAYWxpcGF5X3JlY29yZHMuY3N2UEsFBgAAAAABAAEAQAAAAEABAAAAAA=="
        private const val PLAIN_DEFLATE_B64 =
            "UEsDBBQAAAAIAMBjKV14xdxU9AAAAIYBAAASAAAAYWxpcGF5X3JlY29yZHMuY3N2bY/hSoNQFMe/7yl8gJMdtcW2p6sgFWqj" +
            "0DELhlgWfQhddNFQ3NN4rve+xc5wfrCCA5fD/Z8fv78M87aOKNu2VSqjQEarrnYnKttRs5abQm8E9D/ku91XDbR2KbhWueAk" +
            "yLA4l2EO2nvUL1ug1JPfH6DEu/TLiY325RnOeQzLXji4QASdPui3rNuv9GsM6ianfUlxxZycvAoupiZnoG2emMZKI8SM748I" +
            "ttFXzxxXyT3IWvAMS0+xHXP6L8VCA+cnimo+WfP0kL/TQcEIWibQ/ixVlnCzYzELEc0+zuhx7i/fMnCwHBWVUUyloNu7QdGZ" +
            "/Sp6AFBLAQIUABQAAAAIAMBjKV14xdxU9AAAAIYBAAASAAAAAAAAAAAAAACAAQAAAABhbGlwYXlfcmVjb3Jkcy5jc3ZQSwUG" +
            "AAAAAAEAAQBAAAAAJAEAAAAA"
        private const val PLAIN_STORE_B64 =
            "UEsDBBQAAAAAAMBjKV14xdxUhgEAAIYBAAASAAAAYWxpcGF5X3JlY29yZHMuY3N25pSv5LuY5a6d5Lqk5piT5piO57uGCuiu" +
            "sOW9leaXtumXtCzkuqTmmJPliIbnsbss5ZWG5ZOB6K+05piOLOaUti/mlK8s6YeR6aKdLOWkh+azqCzotKbmiLcKMjAyNi0w" +
            "OS0wOSAxMjozMDowMCzppJDppa7nvo7po58s6IKv5b635Z+6LOaUr+WHuiw0NS4wMCws5L2Z6aKd5a6dCjIwMjYtMDktMDkg" +
            "MTg6MDA6MDAs5Lqk6YCa5Ye66KGMLOa7tOa7tOWHuuihjCzmlK/lh7osMjMuNTAsLOS9memineWunQoyMDI2LTA5LTEwIDA5" +
            "OjAwOjAwLOi9rOi0pizovazotKbliLDpk7booYzljaEs5LiN6K6h5pS25pSvLDEwMDAuMDAs6L2s5Ye65Yiw6ZO26KGM5Y2h" +
            "LOS9memineWunQoyMDI2LTA5LTExIDA4OjAwOjAwLOmkkOmlrue+jumjnyzmmJ/lt7TlhYss5pSv5Ye6LDM4LjAwLCzkvZnp" +
            "op3lrp0KUEsBAhQAFAAAAAAAwGMpXXjF3FSGAQAAhgEAABIAAAAAAAAAAAAAAIABAAAAAGFsaXBheV9yZWNvcmRzLmNzdlBL" +
            "BQYAAAAAAQABAEAAAAC2AQAAAAA="

        private val EXPECTED_CSV_LINES = listOf(
            "支付宝交易明细",
            "记录时间,交易分类,商品说明,收/支,金额,备注,账户",
            "2026-09-09 12:30:00,餐饮美食,肯德基,支出,45.00,,余额宝",
            "2026-09-09 18:00:00,交通出行,滴滴出行,支出,23.50,,余额宝",
            "2026-09-10 09:00:00,转账,转账到银行卡,不计收支,1000.00,转出到银行卡,余额宝",
            "2026-09-11 08:00:00,餐饮美食,星巴克,支出,38.00,,余额宝"
        )
    }
}
