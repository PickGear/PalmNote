// 文件保留 WallpaperManager 命名：WallpaperPresets 与解码工具同属壁纸领域
@file:Suppress("MatchingDeclarationName")

package com.palmnote.data.wallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperPresets {
    data class Preset(val id: String, val lightColors: List<Color>, val darkColors: List<Color>)

    val presets = listOf(
        Preset(
            "ocean",
            listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9)),
            listOf(Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1976D2))
        ),
        Preset(
            "sunset",
            listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2), Color(0xFFFFCC80)),
            listOf(Color(0xFFE65100), Color(0xFFEF6C00), Color(0xFFF57C00))
        ),
        Preset(
            "forest",
            listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9), Color(0xFFA5D6A7)),
            listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C))
        ),
        Preset(
            "lavender",
            listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7), Color(0xFFCE93D8)),
            listOf(Color(0xFF4A148C), Color(0xFF6A1B9A), Color(0xFF7B1FA2))
        ),
        Preset(
            "midnight",
            listOf(Color(0xFF263238), Color(0xFF37474F), Color(0xFF455A64)),
            listOf(Color(0xFF000000), Color(0xFF1A1A1A), Color(0xFF212121))
        ),
        Preset(
            "peach",
            listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3), Color(0xFFFFD54F)),
            listOf(Color(0xFFFF6F00), Color(0xFFFF8F00), Color(0xFFFFA000))
        )
    )

    fun getById(id: String): Preset? = presets.firstOrNull { it.id == id }
}

/**
 * 解码自定义壁纸：在 IO 线程执行，按目标显示尺寸 inSampleSize 降采样，
 * 避免整张原图（可达数十 MB）常驻内存。source 为文件路径或 content:// URI。
 */
suspend fun decodeWallpaperBitmap(
    context: Context,
    source: String,
    reqWidth: Int,
    reqHeight: Int
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (source.isEmpty() || reqWidth <= 0 || reqHeight <= 0) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeSource(context, source, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        }
        decodeSource(context, source, options)?.asImageBitmap()
    }.getOrNull()
}

private fun decodeSource(context: Context, source: String, options: BitmapFactory.Options): android.graphics.Bitmap? {
    return if (source.startsWith("/")) {
        BitmapFactory.decodeFile(source, options)
    } else {
        context.contentResolver.openInputStream(Uri.parse(source))?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}

private fun calcInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
