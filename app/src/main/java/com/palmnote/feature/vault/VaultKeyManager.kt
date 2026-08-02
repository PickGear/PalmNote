package com.palmnote.feature.vault

import android.util.Base64
import com.palmnote.data.datastore.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码本密钥管理器（单例，跨屏幕共享）。
 *
 * - 数据密钥 DK 驻留内存，锁定即清除，永不落盘。
 * - 磁盘仅存：vault_salt + vault_key_wrap（PIN 派生 K 包裹 DK）。
 * - 改 PIN 只重新包裹 DK（密钥包裹模式），条目无需重加密。
 * - PIN 验证 = 解包 DK 成功（无需单独存 PIN 哈希）。
 */
@Singleton
class VaultKeyManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    @Volatile
    private var dataKey: SecretKey? = null

    val isUnlocked: Boolean get() = dataKey != null

    fun isInitialized(): Boolean = preferencesManager.getVaultSalt().isNotEmpty()

    /** 首次使用：生成 salt + DK，用 PIN 派生 K 包裹 DK 落盘。 */
    suspend fun setup(pin: String) = withContext(Dispatchers.IO) {
        val salt = VaultCrypto.generateSalt()
        val dk = VaultCrypto.generateDataKey()
        val k = VaultCrypto.deriveKey(pin, salt)
        val wrapped = VaultCrypto.encrypt(k, dk.encoded)
        preferencesManager.setVaultCredentials(
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(wrapped, Base64.NO_WRAP)
        )
        dataKey = dk
    }

    /** 验证 PIN 并解包 DK。PIN 错误或数据损坏返回 false。 */
    suspend fun unlock(pin: String): Boolean = withContext(Dispatchers.IO) {
        val saltB64 = preferencesManager.getVaultSalt()
        val wrapB64 = preferencesManager.getVaultKeyWrap()
        if (saltB64.isEmpty() || wrapB64.isEmpty()) {
            return@withContext false
        }
        val k = VaultCrypto.deriveKey(pin, Base64.decode(saltB64, Base64.NO_WRAP))
        val wrapped = Base64.decode(wrapB64, Base64.NO_WRAP)
        val dkBytes = try {
            VaultCrypto.decrypt(k, wrapped)
        } catch (_: Exception) {
            return@withContext false
        }
        dataKey = SecretKeySpec(dkBytes, "AES")
        true
    }

    /** 已解锁状态下改 PIN：用新 PIN 派生新 K 重新包裹当前 DK。 */
    suspend fun changePin(newPin: String): Boolean = withContext(Dispatchers.IO) {
        val current = dataKey ?: return@withContext false
        val saltB64 = preferencesManager.getVaultSalt()
        if (saltB64.isEmpty()) return@withContext false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val newK = VaultCrypto.deriveKey(newPin, salt)
        val wrapped = VaultCrypto.encrypt(newK, current.encoded)
        preferencesManager.setVaultCredentials(saltB64, Base64.encodeToString(wrapped, Base64.NO_WRAP))
        true
    }

    /** 加密密码字段（需已解锁）。 */
    fun encryptPassword(plaintext: String): ByteArray? {
        val key = dataKey ?: return null
        return VaultCrypto.encrypt(key, plaintext.toByteArray(Charsets.UTF_8))
    }

    /** 解密密码字段（需已解锁）。 */
    fun decryptPassword(encrypted: ByteArray): String? {
        val key = dataKey ?: return null
        return try {
            String(VaultCrypto.decrypt(key, encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** 锁定：清除内存 DK。 */
    fun lock() {
        dataKey = null
    }

    /** 重置密码本：清除密钥与 salt。 */
    suspend fun reset() = withContext(Dispatchers.IO) {
        dataKey = null
        preferencesManager.clearVaultCredentials()
    }
}
