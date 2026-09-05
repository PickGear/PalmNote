package com.palmnote.ui.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.palmnote.domain.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private val imageJson = Json { ignoreUnknownKeys = true }

fun List<String>.toImageJson(): String = imageJson.encodeToString(this)

fun String.toImageList(): List<String> {
    if (isEmpty()) return emptyList()
    return try {
        imageJson.decodeFromString<List<String>>(this)
    } catch (_: Exception) {
        listOf(this)
    }
}

/**
 * 将 URI 图片复制到内部存储 images 目录，返回文件路径。
 * [prefix] 用于区分模块（如 bill_/asset_），文件名含随机后缀避免同毫秒冲突。
 */
@Suppress("InjectDispatcher")
suspend fun saveImageToInternalStorage(context: Context, uri: Uri, prefix: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "images")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${prefix}${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { inp ->
                FileOutputStream(file).use { output ->
                    inp.copyTo(output)
                }
            }
            if (file.exists()) file.absolutePath else null
        } catch (e: Exception) {
            AppLogger.e("ImageUtils", "Failed to save image", e)
            null
        }
    }

/**
 * 保存密码本头像到独立目录 `vault_avatars/`（不写入共享 images/ 目录，
 * 避免「清理数据」删除 images/ 时误删尚未删除的密码本条目头像）。
 */
@Suppress("InjectDispatcher")
suspend fun saveImageToVaultStorage(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "vault_avatars")
            if (!dir.exists()) dir.mkdirs()
            val file = File(
                dir,
                "avatar_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            )
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { ins ->
                FileOutputStream(file).use { output ->
                    ins.copyTo(output)
                }
            }
            if (file.exists()) file.absolutePath else null
        } catch (e: Exception) {
            AppLogger.e("ImageUtils", "Failed to save vault avatar", e)
            null
        }
    }

/**
 * 将内部存储的图片复制到系统相册（Pictures 目录）。成功返回 true。
 */
@Suppress("InjectDispatcher")
suspend fun saveImageToGallery(context: Context, imagePath: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return@withContext false
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return@withContext false
            val output = context.contentResolver.openOutputStream(uri)
            if (output == null) {
                context.contentResolver.delete(uri, null, null)
                return@withContext false
            }
            output.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            AppLogger.e("ImageUtils", "Save image failed", e)
            false
        }
    }
