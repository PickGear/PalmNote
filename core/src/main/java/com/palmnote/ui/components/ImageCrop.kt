package com.palmnote.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.core.content.FileProvider
import com.palmnote.domain.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 上传图片的裁剪规格与几何计算。
 *
 * ## 交互口径（各场景一致，只有比例与输出尺寸不同）
 * - 图片按「适应屏幕」显示，选区是图片上的一块矩形：拖动选区内部平移，拖四角缩放。
 * - 拖角松手后图片放大到所选区域、选框回最大居中（[settleToMaxFrame]）——框选是临时
 *   取景，收口后框恒为最大居中，拖图时框不动、图片在框下滑动。
 * - 选区永不越出图片；最小边长 = 显示短边的 20%，且不小于 64dp。
 * - 锁定比例的场景（物品 / 头像 1:1、壁纸 9:16）四角缩放保持比例，对角固定。
 * - 取消：直接返回上一页，不落任何文件。
 * - 确认：按选区在原图上的对应区域裁切，缩放到目标尺寸后写盘；确认前可「重置」回到初始选区。
 * - 确认保存后不保留原图，需要换图就重新从相册选（避免存储翻倍，也不会二次裁切损画质）。
 */
enum class CropPurpose {
    /** 物品首图（卡片缩略图）：1:1，长边 1024，写入 files/images/asset_*.jpg */
    ASSET,

    /** 物品附加图（详情画廊，非首图）：自由比例，可裁剪，也可直接原图，长边 1600，前缀 asset_ */
    ASSET_DETAIL,

    /** 账单附件：自由比例，可裁剪，也可直接原图，长边 1600，写入 files/images/bill_*.jpg */
    BILL,

    /** 密码本头像：1:1，512，写入 files/vault_avatars/avatar_*.jpg */
    VAULT_AVATAR,

    /** 「我的」页个人头像：1:1，512，写入 files/avatars/profile_avatar_*.jpg */
    PROFILE_AVATAR,

    /** 首页壁纸：9:16，1080×1920，固定覆盖 files/wallpaper_custom.jpg */
    WALLPAPER
}

/**
 * 裁剪页遮罩形状。只影响预览绘制，不影响裁切数学。
 */
enum class CropMaskShape {
    /** 矩形选区 + 三分线（默认）。 */
    RECTANGLE,

    /**
     * 圆形遮罩：输出按方形裁切、界面按圆形显示（头像），预览与最终观感保持一致，
     * 用户不会框进永远看不见的四个角。四角手柄仍按方形选区工作。
     */
    CIRCLE
}

/**
 * 一个上传位的裁剪规格。
 *
 * [aspect] 为 null 表示自由比例；锁定时 [maxOutputPx] 指长边（壁纸按宽 1080 反推高 1920）。
 * [fixedFileName] 非空时固定覆盖该文件名（壁纸只保留一份），否则按前缀 + 时间戳 + 随机后缀命名。
 */
data class CropSpec(
    val aspect: Float?,
    val maxOutputPx: Int,
    val dirName: String,
    val filePrefix: String,
    val fixedFileName: String? = null,
    /** 是否允许「直接用原图」：非首图/附件类上传不该被强制裁剪。 */
    val optionalCrop: Boolean = false,
    val maskShape: CropMaskShape = CropMaskShape.RECTANGLE
)

fun cropSpecOf(purpose: CropPurpose): CropSpec = when (purpose) {
    CropPurpose.ASSET -> CropSpec(aspect = 1f, maxOutputPx = 1024, dirName = "images", filePrefix = "asset_")
    CropPurpose.ASSET_DETAIL -> CropSpec(
        aspect = null, maxOutputPx = 1600, dirName = "images", filePrefix = "asset_", optionalCrop = true
    )
    CropPurpose.BILL -> CropSpec(
        aspect = null, maxOutputPx = 1600, dirName = "images", filePrefix = "bill_", optionalCrop = true
    )
    CropPurpose.VAULT_AVATAR -> CropSpec(
        aspect = 1f, maxOutputPx = 512, dirName = "vault_avatars", filePrefix = "avatar_",
        maskShape = CropMaskShape.CIRCLE
    )
    CropPurpose.PROFILE_AVATAR -> CropSpec(
        aspect = 1f, maxOutputPx = 512, dirName = "avatars", filePrefix = "profile_avatar_",
        maskShape = CropMaskShape.CIRCLE
    )
    CropPurpose.WALLPAPER -> CropSpec(
        aspect = 9f / 16f, maxOutputPx = 1920, dirName = "", filePrefix = "", fixedFileName = "wallpaper_custom.jpg"
    )
}

