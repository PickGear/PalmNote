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
 *   PIN ──PBKDF2-SHA256(25000)──▶ K（派生密钥）
 *   用 K（AES-GCM）加密/解包数据密钥 DK（256bit）→ vault_key_wrap
 *   用 DK（AES-GCM，随机 IV）加密每条密码字段 → passwordEncrypted
 *
 * 存储格式（encrypt 输出）：iv(12B) + 密文（含 16B GCM tag）。
 *
 * 迭代次数 25000：100k 在部分机型解锁时体感卡顿，改回 25k 保持输入流畅。
 * 6 位 PIN 密钥空间仅 100 万，迭代数对离线爆破的增益有限（真正防线是 Keystore 绑定的 SQLCipher
 * 库密钥 + 锁屏失败锁定）；25k 为安全性可接受且输入无感的最低体感档位。
 * 历史包裹按参数回退迁移：[PREVIOUS_PBKDF2_ITERATIONS]（100k 上版）→
 * [INTERIM_PBKDF2_ITERATIONS]（600k 测试构建）→ [LEGACY_PBKDF2_ITERATIONS]（25k 首发），
 * 解锁成功自动重包裹为现行参数并记录 [PreferencesManager.VAULT_KDF_ITERATIONS]。
 */
object VaultCrypto {
    /** 现行派生参数。 */
    const val PBKDF2_ITERATIONS = 25000
    /** 上版派生参数（100k 版本创建的历史包裹）。 */
    const val PREVIOUS_PBKDF2_ITERATIONS = 100000
    /** 临时派生参数（600k 的测试构建创建的历史包裹）。 */
    const val INTERIM_PBKDF2_ITERATIONS = 600000
    /** 首发派生参数（升级前创建的历史包裹）。 */
    const val LEGACY_PBKDF2_ITERATIONS = 25000
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
    fun deriveKey(pin: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
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
