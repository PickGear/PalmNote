package com.palmnote.feature.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.palmnote.data.datastore.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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

    // 缓存 salt/keyWrap/bioKeyWrap，避免每次解锁都 runBlocking 读 DataStore（显著降低解锁延迟）
    private var cachedSalt: String = ""
    private var cachedKeyWrap: String = ""
    private var cachedBioKeyWrap: String = ""
    private var cachedInitialized: Boolean = false

    val isUnlocked: Boolean get() = dataKey != null

    fun isInitialized(): Boolean {
        if (!cachedInitialized) {
            cachedSalt = preferencesManager.getVaultSalt()
            cachedKeyWrap = preferencesManager.getVaultKeyWrap()
            cachedInitialized = true
        }
        return cachedSalt.isNotEmpty()
    }

    /** 首次使用：生成 salt + DK，用 PIN 派生 K 包裹 DK 落盘。 */
    suspend fun setup(pin: String) = withContext(Dispatchers.IO) {
        val salt = VaultCrypto.generateSalt()
        val dk = VaultCrypto.generateDataKey()
        val k = VaultCrypto.deriveKey(pin, salt)
        val wrapped = VaultCrypto.encrypt(k, dk.encoded)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val wrapB64 = Base64.encodeToString(wrapped, Base64.NO_WRAP)
        preferencesManager.setVaultCredentials(saltB64, wrapB64)
        cachedSalt = saltB64
        cachedKeyWrap = wrapB64
        cachedInitialized = true
        dataKey = dk
    }

    /** 验证 PIN 并解包 DK。PIN 错误或数据损坏返回 false。 */
    suspend fun unlock(pin: String): Boolean = withContext(Dispatchers.IO) {
        val saltB64 = cachedSalt.ifEmpty { preferencesManager.getVaultSalt() }
        val wrapB64 = cachedKeyWrap.ifEmpty { preferencesManager.getVaultKeyWrap() }
        if (saltB64.isEmpty() || wrapB64.isEmpty() || saltB64 == NO_LOCK_SALT) {
            return@withContext false
        }
        cachedSalt = saltB64
        cachedKeyWrap = wrapB64
        cachedInitialized = true
        try {
            val k = VaultCrypto.deriveKey(pin, Base64.decode(saltB64, Base64.NO_WRAP))
            val wrapped = Base64.decode(wrapB64, Base64.NO_WRAP)
            val dkBytes = try {
                VaultCrypto.decrypt(k, wrapped)
            } catch (_: Exception) {
                return@withContext false
            }
            dataKey = SecretKeySpec(dkBytes, "AES")
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 已解锁状态下改 PIN：用新 PIN 派生新 K 重新包裹当前 DK。 */
    suspend fun changePin(newPin: String): Boolean = withContext(Dispatchers.IO) {
        val current = dataKey ?: return@withContext false
        val saltB64 = cachedSalt.ifEmpty { preferencesManager.getVaultSalt() }
        if (saltB64.isEmpty()) return@withContext false
        cachedSalt = saltB64
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val newK = VaultCrypto.deriveKey(newPin, salt)
        val wrapped = VaultCrypto.encrypt(newK, current.encoded)
        val wrapB64 = Base64.encodeToString(wrapped, Base64.NO_WRAP)
        preferencesManager.setVaultCredentials(saltB64, wrapB64)
        cachedKeyWrap = wrapB64
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
        preferencesManager.clearVaultBio()
        preferencesManager.clearVaultNoLock()
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            if (ks.containsAlias(BIO_KEY_ALIAS)) {
                ks.deleteEntry(BIO_KEY_ALIAS)
            }
            if (ks.containsAlias(NOLOCK_KEY_ALIAS)) {
                ks.deleteEntry(NOLOCK_KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "reset: delete keystore key failed", e)
        }
        cachedSalt = ""
        cachedKeyWrap = ""
        cachedBioKeyWrap = ""
        cachedInitialized = false
    }

    // ── 生物识别解锁 ──
    // 用 Android Keystore 的不可导出密钥（setUserAuthenticationRequired=true）额外包裹 DK。
    // 生物识别认证通过 → Keystore 解锁该密钥 → 解密 vault_bio_key_wrap → 得到 DK。
    // Keystore 密钥不出 TEE，安全性与 PIN 包裹同等级；改 PIN 不影响（DK 不变）。

    fun isBiometricEnabled(): Boolean {
        if (cachedBioKeyWrap.isEmpty()) {
            cachedBioKeyWrap = preferencesManager.getVaultBioKeyWrap()
        }
        return cachedBioKeyWrap.isNotEmpty()
    }

    /** 生成 Keystore 密钥并包裹当前内存中的 DK（需已解锁）。返回 false 表示失败（如无生物识别硬件）。 */
    suspend fun setupBiometric(): Boolean = withContext(Dispatchers.IO) {
        val current = dataKey ?: return@withContext false
        try {
            val cipher = Cipher.getInstance(BIO_TRANSFORMATION)
            val key = getOrCreateBioKey()
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(BIO_AAD.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(current.encoded)
            val iv = cipher.iv
            val wrap = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
            preferencesManager.setVaultBioKeyWrap(wrap)
            preferencesManager.setVaultBiometricEnabled(true)
            cachedBioKeyWrap = wrap
            true
        } catch (e: Exception) {
            Log.w(TAG, "setupBiometric failed", e)
            false
        }
    }

    /** 用已认证的 Keystore 密钥解密生物识别包裹，得到 DK。需在 BiometricPrompt 回调中调用。 */
    suspend fun decryptWithBiometric(cipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        val wrapB64 = cachedBioKeyWrap.ifEmpty { preferencesManager.getVaultBioKeyWrap() }
        if (wrapB64.isEmpty()) return@withContext false
        try {
            val data = Base64.decode(wrapB64, Base64.NO_WRAP)
            val encrypted = data.copyOfRange(GCM_IV_LENGTH, data.size)
            // cipher 已在 createBioDecryptCipher 用 IV init，认证后直接解密（不重新 init，避免认证 token 不匹配）
            cipher.updateAAD(BIO_AAD.toByteArray(Charsets.UTF_8))
            val dkBytes = cipher.doFinal(encrypted)
            dataKey = SecretKeySpec(dkBytes, "AES")
            true
        } catch (e: Exception) {
            Log.w(TAG, "decryptWithBiometric failed", e)
            false
        }
    }

    /** 移除生物识别密钥与包裹（关闭生物识别 / 重置）。 */
    suspend fun disableBiometric() = withContext(Dispatchers.IO) {
        try {
            preferencesManager.clearVaultBio()
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            if (ks.containsAlias(BIO_KEY_ALIAS)) {
                ks.deleteEntry(BIO_KEY_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "disableBiometric failed", e)
        }
        cachedBioKeyWrap = ""
    }
    private fun getOrCreateBioKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        (ks.getKey(BIO_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(BIO_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setInvalidatedByBiometricEnrollment(true)
            }
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** 在 BiometricPrompt 前初始化解密 Cipher（带 IV，认证成功后直接 doFinal）。返回 null 表示无生物识别密钥。 */
    fun createBioDecryptCipher(): Cipher? {
        return try {
            val wrapB64 = cachedBioKeyWrap.ifEmpty { preferencesManager.getVaultBioKeyWrap() }
            if (wrapB64.isEmpty()) return null
            val data = Base64.decode(wrapB64, Base64.NO_WRAP)
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            val key = ks.getKey(BIO_KEY_ALIAS, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance(BIO_TRANSFORMATION)
            // 用 IV 初始化：Keystore 认证 token 绑定到该操作，认证成功后无需重新 init
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher
        } catch (e: Exception) {
            Log.w(TAG, "createBioDecryptCipher failed", e)
            null
        }
    }

    // ── 无锁模式 ──
    // DK 用「无认证」的 Keystore 密钥包裹（不需 PIN/指纹），数据仍 AES 加密落盘，
    // Keystore 密钥不可导出。打开即用，可随时在设置里升级为 PIN/生物识别锁。

    /** 无锁模式下用 Keystore 密钥解开 DK（无需任何验证）。 */
    suspend fun unlockNoLock(): Boolean = withContext(Dispatchers.IO) {
        try {
            val wrapB64 = cachedKeyWrap.ifEmpty { preferencesManager.getVaultKeyWrap() }
            if (wrapB64.isEmpty()) return@withContext false
            val data = Base64.decode(wrapB64, Base64.NO_WRAP)
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            val key = ks.getKey(NOLOCK_KEY_ALIAS, null) as? SecretKey ?: return@withContext false
            val cipher = Cipher.getInstance(BIO_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(NOLOCK_AAD.toByteArray(Charsets.UTF_8))
            val dkBytes = cipher.doFinal(encrypted)
            dataKey = SecretKeySpec(dkBytes, "AES")
            true
        } catch (e: Exception) {
            Log.w(TAG, "unlockNoLock failed", e)
            false
        }
    }

    /** 无锁模式：生成 DK 并用无认证 Keystore 密钥包裹落盘。数据加密，打开即用。 */
    suspend fun setupNoLock(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dk = dataKey ?: VaultCrypto.generateDataKey()
            val key = getOrCreateNoLockKey()
            val cipher = Cipher.getInstance(BIO_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(NOLOCK_AAD.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(dk.encoded)
            val wrap = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
            preferencesManager.setVaultCredentials(NO_LOCK_SALT, wrap)
            preferencesManager.setVaultNoLock(true)
            cachedSalt = NO_LOCK_SALT
            cachedKeyWrap = wrap
            cachedInitialized = true
            dataKey = dk
            true
        } catch (e: Exception) {
            Log.w(TAG, "setupNoLock failed", e)
            false
        }
    }

    /** 判断是否为无锁模式（salt 为占位 no-lock 标记）。 */
    fun isNoLockMode(): Boolean {
        isInitialized()
        return cachedSalt == NO_LOCK_SALT
    }

    private fun getOrCreateNoLockKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        (ks.getKey(NOLOCK_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(NOLOCK_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // 不设置 setUserAuthenticationRequired：无锁模式无需验证
        generator.init(builder.build())
        return generator.generateKey()
    }

    /** 升级为 PIN 锁：从无锁模式迁移。用新 PIN 派生 K 包裹当前 DK。 */
    suspend fun upgradeToPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val current = dataKey ?: return@withContext false
        try {
            // 先删除无锁/生物识别 Keystore 密钥（可回滚），再写 DataStore，避免磁盘/内存脑裂
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            if (ks.containsAlias(NOLOCK_KEY_ALIAS)) {
                ks.deleteEntry(NOLOCK_KEY_ALIAS)
            }
            if (ks.containsAlias(BIO_KEY_ALIAS)) {
                ks.deleteEntry(BIO_KEY_ALIAS)
            }
            val salt = VaultCrypto.generateSalt()
            val k = VaultCrypto.deriveKey(pin, salt)
            val wrapped = VaultCrypto.encrypt(k, current.encoded)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val wrapB64 = Base64.encodeToString(wrapped, Base64.NO_WRAP)
            preferencesManager.setVaultCredentials(saltB64, wrapB64)
            preferencesManager.setVaultNoLock(false)
            preferencesManager.clearVaultBio()
            cachedSalt = saltB64
            cachedKeyWrap = wrapB64
            cachedBioKeyWrap = ""
            cachedInitialized = true
            true
        } catch (e: Exception) {
            Log.w(TAG, "upgradeToPin failed", e)
            false
        }
    }

    private companion object {
        const val TAG = "VaultKeyManager"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val BIO_KEY_ALIAS = "vault_bio_key"
        const val BIO_TRANSFORMATION = "AES/GCM/NoPadding"
        const val BIO_AAD = "vault_bio_dk"
        const val GCM_IV_LENGTH = 12
        const val NOLOCK_KEY_ALIAS = "vault_nolock_key"
        const val NOLOCK_AAD = "vault_nolock_dk"
        const val NO_LOCK_SALT = "no_lock"
    }
}
