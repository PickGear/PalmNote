package com.palmnote.data.backup

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.db.DbKeyStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 数据库密钥恢复方案（[BackupManager.planDbKeyRestore]）的判定钉：
 *
 * - 备份带便携密钥（db_key.txt）→ 任何设备都能恢复（2026-10-30 定案：备份自包含）；
 * - 不带便携密钥的旧备份，本机 Keystore 解得开 wrapped key → 同设备原样恢复；
 * - 解不开且无便携密钥 → 写盘前拦截，报错必须说清原因与出路（旧版本备份的密钥
 *   只在导出设备的 Keystore 里，物理上不在文件里——这是唯一无法用代码绕开的死路）。
 *
 * 夹具是合成的 PNB3（magic + zip + sha256），不使用真实用户备份（隐私数据不入库）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BackupRestorePlanTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val wrappedKeyXml = """
        <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
        <map>
            <string name="db_key">${fakeWrappedBlob()}</string>
            <boolean name="db_raw_key_migrated_palmnote_db" value="true" />
        </map>
    """.trimIndent()

    /** 结构合法的 wrapped key（12 IV + 32 密钥 + 16 GCM tag = 60 字节），内容是假数据。 */
    private fun fakeWrappedBlob(): String {
        val blob = ByteArray(60) { it.toByte() }
        return android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP)
    }

    private fun portableKeyB64(): String {
        val raw = ByteArray(32) { (it + 1).toByte() }
        return android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
    }

    /** 合成明文 PNB3：magic(4) + zip + sha256(zip)，与 createBackup 的明文布局一致。 */
    private fun writePnb3(file: File, entries: Map<String, String>) {
        val zipBytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zos ->
                entries.forEach { (name, content) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }.toByteArray()
        FileOutputStream(file).use { fos ->
            fos.write(BackupManager.MAGIC_PLAIN_V3.toByteArray())
            fos.write(zipBytes)
            fos.write(MessageDigest.getInstance("SHA-256").digest(zipBytes))
        }
    }

    /** 写一个纯 ZIP（planDbKeyRestore 读的就是解包后的 temp zip，不带 magic/校验和）。 */
    private fun writeZip(file: File, entries: Map<String, String>) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun keyStore(canDecrypt: Boolean): DbKeyStore = mockk {
        every { canDecryptWrappedKey(any()) } returns canDecrypt
    }

    private fun legacyEntries() = mapOf(
        "db/palmnote_db" to "db-bytes",
        "shared_prefs/db_key_prefs.xml" to wrappedKeyXml
    )

    @Test
    fun `本机解得开包裹密钥按同设备方案原样恢复`() {
        val zip = tempFolder.newFile("same-device.zip")
        writeZip(zip, legacyEntries())
        val plan = BackupManager(keyStore(canDecrypt = true)).planDbKeyRestore(zip)
        assertEquals(BackupManager.DbKeyPlan.SameDevice, plan)
    }

    @Test
    fun `备份自带便携密钥时本机解不开也走便携方案`() {
        val zip = tempFolder.newFile("portable.zip")
        writeZip(zip, legacyEntries() + ("db_key.txt" to portableKeyB64()))
        val plan = BackupManager(keyStore(canDecrypt = false)).planDbKeyRestore(zip)
        assertTrue("应判定为便携方案", plan is BackupManager.DbKeyPlan.Portable)
        assertEquals(
            "便携方案应携带 32 字节原始密钥",
            32,
            (plan as BackupManager.DbKeyPlan.Portable).rawKey.size
        )
    }

    @Test
    fun `无密钥条目的极旧备份按同设备方案恢复`() {
        val zip = tempFolder.newFile("no-key-entry.zip")
        writeZip(zip, mapOf("db/palmnote_db" to "db-bytes"))
        val plan = BackupManager(keyStore(canDecrypt = false)).planDbKeyRestore(zip)
        assertEquals(BackupManager.DbKeyPlan.SameDevice, plan)
    }

    @Test
    fun `旧版明文备份且本机无原密钥时写盘前报可行动的错误`() {
        val backup = tempFolder.newFile("legacy.palmnote")
        writePnb3(backup, legacyEntries())
        val manager = BackupManager(keyStore(canDecrypt = false))

        val error = runCatching { manager.restoreBackup(context, backup) }
            .exceptionOrNull()

        assertTrue("应在写盘前拦截", error is IllegalArgumentException)
        assertEquals(
            "报错文案须与资源一致（说清原因与出路）",
            context.getString(R.string.backup_error_cross_device),
            error?.message
        )
    }

    @Test
    fun `非备份文件的乱码按损坏处理而非抛底层异常`() {
        val garbage = tempFolder.newFile("garbage.palmnote")
        garbage.writeBytes(ByteArray(128) { it.toByte() })

        val error = runCatching { BackupManager().restoreBackup(context, garbage) }
            .exceptionOrNull()

        assertEquals(
            context.getString(R.string.backup_error_corrupted),
            error?.message
        )
    }

    @Test
    fun `不含备份条目的普通zip按损坏处理而非静默成功`() {
        val randomZip = tempFolder.newFile("random.zip")
        writeZip(randomZip, mapOf("readme.txt" to "不是掌记备份"))
        // ZIP 头合法（PK..），走的是「旧明文备份」路径：靠必需条目校验拦截

        val error = runCatching { BackupManager().restoreBackup(context, randomZip) }
            .exceptionOrNull()

        assertEquals(
            context.getString(R.string.backup_error_corrupted),
            error?.message
        )
    }

    @Test
    fun `加密备份密码错误时报密码不正确`() {
        val salt = ByteArray(16) { 7 }
        val zipBytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("db/palmnote_db"))
                zos.write("db-bytes".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        val encrypted = tempFolder.newFile("locked.palmnote")
        // PNB2 布局：magic(4) + salt(16) + 整包密文（GCM）
        val key = CryptoUtils.deriveKey("正确密码", salt, CryptoUtils.CURRENT_PBKDF2_ITERATIONS)
        FileOutputStream(encrypted).use { fos ->
            fos.write("PNB2".toByteArray())
            fos.write(salt)
            CryptoUtils.encryptStream(zipBytes.inputStream(), fos, key)
        }

        val error = runCatching {
            BackupManager().restoreBackup(context, encrypted, password = "错误密码")
        }.exceptionOrNull()

        assertEquals(
            context.getString(R.string.backup_password_wrong),
            error?.message
        )
    }
}
