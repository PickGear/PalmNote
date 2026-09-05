package com.palmnote.data.lock

import android.content.Context
import android.content.SharedPreferences

/**
 * 防暴力破解追踪器：失败次数 + 锁定时长，持久化到 SharedPreferences。
 * 供应用锁与密码本复用（key 前缀参数化）。
 */
class LockoutTracker(
    context: Context,
    prefsName: String,
    private val keyFailedAttempts: String,
    private val keyLockoutUntil: String,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val lockoutDurationMs: Long = LOCKOUT_DURATION_MS,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private var failedAttempts = prefs.getInt(keyFailedAttempts, 0)
    private var lockoutUntilMs = prefs.getLong(keyLockoutUntil, 0L)

    init {
        // 锁定已过期的陈旧状态清掉（只清自己的 key，不动同文件其它键如 app_lock 的 pin_salt）
        if (lockoutUntilMs < System.currentTimeMillis()) {
            failedAttempts = 0
            lockoutUntilMs = 0L
            clearKeys()
        }
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntilMs

    fun getLockoutRemainingMs(): Long {
        val remaining = lockoutUntilMs - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    /** 记录一次失败。达到上限则触发锁定期。 */
    fun onFailedAttempt() {
        failedAttempts++
        if (failedAttempts >= maxAttempts) {
            lockoutUntilMs = System.currentTimeMillis() + lockoutDurationMs
        }
        persist()
    }

    /** 解锁成功：清零计数与锁定期。 */
    fun onSuccess() {
        failedAttempts = 0
        lockoutUntilMs = 0L
        clearKeys()
    }

    fun reset() {
        failedAttempts = 0
        lockoutUntilMs = 0L
        clearKeys()
    }

    private fun clearKeys() {
        prefs.edit()
            .remove(keyFailedAttempts)
            .remove(keyLockoutUntil)
            .apply()
    }

    private fun persist() {
        // 防暴力关键状态用 commit() 同步写盘，进程被杀不丢计数
        prefs.edit()
            .putInt(keyFailedAttempts, failedAttempts)
            .putLong(keyLockoutUntil, lockoutUntilMs)
            .commit()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L
    }
}
