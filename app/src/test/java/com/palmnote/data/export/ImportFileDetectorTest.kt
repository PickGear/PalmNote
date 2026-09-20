package com.palmnote.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

/** 统一导入入口的文件识别规则回归。 */
class ImportFileDetectorTest {

    private fun zipHeader() = "PK".toByteArray(Charsets.US_ASCII)

    @Test
    fun `PNB magic is detected as backup package regardless of extension`() {
        listOf("PNB3", "PNB2", "PNBK").forEach { magic ->
            assertEquals(
                magic,
                ImportFileDetector.FileType.BACKUP_PACKAGE,
                ImportFileDetector.fromSnapshot("weird_name.bin", magic.toByteArray(), emptyList())
            )
        }
        // 加长头部也要认（实际读 4 字节）
        assertEquals(
            ImportFileDetector.FileType.BACKUP_PACKAGE,
            ImportFileDetector.fromSnapshot("x.zip", "PNB3-rest".toByteArray(), emptyList())
        )
    }

    @Test
    fun `xlsx is a bill file`() {
        assertEquals(
            ImportFileDetector.FileType.BILL_FILE,
            ImportFileDetector.fromSnapshot("微信支付账单.xlsx", "PK".toByteArray(), emptyList())
        )
    }

    @Test
    fun `zip containing export group csvs is full data`() {
        val entries = listOf("记账.csv", "物品.csv", "生活.csv", "images/abc.webp")
        assertEquals(
            ImportFileDetector.FileType.FULL_DATA,
            ImportFileDetector.fromSnapshot("palmnote_all.zip", zipHeader(), entries)
        )
    }

    @Test
    fun `zip containing only preferences json is full data`() {
        assertEquals(
            ImportFileDetector.FileType.FULL_DATA,
            ImportFileDetector.fromSnapshot("palmnote.zip", zipHeader(), listOf("preferences.json"))
        )
    }

    @Test
    fun `zip of wechat bill csv is a bill file`() {
        assertEquals(
            ImportFileDetector.FileType.BILL_FILE,
            ImportFileDetector.fromSnapshot("微信支付账单(1).zip", zipHeader(), listOf("微信支付账单明细.csv"))
        )
    }

    @Test
    fun `zip with only images is a bill file (package export of bills with receipts)`() {
        assertEquals(
            ImportFileDetector.FileType.BILL_FILE,
            ImportFileDetector.fromSnapshot("bills.zip", zipHeader(), listOf("images/a.webp"))
        )
    }

    @Test
    fun `plain csv is a bill file`() {
        assertEquals(
            ImportFileDetector.FileType.BILL_FILE,
            ImportFileDetector.fromSnapshot("bills.csv", byteArrayOf(), emptyList())
        )
    }

    @Test
    fun `unknown files are unknown`() {
        assertEquals(
            ImportFileDetector.FileType.UNKNOWN,
            ImportFileDetector.fromSnapshot("photo.jpg", byteArrayOf(-1, -40), emptyList())
        )
        assertEquals(
            ImportFileDetector.FileType.UNKNOWN,
            ImportFileDetector.fromSnapshot("archive.zip", zipHeader(), listOf("random/entry.txt"))
        )
    }
}
