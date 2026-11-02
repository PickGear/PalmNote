package com.palmnote.feature.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密码本剪贴板写入。
 *
 * 只做一件事：把内容写进系统剪贴板，并打上 [ClipDescription.EXTRA_IS_SENSITIVE] 标记
 * （Android 13+ 的系统剪贴板浮层会据此隐藏内容预览）。
 *
 * **2026-11-02 整个删除了「N 秒后自动清除」**。理由是承诺兑现不了：
 * - Android 10+ 只允许**前台且已获得窗口焦点**的应用读剪贴板，计时到点时用户多半正在别的
 *   App 里粘贴，那次清除必然被系统拒绝（真机 logcat：`Denying clipboard access ... not in focus`）；
 * - 更关键的是输入法：你复制后去粘贴，键盘一弹出，输入法就把内容抓进它自己的剪贴板历史，
 *   那份历史第三方应用既清不掉也拦不住。
 * 给不出可信的「已清除」承诺，就不该在设置里摆这个开关，免得用户以为密码已经被收拾干净了。
 */
@Singleton
class VaultClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun copy(label: String, text: String) {
        if (text.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }
}
