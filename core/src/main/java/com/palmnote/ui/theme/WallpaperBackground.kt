// 文件按主组件 WallpaperBackground 命名，随附的 WallpaperData/Local 为其公开数据契约
@file:Suppress("MatchingDeclarationName")

package com.palmnote.ui.theme

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Shader

@Immutable
data class WallpaperData(
    val bitmap: ImageBitmap? = null,
    val gradientColors: List<Color>? = null,
    val style: String = "none",
    val opacity: Float = 1f,
    val blur: Float = 0f
)

val LocalWallpaperData = staticCompositionLocalOf { WallpaperData() }

@Composable
fun WallpaperBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val wallpaperData = LocalWallpaperData.current

    Box(modifier = modifier) {
        // Render wallpaper background
        if (wallpaperData.style != "none") {
            when {
                wallpaperData.gradientColors != null ->
                    GradientWallpaper(
                        colors = wallpaperData.gradientColors,
                        opacity = wallpaperData.opacity,
                        modifier = Modifier.fillMaxSize()
                    )
                wallpaperData.bitmap != null ->
                    WallpaperImage(
                        bitmap = wallpaperData.bitmap,
                        opacity = wallpaperData.opacity,
                        blur = wallpaperData.blur,
                        modifier = Modifier.fillMaxSize()
                    )
            }
        }
        content()
    }
}

// 渐变直接用 Brush 绘制，不生成位图（渐变本身平滑，无需模糊处理）
@Composable
private fun GradientWallpaper(
    colors: List<Color>,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .alpha(opacity)
            .background(Brush.verticalGradient(colors))
    )
}

@Composable
private fun WallpaperImage(
    bitmap: ImageBitmap,
    opacity: Float,
    blur: Float,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0f) {
        // Android 12+ native blur via RenderEffect
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = opacity,
            modifier = modifier.blur(blur.dp)
        )
    } else if (blur > 0f) {
        // Fallback: software blur on a downscaled copy for older devices
        val blurredBitmap = rememberBlurredBitmap(bitmap, blur)
        Image(
            bitmap = blurredBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = opacity,
            modifier = modifier
        )
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = opacity,
            modifier = modifier
        )
    }
}

@Composable
private fun rememberBlurredBitmap(bitmap: ImageBitmap, blurRadius: Float): ImageBitmap {
    return remember(bitmap, blurRadius) {
        blurBitmap(bitmap.asAndroidBitmap(), blurRadius).asImageBitmap()
    }
}

private fun blurBitmap(source: Bitmap, radius: Float): Bitmap {
    // 缩小 1/4 再模糊（半径同步缩小），由 Image 拉伸回原尺寸后视觉等价，
    // 避免 BlurMaskFilter 在全尺寸位图上的高开销
    val scale = 4
    val width = (source.width / scale).coerceAtLeast(1)
    val height = (source.height / scale).coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        maskFilter = BlurMaskFilter(radius * 0.5f / scale, BlurMaskFilter.Blur.NORMAL)
    }

    canvas.scale(width.toFloat() / source.width, height.toFloat() / source.height)
    canvas.drawBitmap(source, 0f, 0f, paint)
    return bitmap
}
