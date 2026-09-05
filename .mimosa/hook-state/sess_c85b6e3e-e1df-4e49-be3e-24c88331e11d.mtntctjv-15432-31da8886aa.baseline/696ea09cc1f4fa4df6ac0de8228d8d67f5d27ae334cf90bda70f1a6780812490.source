package com.palmnote.feature.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 密码本剪贴板管理：复制后按设置延迟自动清空剪贴板。
 * 通过记录写入内容的 SHA-256 哈希，仅清除自身写入的内容，不误清用户手动复制。
 */
@Singleton
class VaultClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val preferencesManager: PreferencesManager
) {
    // 记录最近一次写入内容的哈希。设计取舍：只追踪最后一次复制——
    // 若连续复制 A、B，则仅 A 的"定时清空/锁定清空"不会触发（B 覆盖了 A 的追踪）。
    // 这在语义上可接受（最新一次复制始终会被清空），且避免了多哈希追踪的复杂度。
    private var pendingHash: String? = null
    private var clearJob: Job? = null

    fun copy(label: String, text: String) {
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        pendingHash = sha256(text)
        clearJob?.cancel()
        clearJob = scope.launch {
            val seconds = preferencesManager.vaultClipboardClearSeconds.first()
            if (seconds <= 0) {
                // 未配置定时清除时仍保留 pendingHash，供锁定时 clearIfOwned() 清空，
                // 避免用户关闭自动清除后复制的敏感内容残留在剪贴板。
                return@launch
            }
            delay(seconds * 1000L)
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (current != null && sha256(current) == pendingHash) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            pendingHash = null
        }
    }

    /** 立即清空自身写入的剪贴板内容（锁定时调用）。 */
    fun clearIfOwned() {
        val hash = pendingHash ?: return
        clearJob?.cancel()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (current != null && sha256(current) == hash) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        pendingHash = null
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}
