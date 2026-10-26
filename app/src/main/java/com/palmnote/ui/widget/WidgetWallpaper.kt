package com.palmnote.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.wallpaper.decodeWallpaperBitmap
import com.palmnote.ui.components.toComposeColor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * 桌面小组件的**按实例背景**。
 *
 * 背景**刻意与 app 的全局壁纸脱钩**（用户 2026-10-19 定）：小组件的背景是逐实例的个性化项，
 * 在添加面板/配置页单独编辑（[PreferencesManager.widgetBackgroundFor]），不随 app 壁纸联动。
 *
 * 为什么不用 `setBackgroundColor`：卡片的圆角来自 `widget_card_round`（22dp shape），
 * 直接设背景色会把圆角换成直角。三条路各有各的用法：
 * - **纯色 / 默认卡底（Android 12+）**：给卡片底色 `setBackgroundTintList` —— 颜色（含 alpha）换了、
 *   形状留着，**不建位图**；
 * - **图片**：解码后按 centerCrop 裁成圆角 Bitmap 塞进卡片下层的 `widget_bg`（ImageView），
 *   卡片自身底色清透明，背景从底下透出；
 * - **Android 12 以下**：没有 `setColorStateList` 的入口，卡底（默认色或自定义纯色）一律按精确
 *   alpha 画成圆角位图走 `widget_bg` 层 —— 这样卡底的不透明度是**精确**的（早先用 40/60/80 三档
 *   预置底色，最小档 20% 时卡底还画 40%，与滑杆对不上）。
 *
 * 值的三种形态：null（默认卡片底色）/ "#RRGGBB"（纯色）/ "img:<uri>"（图片）。
 * 图片的 uri 在选图时已 takePersistableUriPermission，重启后仍可读；换设备或源图被删时解码失败
 * 自动回退默认卡片底色。
 *
 * **不透明度只作用在这里（背景层）**：卡上内容保持实色（`setImageAlpha` 打在背景层的 ImageView 上，
 * 卡片底色则把 alpha 直接写进 tint 颜色）。内容跟着一起发灰的旧语义（整卡 `setAlpha`）已去掉 ——
 * 那样既与预览对不上，也会把正文对比度一起压掉。
 */
object WidgetWallpaper {

    /** 与 `widget_card_round.xml` 的 `corners android:radius` 保持一致，背景层才能和卡片同形。 */
    private const val CORNER_DP = 22f

    private const val MIN_SIDE_PX = 48
    private const val MAX_SIDE_PX = 2048

    /** 取平均色的采样步长（像素）：背景图动辄 900×700，逐点算没必要。 */
    private const val AVERAGE_STEP = 8

    /** [toneOnly] 解码图片背景时的采样边长：只用来取平均色判明暗，不需要细节。 */
    private const val TONE_SAMPLE_PX = 128

    /** 图片背景模糊的上限半径（px，作用在 1/4 缩图上，等效观感约 ×4）。 */
    private const val MAX_BLUR_RADIUS_PX = 25

    /**
     * 一次解码的结果。`toneSource` 是算内容色调用的代表色 —— **必须在 bind 之前**拿到，
     * 因为色调要在 bind 时就把颜色下发给卡上的文字/面板。
     */
    class Background(
        /** 纯色背景（可能自带 alpha）；null = 图片或未接管。 */
        val color: Int?,
        /** 图片背景（已裁圆角、已按目标尺寸缩放）。 */
        val image: Bitmap?,
        /** 算色调用的代表色：纯色就是它自己，图片取平均色。 */
        val toneSource: Int
    )

    /** 背景层要铺的像素尺寸（dp → px，与卡片的圆角一起算好，纯色/图片两条路共用）。 */
    private class RenderSize(val widthPx: Int, val heightPx: Int, val radiusPx: Float)

