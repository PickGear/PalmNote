package com.palmnote.feature.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 密码本加密原语（纯函数、无状态、无 Android 依赖，便于单元测试）。
 *
 * 密钥体系（密钥包裹）：
 *   PIN ──PBKDF2-SHA256(120000)──▶ K（派生密钥）
 *   用 K（AES-GCM）加密/解包数据密钥 DK（256bit）→ vault_key_wrap
 *   用 DK（AES-GCM，随机 IV）加密每条密码字段 → passwordEncrypted
 *
 * 存储格式（encrypt 输出）：iv(12B) + 密文（含 16B GCM tag）。
 */
object VaultCrypto {
    const val PBKDF2_ITERATIONS = 120000
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val KEY_BITS = 256

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }

    fun generateDataKey(): SecretKey {
        val bytes = ByteArray(KEY_BITS / 8).also { secureRandom.nextBytes(it) }
        return SecretKeySpec(bytes, "AES")
    }

    /** PIN → 派生密钥 K（用于解包/包裹 DK），迭代较重，调用方应在 IO 线程。 */
    fun deriveKey(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val derived = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        return SecretKeySpec(derived, "AES")
    }

    /** AES-GCM 加密，输出 iv + 密文（含 tag）。 */
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    /** AES-GCM 解密 [data]（iv + 密文）。失败抛 [Exception]，由调用方判定 PIN 错误/数据损坏。 */
    fun decrypt(key: SecretKey, data: ByteArray): ByteArray {
        require(data.size > GCM_IV_LENGTH) { "ciphertext too short" }
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