/** 选区的四个可拖拽角。 */
enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * 初始选区：锁比例时取图片内最大内接矩形并居中；自由比例时整张图全选。
 * 入参是**显示尺寸**（dp/px 皆可，只要与后续手势同一坐标系）。
 */
fun initialCropRect(image: Size, aspect: Float?): Rect {
    if (image.width <= 0f || image.height <= 0f) return Rect.Zero
    if (aspect == null || aspect <= 0f) return Rect(0f, 0f, image.width, image.height)
    val width: Float
    val height: Float
    if (image.width / image.height > aspect) {
        height = image.height
        width = height * aspect
    } else {
        width = image.width
        height = width / aspect
    }
    val left = (image.width - width) / 2f
    val top = (image.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * 初始选区：锁比例 = 在 [bounds] 内取该比例的最大内接矩形并居中；
 * 自由比例 = 整张图（1x 适应矩形）等比缩进 [bounds] 并居中，保证一进来就看到全图。
 */
fun initialCropFrame(fit: Rect, bounds: Rect, aspect: Float?): Rect {
    if (fit == Rect.Zero || bounds == Rect.Zero) return Rect.Zero
    if (aspect != null && aspect > 0f) {
        return initialCropRect(bounds.size, aspect).translate(bounds.left, bounds.top)
    }
    val shrink = min(1f, min(bounds.width / fit.width, bounds.height / fit.height))
    val width = fit.width * shrink
    val height = fit.height * shrink
    val centerX = bounds.center.x
    val centerY = bounds.center.y
    return Rect(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
}

/**
 * 图片在容器内的「适应屏幕」矩形（居中、保持比例）。缩放/平移都以它为 1x 基准。
 */
fun fitImageRect(imageWidth: Int, imageHeight: Int, container: Size): Rect {
    if (imageWidth <= 0 || imageHeight <= 0) return Rect.Zero
    if (container.width <= 0f || container.height <= 0f) return Rect.Zero
    val scale = min(container.width / imageWidth, container.height / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (container.width - width) / 2f
    val top = (container.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * 图片当前的可视矩形 = 1x 适应尺寸 × 缩放，并加上平移。
 * 与 [fitImageRect] 同一个坐标系（容器坐标）。
 */
fun imageRectFor(fit: Rect, scale: Float, offset: androidx.compose.ui.geometry.Offset, container: Size): Rect {
    val width = fit.width * scale
    val height = fit.height * scale
    val centerX = container.width / 2f + offset.x
    val centerY = container.height / 2f + offset.y
    return Rect(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
}

/** 两个矩形的交集；无交集返回 [Rect.Zero]。 */
fun intersectRects(a: Rect, b: Rect): Rect {
    val left = max(a.left, b.left)
    val top = max(a.top, b.top)
    val right = min(a.right, b.right)
    val bottom = min(a.bottom, b.bottom)
    return if (right <= left || bottom <= top) Rect.Zero else Rect(left, top, right, bottom)
}

/**
 * 平移夹取：把图片推回「盖住选框」的合法范围（选框恒居中，边界就是它自己）。
 * 一维合法区间是 `[框右 - 半宽 - 中心, 框左 + 半宽 - 中心]`，缩放足够大时区间非空。
 */
fun clampImageOffset(
    offset: androidx.compose.ui.geometry.Offset,
    fit: Size,
    scale: Float,
    frame: Rect,
    container: Size
): androidx.compose.ui.geometry.Offset {
    val halfW = fit.width * scale / 2f
    val halfH = fit.height * scale / 2f
    val centerX = container.width / 2f
    val centerY = container.height / 2f
    val minX = frame.right - halfW - centerX
    val maxX = frame.left + halfW - centerX
    val minY = frame.bottom - halfH - centerY
    val maxY = frame.top + halfH - centerY
    val x = if (minX > maxX) (minX + maxX) / 2f else offset.x.coerceIn(minX, maxX)
    val y = if (minY > maxY) (minY + maxY) / 2f else offset.y.coerceIn(minY, maxY)
    return androidx.compose.ui.geometry.Offset(x, y)
}

/**
 * 双击缩放：以 [tap] 处的图片内容为锚点放大/还原。
 *
 * 做法是先算出点击点在旧图上的归一化坐标，再反解出新偏移，使该点在屏幕上仍停在指尖下；
 * 最后走一遍 [clampImageOffset]，不会把图拖出选区。
 */
fun zoomAround(
    tap: androidx.compose.ui.geometry.Offset,
    fromScale: Float,
    fromOffset: androidx.compose.ui.geometry.Offset,
    toScale: Float,
    fit: Rect,
    frame: Rect,
    container: Size
): androidx.compose.ui.geometry.Offset {
    if (fit == Rect.Zero) return fromOffset
    val oldRect = imageRectFor(fit, fromScale, fromOffset, container)
    if (oldRect.width <= 0f || oldRect.height <= 0f) return fromOffset
    val anchorX = ((tap.x - oldRect.left) / oldRect.width).coerceIn(0f, 1f)
    val anchorY = ((tap.y - oldRect.top) / oldRect.height).coerceIn(0f, 1f)
    val width = fit.width * toScale
    val height = fit.height * toScale
    val centerX = container.width / 2f
    val centerY = container.height / 2f
    val offsetX = tap.x - centerX + width / 2f - anchorX * width
    val offsetY = tap.y - centerY + height / 2f - anchorY * height
    return clampImageOffset(
        androidx.compose.ui.geometry.Offset(offsetX, offsetY), fit.size, toScale, frame, container
    )
}

/**
 * 顺时针旋转 90° × [turns]（0..3）。用于把竖拍横放的小票/照片摆正。
 *
 * 直接旋转位图而不是加一层矩阵：后续的适应、缩放、裁切全部沿用同一套坐标，
 * 少一套坐标变换就少一类错位 bug。位图最多 2048 长边，翻转几次的内存代价可以接受。
 */
fun rotateBitmapQuarter(bitmap: Bitmap, turns: Int): Bitmap {
    val normalized = ((turns % 4) + 4) % 4
    if (normalized == 0) return bitmap
    val matrix = Matrix().apply { postRotate(normalized * 90f) }
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: OutOfMemoryError) {
        // 4096 长边的图再复制一份就是上百 MB，低端机会抛 Error。转不动就返回原图：
        // 宁可方向没转过来，也不能让裁剪页直接崩掉（用户还能点取消重来）。
        AppLogger.e("ImageCrop", "OOM while rotating bitmap", e)
        bitmap
    }
}

/** 以 [center] 为中心、给定 [size] 的矩形。「框恒居中」由这里结构保证，不靠事后修正。 */
fun centeredRectOf(size: Size, center: androidx.compose.ui.geometry.Offset): Rect =
    Rect(
        center.x - size.width / 2f,
        center.y - size.height / 2f,
        center.x + size.width / 2f,
        center.y + size.height / 2f
    )

/**
 * 拖角改**尺寸**（位置不参与计算 —— 框恒居中，与小米相册、iOS 同形态）。
 *
 * 位移做对称映射：手指移 dx，宽度变化 **2dx**。这样被拖动的那条边正好跟手，
 * 中心不动、另一边自动镜像 —— 如果只改单边，框会整体往一边长，中心就漂了。
 *
 * [maxSize] 是「当前比例在可用区域内能取到的最大尺寸」，再大会超出图片。
 */
fun resizeCenteredCropSize(
    current: Size,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    aspect: Float?,
    minSide: Float,
    maxSize: Size
): Size {
    if (maxSize.width <= 0f || maxSize.height <= 0f) return current
    val signX = if (handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT) -1f else 1f
    val signY = if (handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT) -1f else 1f
    val minW = max(minSide, 1f)

    if (aspect == null || aspect <= 0f) {
        val lowW = min(minW, maxSize.width)
        val lowH = min(minW, maxSize.height)
        val w = (current.width + 2f * signX * dx).coerceIn(lowW, maxSize.width)
        val h = (current.height + 2f * signY * dy).coerceIn(lowH, maxSize.height)
        return Size(w, h)
    }

    // 锁比例：宽度要同时满足「宽 ≥ minSide」「高 = 宽/比例 ≥ minSide」，且不超过可用区域
    val requiredW = max(minW, minW * aspect)
    val limitW = min(maxSize.width, maxSize.height * aspect)
    if (limitW <= 0f) return current
    val proposedW = current.width + 2f * signX * dx
    val proposedH = current.height + 2f * signY * dy
    // 取「相对变化更大」的轴主导：斜着拖按宽算，竖直拖也能靠高算改变大小
    val driven = if (abs(proposedW - current.width) >= abs(proposedH - current.height) * aspect) {
        proposedW
    } else {
        proposedH * aspect
    }
    val clampedW = driven.coerceIn(min(requiredW, limitW), limitW)
    return Size(clampedW, clampedW / aspect)
}

/**
 * 把选框尺寸同比例收进上限（只缩不放）。
 *
 * 旋转 / 容器变化会让「该比例在图片内能取到的最大尺寸」变小，而选框尺寸是独立状态：
 * 不收的话选框会比图片还大，裁切坐标越界、只能靠钳位取到整图（输出被拉伸）。
 * 同比例缩小（而不是宽高各自钳位）才保得住用户已经框好的形状——锁比例场景两边本来就
 * 同形，自由比例场景保留用户选定的长宽比。
 */
fun clampFrameSizeInside(size: Size, maxSize: Size): Size {
    if (size.width <= 0f || size.height <= 0f) return Size.Zero
    if (maxSize.width <= 0f || maxSize.height <= 0f) return Size.Zero
    val shrink = min(1f, min(maxSize.width / size.width, maxSize.height / size.height))
    return if (shrink >= 1f) size else Size(size.width * shrink, size.height * shrink)
}

/** [settleToMaxFrame] 的收口结果：放大后的缩放/偏移，与回归最大值的选框尺寸。 */
data class CropSettleResult(
    val scale: Float,
    val offset: Offset,
    val frameSize: Size
)

/**
 * 拖角松手后的收口几何：把图片放大到「所选区域填满最大选框」，选框回最大居中。
 *
 * 框选是临时取景动作——拖角圈住要的那块，松手即放大重取景。做法是以框中心为锚
 * （[zoomAround]，所选内容原地铺开不窜位）放大 `scale × k`，`k` 取「最大框 / 当前框」
 * 两轴中较大者（自由比例下当前框与最大框不必同形，取大者保证两轴都填满）；
 * 随后选框尺寸回 [maxFrameSize]。最大框 ⊆ fit ⊆ 任意 scale ≥ 1 的图片矩形，
 * 「框在图片内」由构造保证，偏移最后再走一遍 [clampImageOffset] 兜底。
 *
 * 框已处于最大（`k ≈ 1`）或缩放被 [maxZoom] 顶住时不放大，只回归框尺寸并夹偏移。
 */
fun settleToMaxFrame(
    frameSize: Size,
    maxFrameSize: Size,
    frame: Rect,
    scale: Float,
    offset: Offset,
    fit: Rect,
    container: Size,
    maxZoom: Float
): CropSettleResult {
    if (maxFrameSize == Size.Zero || frame == Rect.Zero || frameSize == Size.Zero) {
        return CropSettleResult(scale, offset, frameSize)
    }
    val k = max(maxFrameSize.width / frame.width, maxFrameSize.height / frame.height)
    var nextScale = scale
    var nextOffset = offset
    if (k > 1.0001f) {
        val target = (scale * k).coerceIn(1f, maxZoom)
        if (target > scale + 0.0001f) {
            nextOffset = zoomAround(frame.center, scale, offset, target, fit, frame, container)
            nextScale = target
        }
    }
    val maxFrame = centeredRectOf(maxFrameSize, frame.center)
    nextOffset = clampImageOffset(nextOffset, fit.size, nextScale, maxFrame, container)
    return CropSettleResult(nextScale, nextOffset, maxFrameSize)
}

/**
 * 「拍照 → 裁剪」的落点：cache/camera/ 下的一次性 JPEG（FileProvider 已配置 camera 根）。
 * 文件名带毫秒时间戳，快速连拍不会互相覆盖。
 */
fun cameraCaptureFile(context: Context): File =
    File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture_${System.currentTimeMillis()}.jpg")

/** [cameraCaptureFile] 产物对应的 FileProvider URI：启动相机与裁剪页回读都用它。 */
fun cameraCaptureUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** 目标输出像素：锁比例按长边 [CropSpec.maxOutputPx] 反推；自由比例按长边等比缩放。 */
fun outputSizeOf(cropWidth: Int, cropHeight: Int, spec: CropSpec): Pair<Int, Int> {
    if (cropWidth <= 0 || cropHeight <= 0) return spec.maxOutputPx to spec.maxOutputPx
    val aspect = spec.aspect
    if (aspect != null && aspect > 0f) {
        return if (aspect >= 1f) {
            spec.maxOutputPx to max(1, (spec.maxOutputPx / aspect).roundToInt())
        } else {
            max(1, (spec.maxOutputPx * aspect).roundToInt()) to spec.maxOutputPx
        }
    }
    val longSide = max(cropWidth, cropHeight)
    if (longSide <= spec.maxOutputPx) return cropWidth to cropHeight
    val scale = spec.maxOutputPx.toFloat() / longSide
    return max(1, (cropWidth * scale).roundToInt()) to max(1, (cropHeight * scale).roundToInt())
}

/**
 * 解码待裁剪图片：按 [maxDim] 长边降采样（省内存），并应用 EXIF 方向（拍照图不校正会躺倒）。
 */
@Suppress("InjectDispatcher")
suspend fun decodeImageForCrop(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = context.contentResolver.openInputStream(uri)
            if (boundsStream == null) {
                AppLogger.e("ImageCrop", "decode: openInputStream returned null")
                return@withContext null
            }
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                AppLogger.e("ImageCrop", "decode bounds invalid: ${bounds.outWidth}x${bounds.outHeight}")
                return@withContext null
            }

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (decoded == null) {
                AppLogger.e("ImageCrop", "decode stream null (${bounds.outWidth}x${bounds.outHeight}, sample=$sample)")
                return@withContext null
            }
            applyExifOrientation(context, uri, decoded)
        } catch (e: OutOfMemoryError) {
            // 4096 长边的 ARGB 位图可达数十 MB，低端机会抛 Error 而不是 Exception；
            // 这里必须自己接住，否则整个 App 崩掉。调用方以「解码失败 → 回退显示图」兜底。
            AppLogger.e("ImageCrop", "OOM while decoding image", e)
            null
        } catch (e: Exception) {
            AppLogger.e("ImageCrop", "Failed to decode image", e)
            null
        }
    }

/** 按 EXIF 方向旋转/镜像；读不到方向时原样返回。 */
private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val orientation = readExifOrientation(context, uri)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
        else -> 0f
    }
    val mirrored = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE
    if (rotation == 0f && !mirrored) return bitmap
    val matrix = Matrix()
    if (rotation != 0f) matrix.postRotate(rotation)
    if (mirrored) matrix.postScale(-1f, 1f)
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    } catch (_: Exception) {
        bitmap
    }
}

private fun readExifOrientation(context: Context, uri: Uri): Int = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        ExifInterface(input).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    } ?: ExifInterface.ORIENTATION_NORMAL
} catch (_: Exception) {
    ExifInterface.ORIENTATION_NORMAL
}