    /** 读该实例的背景配置；返回 null = 不接管（默认卡片底色）。 */
    suspend fun load(
        context: Context,
        preferences: PreferencesManager,
        appWidgetId: Int,
        widthDp: Int,
        heightDp: Int
    ): Background? {
        val value = preferences.widgetBackgroundFor(appWidgetId).first() ?: return null
        // "none" = 不接管（app 壁纸用同一个字符串表示「无」，组件侧保持旧映射）
        if (value == "none") return null

        val size = renderSize(context, widthDp, heightDp)
        return if (value.startsWith("img:")) {
            val blur = preferences.widgetBlurFor(appWidgetId).first()
            imageBackground(context, value.removePrefix("img:"), size, blur)
        } else {
            solidBackground(value)
        }
    }

    /** 源图读不出来（换设备/被删）→ null，调用方回退默认卡底，不留白卡。 */
    private suspend fun imageBackground(
        context: Context,
        source: String,
        size: RenderSize,
        blur: Float = 0f
    ): Background? {
        val decoded = decodeWallpaperBitmap(context, source, size.widthPx, size.heightPx) ?: return null
        var bmp = decoded.asAndroidBitmap()
        if (blur > 0f) bmp = blurBitmap(bmp, blur)
        val cropped = cropIntoRounded(bmp, size.widthPx, size.heightPx, size.radiusPx)
        return Background(color = null, image = cropped, toneSource = averageColor(cropped))
    }

    /**
     * 图片背景模糊（0..1 → 半径 1..25px）。栈式盒模糊两趟近似高斯：
     * minSdk 26 没有 [android.graphics.RenderEffect]（API 31），组件更新频率低，
     * 软件模糊的开销可接受；先缩到 1/4 边长模糊再放大，速度再省一个量级。
     */
    /** 预览端复用的模糊入口（与落桌渲染同一条实现）。 */
    internal fun blurForPreview(source: Bitmap, blur: Float): Bitmap = blurBitmap(source, blur)

    private fun blurBitmap(source: Bitmap, blur: Float): Bitmap {
        val radius = (blur.coerceIn(0f, 1f) * MAX_BLUR_RADIUS_PX).toInt().coerceAtLeast(1)
        val scale = 0.25f
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        boxBlur(pixels, w, h, radius)
        boxBlur(pixels, w, h, radius)
        blurred.setPixels(pixels, 0, w, 0, 0, w, h)
        return Bitmap.createScaledBitmap(blurred, source.width, source.height, true)
    }

