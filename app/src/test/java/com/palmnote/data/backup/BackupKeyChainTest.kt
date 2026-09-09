package com.palmnote.data.backup

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.db.DbKeyStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * SQLCipher 密钥跨设备恢复链路测试：
 * - 本机 Keystore 能解开备份的 wrapped key → 原样恢复（同机路径）；
 * - 解不开（换机/重装）但备份含便携密钥 db_key.txt → 跳过 wrapped key 条目并 importRawKey；
 * - 两者皆无（旧版本备份）→ 拒绝恢复，避免写入不可读数据库。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BackupKeyChainTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `restore rejects backup when wrapped key cannot be decrypted and no portable key`() {
        val keyStore = mockk<DbKeyStore>()
        every { keyStore.canDecryptWrappedKey(any()) } returns false

        val backup = pnb3Backup("reject.palmnote", dbKeyPrefsEntry(), dbKeyPrefsXml())
        val manager = BackupManager(keyStore)

        assertThrows(IllegalArgumentException::class.java) {
            manager.restoreBackup(context, backup, null)
        }
        verify { keyStore.canDecryptWrappedKey(any()) }
    }

    @Test
    fun `restore proceeds when wrapped key can be decrypted`() {
        val keyStore = mockk<DbKeyStore>()
        every { keyStore.canDecryptWrappedKey(any()) } returns true

        val backup = pnb3Backup("recoverable.palmnote", dbKeyPrefsEntry(), dbKeyPrefsXml())
        val manager = BackupManager(keyStore)

        manager.restoreBackup(context, backup, null)
        verify { keyStore.canDecryptWrappedKey(any()) }
        verify(exactly = 0) { keyStore.importRawKey(any()) }
    }

    @Test
    fun `restore imports portable key on new device`() {
        val keyStore = mockk<DbKeyStore>()
        every { keyStore.canDecryptWrappedKey(any()) } returns false
        every { keyStore.importRawKey(any()) } returns Unit

        val rawKey = ByteArray(DbKeyStore.KEY_SIZE) { it.toByte() }
        val backup = pnb3Backup(
            "portable.palmnote",
            listOf(dbKeyPrefsEntry() to dbKeyPrefsXml(), BackupManager.PORTABLE_KEY_ENTRY to portableKeyXml(rawKey))
        )
        BackupManager(keyStore).restoreBackup(context, backup, null)

        verify(exactly = 1) { keyStore.importRawKey(rawKey) }
    }

    @Test
    fun `restore rejects portable key of wrong size`() {
        val keyStore = mockk<DbKeyStore>()
        every { keyStore.canDecryptWrappedKey(any()) } returns false

        val backup = pnb3Backup(
            "badsize.palmnote",
            listOf(
                dbKeyPrefsEntry() to dbKeyPrefsXml(),
                BackupManager.PORTABLE_KEY_ENTRY to portableKeyXml(ByteArray(16))
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager(keyStore).restoreBackup(context, backup, null)
        }
        verify(exactly = 0) { keyStore.importRawKey(any()) }
    }

    @Test
    fun `restore without key prefs skips validation`() {
        val keyStore = mockk<DbKeyStore>()

        val backup = pnb3Backup("nokey.palmnote", "shared_prefs/other.xml", "<map/>")
        BackupManager(keyStore).restoreBackup(context, backup, null)
        verify(exactly = 0) { keyStore.canDecryptWrappedKey(any()) }
    }

    @Test
    fun `canDecryptWrappedKey returns false for blank and garbage`() {
        val underTest = DbKeyStore(context)
        assertEquals(false, underTest.canDecryptWrappedKey(""))
        assertEquals(false, underTest.canDecryptWrappedKey("not-base64!!!"))
    }

    // ── helpers ──

    private fun dbKeyPrefsEntry(): String = "shared_prefs/${DbKeyStore.PREFS_NAME}.xml"

    private fun dbKeyPrefsXml(): String = """
        <?xml version="1.0" encoding="utf-8" standalone="yes"?>
        <map>
            <string name="${DbKeyStore.KEY_NAME}">cGFybW5vdGUtZGIta2V5LXdyYXBwZWQ=</string>
        </map>
    """.trimIndent()

    private fun portableKeyXml(rawKey: ByteArray): String =
        android.util.Base64.encodeToString(rawKey, android.util.Base64.NO_WRAP)

    /** 构造 PNB3 明文备份：MAGIC + zip 字节 + SHA-256 校验。 */
    private fun pnb3Backup(fileName: String, entryName: String, entryBody: String): File =
        pnb3Backup(fileName, listOf(entryName to entryBody))

    private fun pnb3Backup(fileName: String, entries: List<Pair<String, String>>): File {
        val zipBytes = zipBytes(entries)
        val digest = MessageDigest.getInstance("SHA-256").digest(zipBytes)
        val out = tempFolder.newFile(fileName)
        out.writeBytes("PNB3".toByteArray() + zipBytes + digest)
        return out
    }

    private fun zipBytes(entries: List<Pair<String, String>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, body) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(body.toByteArray())
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