/**
 * 直接用原图：原样拷贝字节（不重编码、不缩放），保留原扩展名。
 *
 * 非首图/附件类上传的默认行为——用户不该为了「上传一张图」先被迫裁一刀。
 */
@Suppress("InjectDispatcher")
suspend fun saveOriginalCopy(context: Context, uri: Uri, spec: CropSpec): String? =
    withContext(Dispatchers.IO) {
        try {
            val dir = if (spec.dirName.isEmpty()) context.filesDir else File(context.filesDir, spec.dirName)
            if (!dir.exists()) dir.mkdirs()
            val extension = imageExtensionOf(context, uri)
            // Android 8.0/8.1 不解 HEIF：原样落盘会在旧机上显示不出来，统一转 JPEG
            if (extension == "heic" || extension == "heif") {
                return@withContext saveHeicAsJpeg(context, uri, spec)
            }
            val name = spec.fixedFileName
                ?: "${spec.filePrefix}${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
            val file = File(dir, name)
            // 先写 .tmp 再改名：复制中途失败（存储满、流断开）不会在目录里留下半张坏图
            val tmp = File(dir, file.name + ".tmp")
            val copied = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } != null
            } catch (e: Exception) {
                AppLogger.e("ImageCrop", "Failed to copy original", e)
                false
            }
            if (!copied || tmp.length() == 0L) {
                tmp.delete()
                null
            } else {
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
                file.absolutePath
            }
        } catch (e: Exception) {
            AppLogger.e("ImageCrop", "Failed to save original", e)
            null
        }
    }

