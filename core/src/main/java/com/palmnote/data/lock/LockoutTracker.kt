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
    private val keyOffenseCount: String,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private var failedAttempts = prefs.getInt(keyFailedAttempts, 0)
    private var lockoutUntilMs = prefs.getLong(keyLockoutUntil, 0L)
    // 触发锁定的次数（第几次被锁），决定本次锁定时长；仅在成功解锁/显式重置时清零，
    // 锁定期自然过期不清零，否则惩罚永远停留在最轻一档
    private var offenseCount = prefs.getInt(keyOffenseCount, 0)

    init {
        // 锁定已过期的陈旧状态清掉（只清失败计数与锁定期，不动同文件其它键如 pin_salt）
        if (lockoutUntilMs < System.currentTimeMillis()) {
            failedAttempts = 0
            lockoutUntilMs = 0L
            prefs.edit().remove(keyFailedAttempts).remove(keyLockoutUntil).apply()
        }
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntilMs

    fun getLockoutRemainingMs(): Long {
        val remaining = lockoutUntilMs - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    /** 记录一次失败。达到上限则触发递增时长的锁定期。 */
    fun onFailedAttempt() {
        failedAttempts++
        if (failedAttempts >= maxAttempts) {
            offenseCount++
            lockoutUntilMs = System.currentTimeMillis() + lockoutDurationFor(offenseCount)
        }
        persist()
    }

    /** 解锁成功：清零失败计数、锁定期与惩罚档位。 */
    fun onSuccess() {
        failedAttempts = 0
        lockoutUntilMs = 0L
        offenseCount = 0
        clearKeys()
    }

    fun reset() {
        failedAttempts = 0
        lockoutUntilMs = 0L
        offenseCount = 0
        clearKeys()
    }

    private fun clearKeys() {
        prefs.edit()
            .remove(keyFailedAttempts)
            .remove(keyLockoutUntil)
            .remove(keyOffenseCount)
            .apply()
    }

    private fun persist() {
        // 防暴力关键状态用 commit() 同步写盘，进程被杀不丢计数
        prefs.edit()
            .putInt(keyFailedAttempts, failedAttempts)
            .putLong(keyLockoutUntil, lockoutUntilMs)
            .putInt(keyOffenseCount, offenseCount)
            .commit()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        /** 递增惩罚：第 1/2/3/4/≥5 次触发锁定分别锁 30s / 5min / 15min / 1h / 24h。
         *  6 位 PIN 全空间仅 10^6，固定 30s 惩罚下物理接触设备的攻击者可以反复枚举。 */
        private val LOCKOUT_SCHEDULE_MS = longArrayOf(
            30_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L, 24 * 60 * 60_000L
        )
        fun lockoutDurationFor(offenseCount: Int): Long =
            LOCKOUT_SCHEDULE_MS[(offenseCount - 1).coerceIn(LOCKOUT_SCHEDULE_MS.indices)]
    }
}
