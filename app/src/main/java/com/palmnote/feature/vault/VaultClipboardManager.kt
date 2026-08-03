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
                pendingHash = null
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