/** HEIC/HEIF 原图转成 JPEG 落盘（旧系统兼容）；失败返回 null。 */
@Suppress("InjectDispatcher")
private suspend fun saveHeicAsJpeg(context: Context, uri: Uri, spec: CropSpec): String? =
    withContext(Dispatchers.IO) {
        try {
            // 不能直接 decodeStream：HEIC 常见 4000×3000 起步，一张 48MP 解成 ARGB 接近 200MB，
            // 低端机直接抛 OutOfMemoryError（catch Exception 抓不到）。复用带降采样 + EXIF + OOM
            // 保护的解码入口，长边封顶 4096 —— 转 JPEG 本来就要重编码，这个上限足够。
            val bitmap = decodeImageForCrop(context, uri, MAX_CROP_DECODE_EDGE)
                ?: return@withContext null
            try {
                val dir = if (spec.dirName.isEmpty()) context.filesDir else File(context.filesDir, spec.dirName)
                if (!dir.exists()) dir.mkdirs()
                val name = "${spec.filePrefix}${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
                val file = File(dir, name)
                val tmp = File(dir, file.name + ".tmp")
                val ok = tmp.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (!ok || tmp.length() == 0L) {
                    tmp.delete()
                    null
                } else {
                    if (!tmp.renameTo(file)) {
                        tmp.copyTo(file, overwrite = true)
                        tmp.delete()
                    }
                    file.absolutePath
                }
            } finally {
                // compress 抛异常时也要回收，否则这张全尺寸位图会一直挂着
                bitmap.recycle()
            }
        } catch (e: OutOfMemoryError) {
            AppLogger.e("ImageCrop", "OOM while converting HEIC", e)
            null
        } catch (e: Exception) {
            AppLogger.e("ImageCrop", "Failed to convert HEIC", e)
            null
        }
    }