    /** 单趟盒模糊（水平 + 垂直各一遍），原地写回 [pixels]。 */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val temp = IntArray(pixels.size)
        var sumR: Long; var sumG: Long; var sumB: Long; var sumA: Long
        // 水平
        for (y in 0 until h) {
            val row = y * w
            sumR = 0; sumG = 0; sumB = 0; sumA = 0
            for (x in -radius..radius) {
                val px = pixels[row + x.coerceIn(0, w - 1)]
                sumA += (px ushr 24) and 0xFF; sumR += (px ushr 16) and 0xFF
                sumG += (px ushr 8) and 0xFF; sumB += px and 0xFF
            }
            val window = radius * 2 + 1
            for (x in 0 until w) {
                temp[row + x] =
                    ((sumA / window).toInt() shl 24) or ((sumR / window).toInt() shl 16) or
                        ((sumG / window).toInt() shl 8) or (sumB / window).toInt()
                val outPx = pixels[row + (x - radius).coerceIn(0, w - 1)]
                val inPx = pixels[row + (x + radius + 1).coerceIn(0, w - 1)]
                sumA += ((inPx ushr 24) and 0xFF) - ((outPx ushr 24) and 0xFF)
                sumR += ((inPx ushr 16) and 0xFF) - ((outPx ushr 16) and 0xFF)
                sumG += ((inPx ushr 8) and 0xFF) - ((outPx ushr 8) and 0xFF)
                sumB += (inPx and 0xFF) - (outPx and 0xFF)
            }
        }
        // 垂直
        for (x in 0 until w) {
            sumR = 0; sumG = 0; sumB = 0; sumA = 0
            for (y in -radius..radius) {
                val px = temp[y.coerceIn(0, h - 1) * w + x]
                sumA += (px ushr 24) and 0xFF; sumR += (px ushr 16) and 0xFF
                sumG += (px ushr 8) and 0xFF; sumB += px and 0xFF
            }
            val window = radius * 2 + 1
            for (y in 0 until h) {
                pixels[y * w + x] =
                    ((sumA / window).toInt() shl 24) or ((sumR / window).toInt() shl 16) or
                        ((sumG / window).toInt() shl 8) or (sumB / window).toInt()
                val outPx = temp[(y - radius).coerceIn(0, h - 1) * w + x]
                val inPx = temp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                sumA += ((inPx ushr 24) and 0xFF) - ((outPx ushr 24) and 0xFF)
                sumR += ((inPx ushr 16) and 0xFF) - ((outPx ushr 16) and 0xFF)
                sumG += ((inPx ushr 8) and 0xFF) - ((outPx ushr 8) and 0xFF)
                sumB += (inPx and 0xFF) - (outPx and 0xFF)
            }
        }
    }

    private fun solidBackground(value: String): Background {
        val color = value.toComposeColor(ComposeColor.White).toArgb()
        return Background(color = color, image = null, toneSource = color)
    }

    private fun renderSize(context: Context, widthDp: Int, heightDp: Int): RenderSize {
        val density = context.resources.displayMetrics.density
        return RenderSize(
            widthPx = (widthDp * density).roundToInt().coerceIn(MIN_SIDE_PX, MAX_SIDE_PX),
            heightPx = (heightDp * density).roundToInt().coerceIn(MIN_SIDE_PX, MAX_SIDE_PX),
            radiusPx = CORNER_DP * density
        )
    }

    /** 该实例的内容色调；未接管背景时返回 null（内容色继续跟随系统，一个字都不动）。 */
    fun toneOf(context: Context, background: Background?): WidgetTone? =
        background?.let { WidgetTonePicker.of(context, it.toneSource) }

    /**
     * 只算色调、不建背景位图。给 [TodoWidgetService] 用：列表行不在卡面上（它们由
     * RemoteViewsService 单独出），但行上的字必须和卡上的字同一个色调。图片照样要解码，
     * 取平均色只用一个很小的采样尺寸就够，比整卡解码便宜得多。
     */
    suspend fun toneOnly(
        context: Context,
        preferences: PreferencesManager,
        appWidgetId: Int
    ): WidgetTone? {
        val value = preferences.widgetBackgroundFor(appWidgetId).first() ?: return null
        if (value == "none") return null
        return if (value.startsWith("img:")) {
            imageTone(context, value.removePrefix("img:"))
        } else {
            WidgetTonePicker.of(context, value.toComposeColor(ComposeColor.White).toArgb())
        }
    }

    private suspend fun imageTone(context: Context, source: String): WidgetTone? {
        val decoded = decodeWallpaperBitmap(context, source, TONE_SAMPLE_PX, TONE_SAMPLE_PX) ?: return null
        return WidgetTonePicker.of(context, averageColor(decoded.asAndroidBitmap()))
    }

    /**
     * 把背景层与不透明度落到 RemoteViews 上。调用处在 bind **之后**（[Background] 在 bind 之前就绪）。
     */
    fun apply(
        context: Context,
        views: RemoteViews,
        background: Background?,
        widthDp: Int,
        heightDp: Int,
        opacity: Float
    ) {
        val image = background?.image
        if (image != null) {
            views.setImageViewBitmap(R.id.widget_bg, image)
            views.setViewVisibility(R.id.widget_bg, View.VISIBLE)
            // 内容不透：alpha 只打在背景层这张 ImageView 上
            views.setInt(R.id.widget_bg, "setImageAlpha", alphaOf(opacity))
            views.setInt(R.id.widget_layout, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
            return
        }

        val alpha = opacity.coerceIn(0f, 1f)
        views.setViewVisibility(R.id.widget_bg, View.GONE)
        // 卡底复位成标准圆角底：从图片背景切回纯色/默认时，卡底此前被清成透明，不复位会连底色都没有
        views.setInt(R.id.widget_layout, "setBackgroundResource", R.drawable.widget_card_round)

        val color = background?.color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // **总是**写 tint：同一实例从「自定义色 / 低不透明度」切回默认时，view 上还留着上一轮
            // 的 tint（launcher 复用视图树，setBackgroundResource 也不会清 tint），不覆写就会一直是半透的旧色
            val card = color ?: systemCardColor(context)
            tintCard(views, withAlpha(card, alpha))
            return
        }
        // 31 以下：没有 setBackgroundTintList 的入口（RemoteViews 只能按 id 调 @RemotableViewMethod），
        // 于是把卡底按**精确 alpha** 画进圆角位图，走 widget_bg 那一层 —— 与自定义纯色同一条路，
        // 也与面板的 atAlpha 一致。
        // 早先退回 40/60/80 三档预置底色：档位与面板最多差 20%（最小档 20% 时卡底还画 40%），
        // 「卡底与内层面板透出程度一致」这条就破了；100%（或 alpha ≥ 1）时仍走布局自带的圆角底，
        // 不必白铺一张位图。
        if (color == null && alpha >= 1f) return
        val size = renderSize(context, widthDp, heightDp)
        val bitmap = solid(
            color = withAlpha(color ?: systemCardColor(context), alpha),
            widthPx = size.widthPx,
            heightPx = size.heightPx,
            radiusPx = size.radiusPx
        )
        views.setImageViewBitmap(R.id.widget_bg, bitmap)
        views.setViewVisibility(R.id.widget_bg, View.VISIBLE)
        views.setInt(R.id.widget_layout, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
    }

    /** `RemoteViews#setColorStateList` 是 API 31 才有的（下方调用点已按版本分流），这里标清楚。 */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun tintCard(views: RemoteViews, argb: Int) {
        views.setColorStateList(R.id.widget_layout, "setBackgroundTintList", ColorStateList.valueOf(argb))
    }

    /** 默认卡底在当前系统明暗下的颜色（桌面按自己的 uiMode 解析 `widget_card_bg`，这里与它对齐）。 */
    private fun systemCardColor(context: Context): Int =
        WidgetToneContext.of(context, systemToneOf(context)).getColor(R.color.widget_v2_card_bg)

    private fun alphaOf(opacity: Float): Int = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)

    /** 把实例不透明度乘进颜色自带的 alpha 里。 */
    private fun withAlpha(color: Int, opacity: Float): Int =
        (color and 0x00FFFFFF) or (alphaOf(opacity * (Color.alpha(color) / 255f)) shl 24)

    /** 背景图/背景色的代表色：图片取平均（跳过全透明像素），够用来判明暗。 */
    private fun averageColor(bitmap: Bitmap): Int {
        var count = 0L
        var r = 0L
        var g = 0L
        var b = 0L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 8) {
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }
                x += AVERAGE_STEP
            }
            y += AVERAGE_STEP
        }
        if (count == 0L) return Color.WHITE
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    /** 把任意比例的图片按 centerCrop 铺满目标框，并裁成与卡片一致的圆角。 */
    private fun cropIntoRounded(
        source: Bitmap,
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val clip = Path().apply {
            addRoundRect(
                RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
                radiusPx, radiusPx, Path.Direction.CW
            )
        }
        canvas.clipPath(clip)
        val scale = max(widthPx.toFloat() / source.width, heightPx.toFloat() / source.height)
        val drawnWidth = source.width * scale
        val drawnHeight = source.height * scale
        val left = (widthPx - drawnWidth) / 2f
        val top = (heightPx - drawnHeight) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + drawnWidth, top + drawnHeight), paint)
        return output
    }

    private fun solid(color: Int, widthPx: Int, heightPx: Int, radiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radiusPx, radiusPx, paint
        )
        return output
    }
}
