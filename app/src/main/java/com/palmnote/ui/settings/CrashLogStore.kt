package com.palmnote.ui.settings

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 崩溃日志（PalmNoteApp 在 cacheDir 写入的 crash_*.log，内容已脱敏）的读取 / 导出 / 清空。
 *
 * 落盘与导出复用项目既有的 SAF 方式（ActivityResultContracts.CreateDocument + contentResolver 写流），
 * 不新增权限、不新增 FileProvider。
 */
internal object CrashLogStore {

    private const val PREFIX = "crash_"
    private const val SUFFIX = ".log"

    /** 按文件名（内含时间戳）倒序，最新在前。 */
    fun listLogs(context: Context): List<File> {
        return context.cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX)
        }?.sortedByDescending { it.name }.orEmpty()
    }

    fun count(context: Context): Int = listLogs(context).size

    fun totalBytes(context: Context): Long = listLogs(context).sumOf { it.length() }

    /** 合并所有日志为一段文本（最新在前，每份以文件名分隔）；无日志返回 null。 */
    fun readAll(context: Context): String? {
        val logs = listLogs(context)
        if (logs.isEmpty()) return null
        val builder = StringBuilder()
        logs.forEachIndexed { index, file ->
            if (index > 0) builder.append("\n\n")
            builder.append(file.name).append('\n')
            builder.append(runCatching { file.readText() }.getOrDefault(""))
        }
        return builder.toString()
    }

    /** 将全部日志写入用户选定的输出流；无内容或写入失败返回 false。 */
    fun export(context: Context, uri: Uri): Boolean {
        val content = readAll(context) ?: return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 删除全部日志，返回成功删除的份数。 */
    fun clear(context: Context): Int {
        var deleted = 0
        listLogs(context).forEach { if (it.delete()) deleted++ }
        return deleted
    }
}
