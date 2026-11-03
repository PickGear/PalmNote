package com.palmnote.ui.components

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.palmnote.domain.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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
