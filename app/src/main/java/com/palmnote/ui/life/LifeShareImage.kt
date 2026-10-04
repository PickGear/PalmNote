package com.palmnote.ui.life

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import java.io.File

/**
 * 分享图的**唯一实现**：Compose 截图 → 存 PNG → 交给系统分享。
 *
 * ## 为什么收拢到一处（而不是各处自己写）
 *
 * 月度回顾原本自己写了一套（`saveReviewPng` + `shareReviewPng`），而它把 PNG 写到
 * `getExternalFilesDir(null)/reviews` —— 那个目录**不在** `file_paths.xml` 配置的根里
 * （此前只配了内部 `filesDir/images`）。于是 `FileProvider.getUriForFile` 必然抛
 * `IllegalArgumentException`，被 `runCatching` 吞掉后只显示「生成回顾图失败」：
 * **功能一直不可用，而且失败原因在界面上完全看不出来**。
 *
 * 现在统一写到 `cacheDir/share`（`file_paths.xml` 已加对应的 `cache-path`）：
 * 分享产物是一次性的，放 cache 既不进备份包（`BackupManager` 会打包 `filesDir/images`），
 * 也不会混进用户的记录照片目录。
 */
internal const val SHARE_DIR = "share"

/** 分享文件名：纯函数（时间戳由调用方注入），便于单测。 */
internal fun lifeShareFileName(prefix: String, now: Long): String = "palmnote-$prefix-$now.png"

/**
 * 分享图的**落点**（唯一来源：生产写入与测试断言都走这里）。
 *
 * 单独抽出来是为了让测试**能真的失败**：如果测试自己拼 `cacheDir/share`，
 * 那它断言的只是自己的算术，生产代码改到别处也照样通过（同义反复）。
 */
internal fun shareDir(context: Context): File = File(context.cacheDir, SHARE_DIR)

/** 把位图存成 PNG 到 [shareDir]，返回文件。**IO 请放到 `Dispatchers.IO`**。 */
internal fun saveSharePng(context: Context, bitmap: Bitmap, fileName: String): File {
    val dir = shareDir(context).apply { mkdirs() }
    val file = File(dir, fileName)
    file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

/** FileProvider 分享：用户自选进相册 / 发消息 / 上传（Wrapped 式传播闭环）。 */
internal fun sharePng(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/**
 * 把这段内容录进 [layer]（配合 `rememberGraphicsLayer()` 截图）。
 *
 * 录完之后照常 `drawContent()` —— 也就是"边正常显示边记录"，不需要额外 `drawLayer`。
 */
internal fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawContent()
}
