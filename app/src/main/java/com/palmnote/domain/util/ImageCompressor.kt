package com.palmnote.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 图片压缩工具。
 * - 存储前压缩：长边 1920px，WebP 格式，质量 80%
 * - 缩略图：200px，质量 60%
 */
object ImageCompressor {

    private const val MAX_EDGE = 1920
    private const val THUMB_EDGE = 200

    private fun lossyWebpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
        else Bitmap.CompressFormat.WEBP

    suspend fun compress(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled(context, uri, MAX_EDGE)
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        File(dir, "${UUID.randomUUID()}.webp").also {
            bitmap.compress(lossyWebpFormat(), 80, it.outputStream())
            bitmap.recycle()
        }
    }

    suspend fun thumbnail(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val bitmap = decodeSampled(context, uri, THUMB_EDGE)
        val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
        File(dir, "${UUID.randomUUID()}.webp").also {
            bitmap.compress(lossyWebpFormat(), 60, it.outputStream())
            bitmap.recycle()
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, maxEdge: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        val scale = maxOf(1, maxOf(options.outWidth, options.outHeight) / maxEdge)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
        return context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)!!
        }
    }
}