/**
 * 「用原图」但用户转过方向：按接近原始分辨率重解码 → 旋转 → 落盘。
 *
 * 转过方向就不能再原样拷贝字节了（那份字节不带旋转），但语义仍是「用原图」：
 * **不裁剪、不缩小**，只多一次旋转与重编码。分辨率上限 [MAX_CROP_DECODE_EDGE]（4096），
 * 再高只是内存风险，绝大多数照片都在此范围内。
 */
@Suppress("InjectDispatcher")
suspend fun saveRotatedOriginal(
    context: Context,
    uri: Uri,
    turns: Int,
    spec: CropSpec
): String? = withContext(Dispatchers.IO) {
    try {
        val decoded = decodeImageForCrop(context, uri, MAX_CROP_DECODE_EDGE) ?: return@withContext null
        val rotated = rotateBitmapQuarter(decoded, turns)
        try {
            writeScaledBitmap(context, rotated, spec)
        } finally {
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
        }
    } catch (e: OutOfMemoryError) {
        AppLogger.e("ImageCrop", "OOM while saving rotated original", e)
        null
    } catch (e: Exception) {
        AppLogger.e("ImageCrop", "Failed to save rotated original", e)
        null
    }
}

/** 原图扩展名：优先按 MIME，退回 URI 后缀，最后兜底 jpg。 */
private fun imageExtensionOf(context: Context, uri: Uri): String {
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
    val byMime = when {
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("heic") || mime.contains("heif") -> "heic"
        mime.contains("gif") -> "gif"
        else -> null
    }
    if (byMime != null) return byMime
    val suffix = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return if (suffix.length in 2..4 && suffix.all { it.isLetterOrDigit() }) suffix else "jpg"
}

