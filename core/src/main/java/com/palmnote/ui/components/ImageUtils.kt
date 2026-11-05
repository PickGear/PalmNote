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

/**
 * 空占位：图片列表的**首位**存这个空串表示「首图位空着」。
 *
 * 物品的首图是独立槽位——用户删掉首图后，后面几张不会顶上封面位（见 [ImageGridPicker]），
 * 但列表本身必须记住「首位是空着的」，否则存盘再读出来时第二张就会被当成首图。
 * 账单附件没有封面概念，它们的列表永远是紧密排列的，不会出现空串。
 */
const val EMPTY_IMAGE_SLOT = ""

fun List<String>.toImageJson(): String = imageJson.encodeToString(this)

fun String.toImageList(): List<String> {
    if (isEmpty()) return emptyList()
    return try {
        imageJson.decodeFromString<List<String>>(this)
    } catch (_: Exception) {
        listOf(this)
    }
}

/** 首图（封面）路径；首图位空着、或一张图都没有时为 null。 */
fun List<String>.coverImagePath(): String? = firstOrNull()?.takeIf { it.isNotBlank() }

/** 有图的路径（去掉首图位的空占位）；详情页画廊、删文件、导出都该用这个。 */
fun List<String>.imagePaths(): List<String> = filter { it.isNotBlank() }

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
