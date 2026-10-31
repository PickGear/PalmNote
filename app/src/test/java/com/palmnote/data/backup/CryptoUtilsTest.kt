package com.palmnote.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException

/**
 * 备份整包加解密的往返与失败形态：恢复链路的一切都建立在这两个行为上。
 * 特别钉住 [CryptoUtils.decryptStream] 的成功路径——它曾用 CipherInputStream，
 * 会把 AEAD 模式的标签校验异常吞成正常 EOF（见 c3f22f3），失败形态必须显式可测。
 */
class CryptoUtilsTest {

    @Test
    fun `加密解密往返还原原始字节`() {
        val key = CryptoUtils.deriveKey("密码", ByteArray(16) { 3 }, CryptoUtils.CURRENT_PBKDF2_ITERATIONS)
        // 100KB：多块 + 非整块尾部，覆盖分块 update 的拼接
        val plain = ByteArray(100_000) { (it % 251).toByte() }
        val cipherText = ByteArrayOutputStream().use { out ->
            CryptoUtils.encryptStream(plain.inputStream(), out, key)
            out.toByteArray()
        }
        val restored = ByteArrayOutputStream().use { out ->
            CryptoUtils.decryptStream(ByteArrayInputStream(cipherText), out, key)
            out.toByteArray()
        }
        assertArrayEquals(plain, restored)
    }

    @Test
    fun `密码错误时 GCM 标签校验原样抛出`() {
        val salt = ByteArray(16) { 5 }
        val right = CryptoUtils.deriveKey("正确", salt, CryptoUtils.CURRENT_PBKDF2_ITERATIONS)
        val wrong = CryptoUtils.deriveKey("错误", salt, CryptoUtils.CURRENT_PBKDF2_ITERATIONS)
        val cipherText = ByteArrayOutputStream().use { out ->
            CryptoUtils.encryptStream("数据".toByteArray().inputStream(), out, right)
            out.toByteArray()
        }
        assertThrows(AEADBadTagException::class.java) {
            CryptoUtils.decryptStream(ByteArrayInputStream(cipherText), ByteArrayOutputStream(), wrong)
        }
    }

    @Test
    fun `旧迭代次数派生的密钥仍可解密`() {
        val salt = ByteArray(16) { 9 }
        val legacyKey = CryptoUtils.deriveKey("旧密码", salt, CryptoUtils.LEGACY_PBKDF2_ITERATIONS)
        val plain = "legacy".toByteArray()
        val cipherText = ByteArrayOutputStream().use { out ->
            CryptoUtils.encryptStream(plain.inputStream(), out, legacyKey)
            out.toByteArray()
        }
        val restored = ByteArrayOutputStream().use { out ->
            CryptoUtils.decryptStream(ByteArrayInputStream(cipherText), out, legacyKey)
            out.toByteArray()
        }
        assertArrayEquals(plain, restored)
    }
}