/**
 * 把最终位图写到目标位置：透明通道保留为 PNG，其余 JPEG 88；
 * 先写 `.tmp` 再改名，固定文件名的场景（壁纸）中途失败不会留下半张坏图。
 */
private fun writeScaledBitmap(context: Context, bitmap: Bitmap, spec: CropSpec): String {
    val dir = if (spec.dirName.isEmpty()) context.filesDir else File(context.filesDir, spec.dirName)
    if (!dir.exists()) dir.mkdirs()
    val keepAlpha = bitmap.hasAlpha() && spec.fixedFileName == null
    val extension = if (keepAlpha) "png" else "jpg"
    val name = spec.fixedFileName
        ?: "${spec.filePrefix}${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
    val file = File(dir, name)
    val tmp = File(file.parentFile ?: dir, file.name + ".tmp")
    try {
        tmp.outputStream().use { out ->
            val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (keepAlpha) 100 else 88
            check(bitmap.compress(format, quality, out)) { "compress failed" }
        }
    } catch (e: Throwable) {
        // 编码中途失败（磁盘满 / OOM）会留下半截 .tmp，清掉再把原异常抛给调用方
        tmp.delete()
        throw e
    }
    if (!tmp.renameTo(file)) {
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }
    return file.absolutePath
}

/** 确认时重解码的长边上限：再高只是内存风险，输出根本用不到。 */
private const val MAX_CROP_DECODE_EDGE = 4096

