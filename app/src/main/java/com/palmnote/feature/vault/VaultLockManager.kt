package com.palmnote.feature.vault

import android.content.Context
import android.content.SharedPreferences
import com.palmnote.data.datastore.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码本锁定管理（单例）。
 *
 * - 独立于应用锁：即使应用锁未超时，切后台/重新进入密码本仍需重新验证。
 * - 失败计数与锁定时长持久化（进程被杀不重置），防暴力破解。
 * - 锁定即清除内存数据密钥（[VaultKeyManager.lock]）。
 */
@Singleton
class VaultLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: VaultKeyManager,
    private val clipboardManager: VaultClipboardManager,
    private val preferencesManager: PreferencesManager
) {
    enum class LockState { NEED_SETUP, LOCKED, UNLOCKED }

    private val _state = MutableStateFlow<LockState>(LockState.LOCKED)
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var failedAttempts = 0
    private var lockoutUntilMs = 0L
    private var hasKey = false
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun initialize() {
        failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        lockoutUntilMs = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntilMs < System.currentTimeMillis()) {
            failedAttempts = 0
            lockoutUntilMs = 0L
            prefs.edit().clear().apply()
        }
        hasKey = keyManager.isInitialized()
        _state.value = if (hasKey) LockState.LOCKED else LockState.NEED_SETUP
    }

    val isUnlocked: Boolean get() = _state.value == LockState.UNLOCKED

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntilMs

    fun getLockoutRemainingMs(): Long {
        val remaining = lockoutUntilMs - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    /** 首次设置主密码。 */
    suspend fun setup(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (hasKey) {
            return@withContext false
        }
        keyManager.setup(pin)
        hasKey = true
        resetFailureState()
        _state.value = LockState.UNLOCKED
        true
    }

    /** 验证 PIN 解锁。失败递增计数并在超过上限后锁定一段时间。 */
    suspend fun unlock(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (isLockedOut()) {
            return@withContext false
        }
        val ok = keyManager.unlock(pin)
        if (ok) {
            resetFailureState()
            _state.value = LockState.UNLOCKED
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockoutUntilMs = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            }
            persistFailureState()
        }
        ok
    }

    /** 已解锁状态下修改主密码。 */
    suspend fun changePin(newPin: String): Boolean {
        val ok = keyManager.changePin(newPin)
        if (!ok) {
            lock()
        }
        return ok
    }

    fun lock() {
        keyManager.lock()
        clipboardManager.clearIfOwned()
        _state.value = if (hasKey) LockState.LOCKED else LockState.NEED_SETUP
    }

    suspend fun requireAuth(): Boolean = preferencesManager.vaultRequireAuth.first()

    suspend fun reset() = withContext(Dispatchers.IO) {
        keyManager.reset()
        prefs.edit().clear().apply()
        failedAttempts = 0
        lockoutUntilMs = 0L
        hasKey = false
        _state.value = LockState.NEED_SETUP
    }

    private fun resetFailureState() {
        failedAttempts = 0
        lockoutUntilMs = 0L
        prefs.edit().clear().apply()
    }

    private fun persistFailureState() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            .putLong(KEY_LOCKOUT_UNTIL, lockoutUntilMs)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "vault_prefs"
        const val KEY_FAILED_ATTEMPTS = "vault_failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "vault_lockout_until"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30000L
    }
}
