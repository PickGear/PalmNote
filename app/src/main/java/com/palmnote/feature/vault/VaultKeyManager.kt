package com.palmnote.feature.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.palmnote.domain.util.AppLogger
import com.palmnote.data.datastore.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.AEADBadTagException
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
        // 实时读取（prefsState.value 为内存读取，成本低）。不缓存"未初始化"，
        // 否则冷启动时 DataStore 未加载会永久误判为未初始化，进而允许 setup() 覆盖真实凭据。
        val salt = preferencesManager.getVaultSalt()
        if (salt.isNotEmpty()) {
            cachedSalt = salt
            cachedKeyWrap = preferencesManager.getVaultKeyWrap()
            cachedInitialized = true
            return true
        }
        cachedInitialized = false
        return false
    }

    /** 首次使用：生成 salt + DK，用 PIN 派生 K 包裹 DK 落盘。失败返回 false（不崩溃）。 */
    suspend fun setup(pin: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val salt = VaultCrypto.generateSalt()
            val dk = VaultCrypto.generateDataKey()
            val k = VaultCrypto.deriveKey(pin, salt)
            val wrapped = VaultCrypto.encrypt(k, dk.encoded)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val wrapB64 = Base64.encodeToString(wrapped, Base64.NO_WRAP)
            preferencesManager.setVaultCredentials(saltB64, wrapB64, VaultCrypto.PBKDF2_ITERATIONS)
            cachedSalt = saltB64
            cachedKeyWrap = wrapB64
            cachedInitialized = true
            dataKey = dk
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "setup failed", e)
            false
        }
    }

    /** 验证 PIN 并解包 DK。PIN 错误或数据损坏返回 false。
     *  已知派生参数 → 单次派生（输错立即反馈）；历史包裹 → 当前→临时→遗留 回退，
     *  成功后用现行参数重包裹落盘并记录（自动迁移）。 */
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
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val wrapped = Base64.decode(wrapB64, Base64.NO_WRAP)
            val storedIterations = preferencesManager.getVaultKdfIterations()
            val dkBytes = if (storedIterations > 0) {
                VaultCrypto.decrypt(VaultCrypto.deriveKey(pin, salt, storedIterations), wrapped)
            } else {
                decryptWithFallback(pin, salt, wrapped) ?: return@withContext false
            }
            // 包裹参数过时 → 用现行参数重包裹并记录（自动迁移）
            if (storedIterations != VaultCrypto.PBKDF2_ITERATIONS) {
                val newWrap = VaultCrypto.encrypt(VaultCrypto.deriveKey(pin, salt), dkBytes)
                val newWrapB64 = Base64.encodeToString(newWrap, Base64.NO_WRAP)
                preferencesManager.setVaultCredentials(saltB64, newWrapB64, VaultCrypto.PBKDF2_ITERATIONS)
                cachedKeyWrap = newWrapB64
            }
            dataKey = SecretKeySpec(dkBytes, "AES")
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 历史包裹（无参数记录）：按 当前→上版(100k)→临时(600k)→遗留(25k) 依次尝试，返回解出的 DK；全失败返回 null。 */
    private fun decryptWithFallback(pin: String, salt: ByteArray, wrapped: ByteArray): ByteArray? {
        val attempt = { iterations: Int ->
            try {
                VaultCrypto.decrypt(VaultCrypto.deriveKey(pin, salt, iterations), wrapped)
            } catch (_: AEADBadTagException) {
                null
            }
        }
        return attempt(VaultCrypto.PBKDF2_ITERATIONS)
            ?: attempt(VaultCrypto.PREVIOUS_PBKDF2_ITERATIONS)
            ?: attempt(VaultCrypto.INTERIM_PBKDF2_ITERATIONS)
            ?: attempt(VaultCrypto.LEGACY_PBKDF2_ITERATIONS)
    }

    /** 已解锁状态下改 PIN：用新 PIN 派生新 K 重新包裹当前 DK。失败返回 false（不崩溃）。 */
    suspend fun changePin(newPin: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = dataKey ?: return@withContext false
            val saltB64 = cachedSalt.ifEmpty { preferencesManager.getVaultSalt() }
            if (saltB64.isEmpty()) return@withContext false
            cachedSalt = saltB64
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val newK = VaultCrypto.deriveKey(newPin, salt)
            val wrapped = VaultCrypto.encrypt(newK, current.encoded)
            val wrapB64 = Base64.encodeToString(wrapped, Base64.NO_WRAP)
            preferencesManager.setVaultCredentials(saltB64, wrapB64, VaultCrypto.PBKDF2_ITERATIONS)
            cachedKeyWrap = wrapB64
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "changePin failed", e)
            false
        }
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
            AppLogger.w(TAG, "reset: delete keystore key failed", e)
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
    //
    // 可靠性设计（单一官方组合，收敛自调研结论）：
    //  1. 密钥带 30s 认证有效期 + 纯在场 BiometricPrompt（不传 CryptoObject）。
    //     Android 官方明确：带认证有效期的密钥与 CryptoObject 互斥，必须用在场弹窗；
    //     且带有效期密钥 cipher.init 不会因未认证抛异常（规避部分设备单次密钥 init 即抛
    //     UserNotAuthenticatedException 的问题）。
    //  2. 开启时强制重建密钥（getFreshBioKey）：杜绝复用旧版「单次密钥+无有效期」参数生成的
    //     Keystore 残留密钥——旧密钥被复用会导致「指纹验证通过但不解锁」。
    //  3. 解锁失败自愈：指纹变更/密钥失效导致 init 或 doFinal 失败时，删除 Keystore 密钥并
    //     清除磁盘包裹，界面即时隐藏指纹按钮并提示用户重新开启。

    fun isBiometricEnabled(): Boolean {
        if (cachedBioKeyWrap.isEmpty()) {
            cachedBioKeyWrap = preferencesManager.getVaultBioKeyWrap()
        }
        return cachedBioKeyWrap.isNotEmpty()
    }

    /** 在场认证通过后包裹当前内存中的 DK 落盘（须已解锁 + 刚在场认证，30s 窗口内）。
     *  强制重建密钥，避免复用旧版参数不匹配的残留密钥。 */
    suspend fun setupBiometric(): Boolean = withContext(Dispatchers.IO) {
        val current = dataKey ?: return@withContext false
        try {
            wrapBiometric(getFreshBioKey(), current)
        } catch (e: Throwable) {
            AppLogger.w(TAG, "setupBiometric failed, retrying with fresh key", e)
            try {
                wrapBiometric(getFreshBioKey(), current)
            } catch (e2: Throwable) {
                AppLogger.w(TAG, "setupBiometric retry failed", e2)
                false
            }
        }
    }

    /** 生物识别认证通过后解开 DK（带 30s 认证有效期，须在在场 BiometricPrompt 成功后立即调用）。
     *  仅确定性失效（指纹变更/包裹与密钥不匹配/密钥缺失）才自愈删密钥清包裹；
     *  瞬时失败（认证窗口过期、设备 Keystore 临时异常）保留密钥，供重启设备后恢复。 */
    suspend fun unlockBiometric(): Boolean = withContext(Dispatchers.IO) {
        val wrapB64 = cachedBioKeyWrap.ifEmpty { preferencesManager.getVaultBioKeyWrap() }
        if (wrapB64.isEmpty()) return@withContext false
        try {
            val data = Base64.decode(wrapB64, Base64.NO_WRAP)
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            val key = ks.getKey(BIO_KEY_ALIAS, null) as? SecretKey ?: run {
                healInvalidBioKey()
                return@withContext false
            }
            val cipher = Cipher.getInstance(BIO_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(BIO_AAD.toByteArray(Charsets.UTF_8))
            val dkBytes = cipher.doFinal(encrypted)
            dataKey = SecretKeySpec(dkBytes, "AES")
            true
        } catch (e: KeyPermanentlyInvalidatedException) {
            // 指纹变更/删除导致密钥被 Keystore 永久失效：确定性，自愈
            AppLogger.w(TAG, "unlockBiometric key permanently invalidated, self-healing", e)
            healInvalidBioKey()
            false
        } catch (e: AEADBadTagException) {
            // GCM 校验失败 = 包裹与密钥不匹配（旧版残留密钥）：确定性，自愈
            AppLogger.w(TAG, "unlockBiometric wrap/key mismatch, self-healing", e)
            healInvalidBioKey()
            false
        } catch (e: UserNotAuthenticatedException) {
            // 旧版单次密钥（无认证有效期）：确定性失效，自愈
            AppLogger.w(TAG, "unlockBiometric legacy single-use key, self-healing", e)
            healInvalidBioKey()
            false
        } catch (e: Throwable) {
            // 瞬时失败（认证窗口过期 / 设备 Keystore 临时异常）：不删密钥，
            // 提示重试或重启设备（KeePassDX #2250 经验），保留设置待设备恢复后直接可用
            AppLogger.w(TAG, "unlockBiometric failed (transient), key kept", e)
            false
        }
    }

    /** 用给定密钥包裹 DK 落盘并更新缓存（包裹 + 启用标志原子写入）。 */
    private suspend fun wrapBiometric(key: SecretKey, dk: SecretKey): Boolean {
        val cipher = Cipher.getInstance(BIO_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(BIO_AAD.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(dk.encoded)
        val wrap = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        preferencesManager.setVaultBioCredentials(wrap)
        cachedBioKeyWrap = wrap
        return true
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
            AppLogger.w(TAG, "disableBiometric failed", e)
        }
        cachedBioKeyWrap = ""
    }

    /** 重建生物识别密钥：先删旧密钥（可能是失效/旧版参数），再按当前参数生成。 */
    private fun getFreshBioKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        if (ks.containsAlias(BIO_KEY_ALIAS)) {
            ks.deleteEntry(BIO_KEY_ALIAS)
        }
        return getOrCreateBioKey()
    }

    /** 失效密钥自愈：删除 Keystore 密钥并清除磁盘包裹，界面即时隐藏指纹按钮。 */
    private suspend fun healInvalidBioKey() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            if (ks.containsAlias(BIO_KEY_ALIAS)) {
                ks.deleteEntry(BIO_KEY_ALIAS)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "healInvalidBioKey delete failed", e)
        }
        preferencesManager.clearVaultBio()
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
            // 认证有效期：纯在场 BiometricPrompt 认证后 30s 内可直接加密/解密，
            // 使 cipher.init 无需在认证前执行（规避部分设备 init 即抛异常）
            builder.setUserAuthenticationValidityDurationSeconds(BIO_AUTH_TIMEOUT_SECONDS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setInvalidatedByBiometricEnrollment(true)
            }
        }
        generator.init(builder.build())
        return generator.generateKey()
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
            AppLogger.w(TAG, "unlockNoLock failed", e)
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
            preferencesManager.setVaultCredentials(NO_LOCK_SALT, wrap, 0)
            preferencesManager.setVaultNoLock(true)
            cachedSalt = NO_LOCK_SALT
            cachedKeyWrap = wrap
            cachedInitialized = true
            dataKey = dk
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "setupNoLock failed", e)
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
            preferencesManager.setVaultCredentials(saltB64, wrapB64, VaultCrypto.PBKDF2_ITERATIONS)
            preferencesManager.setVaultNoLock(false)
            preferencesManager.clearVaultBio()
            cachedSalt = saltB64
            cachedKeyWrap = wrapB64
            cachedBioKeyWrap = ""
            cachedInitialized = true
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "upgradeToPin failed", e)
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
        const val BIO_AUTH_TIMEOUT_SECONDS = 30
        const val NOLOCK_KEY_ALIAS = "vault_nolock_key"
        const val NOLOCK_AAD = "vault_nolock_dk"
        const val NO_LOCK_SALT = "no_lock"
    }
}
