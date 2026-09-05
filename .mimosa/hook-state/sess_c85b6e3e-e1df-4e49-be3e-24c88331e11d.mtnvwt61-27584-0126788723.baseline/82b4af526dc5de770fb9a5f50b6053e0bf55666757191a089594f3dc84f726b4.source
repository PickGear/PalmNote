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
 * 备份的 db_key_prefs.xml（Keystore 包裹的 db_key）在本机 Keystore 解不开时，
 * restore 应被 [com.palmnote.data.backup.BackupManager] 拦截并抛错，避免写入不可读数据库。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BackupKeyChainTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `restore rejects backup when wrapped key cannot be decrypted`() {
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

    /** 构造 PNB3 明文备份：MAGIC + zip 字节 + SHA-256 校验。 */
    private fun pnb3Backup(fileName: String, entryName: String, entryBody: String): File {
        val zipBytes = zipBytes(entryName, entryBody)
        val digest = MessageDigest.getInstance("SHA-256").digest(zipBytes)
        val out = tempFolder.newFile(fileName)
        out.writeBytes("PNB3".toByteArray() + zipBytes + digest)
        return out
    }

    private fun zipBytes(entryName: String, entryBody: String): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(entryBody.toByteArray())
            zos.closeEntry()
        }
        return bos.toByteArray()
    }
}
