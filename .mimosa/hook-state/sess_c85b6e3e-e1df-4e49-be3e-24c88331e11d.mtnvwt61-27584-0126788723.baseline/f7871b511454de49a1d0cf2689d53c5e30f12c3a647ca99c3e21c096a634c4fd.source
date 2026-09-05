package com.palmnote.data.lock

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.AppDatabase
import com.palmnote.ui.lock.AppLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
class AppLockManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val _lockState = MutableStateFlow<AppLockState>(AppLockState.Unlocked)
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    private var cachedIsLockEnabled: Boolean = false
    private var cachedHasPin: Boolean = false
    // 缓存加密 PIN，避免每次校验都 runBlocking 读 DataStore（显著降低解锁延迟）
    private var cachedEncryptedPin: String = ""

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
    }

    // 防暴力破解追踪（失败次数/锁定期持久化，进程被杀不丢）
    private val lockoutTracker = LockoutTracker(
        context = context,
        prefsName = "app_lock_prefs",
        keyFailedAttempts = KEY_FAILED_ATTEMPTS,
        keyLockoutUntil = KEY_LOCKOUT_UNTIL,
    )

    init {
        // 冷启动时 DataStore 可能尚未完成首次读取，prefsState.value 可能是空快照；
        // 这里先按当前值填充缓存，真正的就绪校正由 refreshFromStore() 在 MainActivity 等待
        // DataStore 首次发射后调用（见 MainActivity 冷启动锁定逻辑）。
        refreshFromStore()
    }

    /** 从 DataStore 当前快照刷新缓存（冷启动等待数据就绪后调用，避免读到空值跳过上锁）。 */
    fun refreshFromStore() {
        cachedIsLockEnabled = preferencesManager.isAppLockEnabled()
        cachedEncryptedPin = preferencesManager.getEncryptedPin()
        cachedHasPin = cachedEncryptedPin.isNotEmpty()
    }

    fun isLockEnabled(): Boolean = cachedIsLockEnabled

    fun hasPin(): Boolean = cachedHasPin

    fun biometricEnabledFlow(): Flow<Boolean> = preferencesManager.biometricEnabled

    fun appLockEnabledFlow(): Flow<Boolean> = preferencesManager.appLockEnabledFlow

    /** 是否已设置 PIN 的 DataStore flow（供设置页响应式开关）。 */
    fun hasPinFlow(): Flow<Boolean> = preferencesManager.encryptedPinFlow.map { it.isNotEmpty() }

    /** 校验 PIN；PBKDF2 迭代较重，放 IO 线程执行避免阻塞 UI */
    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (isLockedOut()) return@withContext false
        val storedPin = cachedEncryptedPin
        if (storedPin.isEmpty()) return@withContext false

        val isValid = if (storedPin.startsWith(PBKDF2_PREFIX)) {
            verifyPbkdf2Pin(pin, storedPin)
        } else {
            val legacyValid = hashPinLegacy(pin) == storedPin
            if (legacyValid) {
                // 旧 SHA-256 哈希迁移到 PBKDF2，并清理明文 salt
                val migrated = hashPin(pin)
                preferencesManager.setEncryptedPin(migrated)
                cachedEncryptedPin = migrated
                prefs.edit().remove("pin_salt").apply()
            }
            legacyValid
        }

        if (isValid) {
            lockoutTracker.onSuccess()
        } else {
            lockoutTracker.onFailedAttempt()
        }
        isValid
    }

    fun getLockoutRemainingMs(): Long = lockoutTracker.getLockoutRemainingMs()

    fun isLockedOut(): Boolean = lockoutTracker.isLockedOut()

    suspend fun resetFailedAttempts() = withContext(Dispatchers.IO) {
        lockoutTracker.reset()
    }

    suspend fun setPin(pin: String, enable: Boolean = false) = withContext(Dispatchers.IO) {
        val hashed = hashPin(pin)
        if (enable) {
            preferencesManager.setAppLockCredentials(hashed, true)
            cachedIsLockEnabled = true
        } else {
            preferencesManager.setEncryptedPin(hashed)
        }
        cachedEncryptedPin = hashed
        cachedHasPin = true
    }

    /** 原子关闭应用锁：清 PIN + 关锁 + 关生物识别（单次 DataStore 写入，避免中间态） */
    suspend fun disableLock() = withContext(Dispatchers.IO) {
        preferencesManager.setAppLockCredentials("", false)
        preferencesManager.setBiometricEnabled(false)
        cachedHasPin = false
        cachedIsLockEnabled = false
        _lockState.value = AppLockState.Unlocked
    }

    /**
     * 忘记 PIN 的唯一自救路径：销毁本地数据并重置锁。
     * 只清 PIN 不销毁数据会让人绕过锁，因此必须删除数据库 / DataStore / 锁偏好 / 备份文件。
     * 删除后由调用方重启进程，让单例以全新状态重新初始化。
     */
    suspend fun resetLockAndData(context: Context) = withContext(Dispatchers.IO) {
        // 1. 先程序化清锁（DataStore 内存+磁盘同步更新，即使进程未重启也能立即解除锁定）
        preferencesManager.setAppLockCredentials("", false)
        preferencesManager.setBiometricEnabled(false)
        cachedHasPin = false
        cachedIsLockEnabled = false
        _lockState.value = AppLockState.Unlocked
        // 2. 删除用户数据库文件，进程重启后重建为空库
        File(context.applicationInfo.dataDir, "databases").listFiles()?.forEach {
            if (it.name.startsWith(AppDatabase.DATABASE_NAME)) it.delete()
        }
        // 3. 清除锁偏好（失败计数等）
        context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        // 4. 删除外置备份（含 DB 快照 + DataStore，可能包含旧 PIN/锁标志）
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "PalmNote")
            .listFiles()?.forEach { it.delete() }
    }

    fun lock() {
        if (cachedIsLockEnabled && cachedHasPin) {
            _lockState.value = AppLockState.Locked
        }
    }

    fun unlock() {
        _lockState.value = AppLockState.Unlocked
    }

    fun setEnabled(enabled: Boolean) {
        preferencesManager.setAppLockEnabledSync(enabled)
        cachedIsLockEnabled = enabled
        if (enabled && !cachedHasPin) {
            _lockState.value = AppLockState.NeedSetup
        } else {
            _lockState.value = AppLockState.Unlocked
        }
    }

    /** PBKDF2 hash: pbkdf2:<iterations>:<base64(salt)>:<base64(hash)> */
    private fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return "${PBKDF2_PREFIX}${PBKDF2_ITERATIONS}:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(hash)}"
    }

    private fun verifyPbkdf2Pin(pin: String, stored: String): Boolean {
        return try {
            val parts = stored.removePrefix(PBKDF2_PREFIX).split(":")
            if (parts.size != 3) false
            else {
                val iterations = parts[0].toInt()
                val salt = Base64.getDecoder().decode(parts[1])
                val expected = Base64.getDecoder().decode(parts[2])
                val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, expected.size * 8)
                val actual = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
                MessageDigest.isEqual(actual, expected)
            }
        } catch (_: Exception) { false }
    }

    /** Legacy SHA-256 (for migration only) */
    private fun hashPinLegacy(pin: String): String {
        val salt = getOrCreateSalt()
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest((salt + pin).toByteArray()))
    }

    private fun getOrCreateSalt(): String {
        var salt = prefs.getString("pin_salt", null)
        if (salt == null) {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            salt = Base64.getEncoder().encodeToString(bytes)
            prefs.edit().putString("pin_salt", salt).apply()
        }
        return salt
    }

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 25000
        private const val SALT_SIZE = 16
        private const val KEY_LENGTH = 256
        private const val PBKDF2_PREFIX = "pbkdf2:"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