/**
 * 按「裁剪区域最终要多大」反推整图解码尺寸。
 *
 * 显示用的图只解到 2048 长边，直接拿它裁一小块再放大必然发虚（例如 1/4 区域只有 512px
 * 却要输出 1024）。这里按 `输出尺寸 ÷ 选区占比` 算出整图需要的长边，封顶 4096 后再解一次：
 * 典型情况一次即可拿到原生像素，内存仍然只解一张图。
 */
@Suppress("InjectDispatcher")
suspend fun decodeForCropQuality(
    context: Context,
    uri: Uri,
    spec: CropSpec,
    cropInImage: Rect
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val fraction = min(cropInImage.width, cropInImage.height).coerceIn(0.02f, 1f)
        val needed = (spec.maxOutputPx / fraction).toInt()
        val cap = min(max(needed, 2048), MAX_CROP_DECODE_EDGE)
            .coerceAtMost(max(bounds.outWidth, bounds.outHeight))
        decodeImageForCrop(context, uri, cap)
    } catch (e: Exception) {
        AppLogger.e("ImageCrop", "Failed to decode hi-res for crop", e)
        null
    }
}

/**
 * 按归一化选区裁切并写盘，返回文件绝对路径；失败返回 null。
 *
 * [cropInImage] 是选区在**原图**上的比例（0..1，左上原点），由裁剪页把显示坐标换算而来——
 * 这样与显示尺寸、屏幕密度无关，同一份比例在任何设备上都落在同一块像素上。
 */
@Suppress("InjectDispatcher")
suspend fun cropBitmapToFile(
    context: Context,
    bitmap: Bitmap,
    spec: CropSpec,
    cropInImage: Rect
): String? = withContext(Dispatchers.IO) {
    var cropped: Bitmap? = null
    var scaled: Bitmap? = null
    try {
        val left = (cropInImage.left.coerceIn(0f, 1f) * bitmap.width).roundToInt()
            .coerceIn(0, bitmap.width - 1)
        val top = (cropInImage.top.coerceIn(0f, 1f) * bitmap.height).roundToInt()
            .coerceIn(0, bitmap.height - 1)
        val width = (cropInImage.width.coerceIn(0.01f, 1f) * bitmap.width).roundToInt()
            .coerceIn(1, bitmap.width - left)
        val height = (cropInImage.height.coerceIn(0.01f, 1f) * bitmap.height).roundToInt()
            .coerceIn(1, bitmap.height - top)
        cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
        val (outW, outH) = outputSizeOf(width, height, spec)
        scaled = if (cropped.width == outW && cropped.height == outH) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, outW, outH, true)
        }

        writeScaledBitmap(context, scaled, spec)
    } catch (e: OutOfMemoryError) {
        AppLogger.e("ImageCrop", "OOM while cropping image", e)
        null
    } catch (e: Exception) {
        AppLogger.e("ImageCrop", "Failed to crop image", e)
        null
    } finally {
        // 入参 bitmap 的所有权归调用方：`Bitmap.createBitmap(src, 0, 0, w, h)` 在「整图 + 无变换」时
        // **原样返回 src**（AOSP 里 `return source`），不判等就会把调用方正在显示的位图回收掉 ——
        // 自由比例场景一打开就是整图，点「使用这张」必现「trying to use a recycled bitmap」。
        if (scaled != null && scaled !== cropped && scaled !== bitmap) scaled.recycle()
        if (cropped != null && cropped !== bitmap) cropped.recycle()
    }
}
