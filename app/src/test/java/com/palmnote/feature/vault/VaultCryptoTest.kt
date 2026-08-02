package com.palmnote.feature.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 密码本加密原语测试：往返、错误密钥、篡改检测、密钥派生确定性。
 */
class VaultCryptoTest {

    @Test
    fun encryptDecrypt_roundTrip_returnsOriginal() {
        val key = VaultCrypto.generateDataKey()
        val plaintext = "p@ssw0rd!秘密123".toByteArray(Charsets.UTF_8)

        val encrypted = VaultCrypto.encrypt(key, plaintext)
        val decrypted = VaultCrypto.decrypt(key, encrypted)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun encrypt_sameKeySamePlaintext_differentCiphertext() {
        val key = VaultCrypto.generateDataKey()
        val plaintext = "same".toByteArray(Charsets.UTF_8)

        val c1 = VaultCrypto.encrypt(key, plaintext)
        val c2 = VaultCrypto.encrypt(key, plaintext)

        assertFalse(c1.contentEquals(c2))
    }

    @Test
    fun decrypt_wrongKey_fails() {
        val key1 = VaultCrypto.generateDataKey()
        val key2 = VaultCrypto.generateDataKey()
        val encrypted = VaultCrypto.encrypt(key1, "secret".toByteArray())

        try {
            VaultCrypto.decrypt(key2, encrypted)
            fail("解密应抛出异常")
        } catch (_: Exception) {
            assertTrue(true)
        }
    }

    @Test
    fun decrypt_tamperedCiphertext_fails() {
        val key = VaultCrypto.generateDataKey()
        val encrypted = VaultCrypto.encrypt(key, "secret".toByteArray())
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0x01).toByte()

        try {
            VaultCrypto.decrypt(key, encrypted)
            fail("篡改数据解密应抛出异常")
        } catch (_: Exception) {
            assertTrue(true)
        }
    }

    @Test
    fun decrypt_tooShortInput_fails() {
        val key = VaultCrypto.generateDataKey()
        try {
            VaultCrypto.decrypt(key, ByteArray(4))
            fail("过短输入应抛出异常")
        } catch (_: Exception) {
            assertTrue(true)
        }
    }

    @Test
    fun deriveKey_samePinSameSalt_deterministic() {
        val salt = VaultCrypto.generateSalt()
        val k1 = VaultCrypto.deriveKey("123456", salt)
        val k2 = VaultCrypto.deriveKey("123456", salt)
        assertArrayEquals(k1.encoded, k2.encoded)
    }

    @Test
    fun deriveKey_differentPin_differentKey() {
        val salt = VaultCrypto.generateSalt()
        val k1 = VaultCrypto.deriveKey("123456", salt)
        val k2 = VaultCrypto.deriveKey("654321", salt)
        assertNotEquals(k1.encoded.toList(), k2.encoded.toList())
    }

    @Test
    fun deriveKey_differentSalt_differentKey() {
        val k1 = VaultCrypto.deriveKey("123456", VaultCrypto.generateSalt())
        val k2 = VaultCrypto.deriveKey("123456", VaultCrypto.generateSalt())
        assertNotEquals(k1.encoded.toList(), k2.encoded.toList())
    }

    @Test
    fun generatedDataKey_hasExpectedLength() {
        assertEquals(32, VaultCrypto.generateDataKey().encoded.size)
    }

    @Test
    fun generatedSalt_hasExpectedLength() {
        assertEquals(16, VaultCrypto.generateSalt().size)
    }
}
