package com.palmnote.data.lock

import android.app.KeyguardManager
import android.content.Context

/**
 * 自动锁定规则决策：把选择权交给用户。
 *
 * - immediate：切到后台立即回锁
 * - system（默认）：跟随系统锁屏——手机屏锁了才回锁；仅切后台/快速切换不锁（手机有系统锁=用户在场）
 * - timeout：手机锁屏 或 切后台超过 [TIMEOUT_MS] 才回锁
 */
object AutoLockHelper {

    private const val TIMEOUT_MS = 5 * 60 * 1000L // 5 分钟

    /** 判断 ON_START 时是否需要回锁。 */
    fun shouldLock(
        context: Context,
        mode: String,
        backgroundedAt: Long,
    ): Boolean {
        if (backgroundedAt <= 0L) return false
        return when (mode) {
            com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_IMMEDIATE ->
                System.currentTimeMillis() - backgroundedAt >= MIN_IMMEDIATE_GAP_MS
            com.palmnote.data.datastore.PreferencesManager.AUTO_LOCK_MODE_TIMEOUT ->
                isDeviceLocked(context) || System.currentTimeMillis() - backgroundedAt >= TIMEOUT_MS
            // system：仅当系统处于锁屏状态才锁
            else -> isDeviceLocked(context)
        }
    }

    /** 系统锁屏状态（true=设备已锁屏）。 */
    fun isDeviceLocked(context: Context): Boolean {
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
            km.isDeviceLocked
        } catch (_: Exception) {
            false
        }
    }

    // 防止切后台瞬间立即锁（如系统弹窗一闪而过）
    private const val MIN_IMMEDIATE_GAP_MS = 1000L
}
