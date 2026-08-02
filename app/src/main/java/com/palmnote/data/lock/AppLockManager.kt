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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
    }

    // 失败次数/锁定时长持久化到 SharedPreferences，防止杀进程重置后无限暴力尝试
    private var failedAttempts: Int = 0
    private var lockoutUntilMs: Long = 0L

    init {
        cachedIsLockEnabled = preferencesManager.isAppLockEnabled()
        cachedHasPin = preferencesManager.getEncryptedPin().isNotEmpty()
        failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        lockoutUntilMs = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        // 锁定已过期的陈旧状态清掉，避免重启后输错一次就再次被锁
        if (lockoutUntilMs < System.currentTimeMillis()) {
            failedAttempts = 0
            lockoutUntilMs = 0L
            // 构造期（主线程）：用 apply 异步写，避免同步 commit 阻塞；此时锁尚未生效，可接受
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
        }
    }

    fun isLockEnabled(): Boolean = cachedIsLockEnabled

    fun hasPin(): Boolean = cachedHasPin

    fun biometricEnabledFlow(): Flow<Boolean> = preferencesManager.biometricEnabled

    fun appLockEnabledFlow(): Flow<Boolean> = preferencesManager.appLockEnabledFlow

    /** 校验 PIN；PBKDF2 迭代较重，放 IO 线程执行避免阻塞 UI */
    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (isLockedOut()) return@withContext false
        val storedPin = preferencesManager.getEncryptedPin()
        if (storedPin.isEmpty()) return@withContext false

        val isValid = if (storedPin.startsWith(PBKDF2_PREFIX)) {
            verifyPbkdf2Pin(pin, storedPin)
        } else {
            val legacyValid = hashPinLegacy(pin) == storedPin
            if (legacyValid) {
                // 旧 SHA-256 哈希迁移到 PBKDF2，并清理明文 salt
                preferencesManager.setEncryptedPin(hashPin(pin))
                prefs.edit().remove("pin_salt").apply()
            }
            legacyValid
        }

        if (isValid) {
            failedAttempts = 0
            lockoutUntilMs = 0L
            persistLockout()
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockoutUntilMs = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            }
            persistLockout()
        }
        isValid
    }

    fun getLockoutRemainingMs(): Long {
        val remaining = lockoutUntilMs - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntilMs

    suspend fun resetFailedAttempts() = withContext(Dispatchers.IO) {
        failedAttempts = 0
        lockoutUntilMs = 0L
        persistLockout()
    }

    suspend fun setPin(pin: String, enable: Boolean = false) = withContext(Dispatchers.IO) {
        if (enable) {
            preferencesManager.setAppLockCredentials(hashPin(pin), true)
            cachedIsLockEnabled = true
        } else {
            preferencesManager.setEncryptedPin(hashPin(pin))
        }
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

    private fun persistLockout() {
        // 同步写：锁定状态是防暴力破解的关键，异步 apply 在进程被杀时可能丢失
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            .putLong(KEY_LOCKOUT_UNTIL, lockoutUntilMs)
            .commit()
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
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30000L
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
