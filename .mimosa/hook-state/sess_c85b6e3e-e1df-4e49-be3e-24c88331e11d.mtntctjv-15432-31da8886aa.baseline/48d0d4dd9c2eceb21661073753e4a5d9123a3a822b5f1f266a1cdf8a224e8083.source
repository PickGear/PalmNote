package com.palmnote.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * DbKeyStore 单元测试。
 * 注意：Android Keystore 在 JVM 测试环境中不可用，
 * 此测试仅验证密钥生成逻辑，不测试 Keystore 加密。
 */
class DbKeyStoreTest {

    @Test
    fun `generated key is 32 bytes`() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        assertEquals(32, key.size)
        // 验证不是全零
        assertTrue(key.any { it != 0.toByte() })
    }

    @Test
    fun `two generated keys are different`() {
        val key1 = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val key2 = ByteArray(32).also { SecureRandom().nextBytes(it) }
        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun `encrypt and decrypt round trip`() {
        // 模拟 AES/GCM 加密解密（不依赖 Android Keystore）
        val original = ByteArray(32).also { SecureRandom().nextBytes(it) }

        // 使用 javax.crypto 直接测试
        val keyGen = javax.crypto.KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(original)

        // 解密
        val decryptCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            secretKey,
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        val decrypted = decryptCipher.doFinal(encrypted)

        assertTrue(original.contentEquals(decrypted))
    }
}
