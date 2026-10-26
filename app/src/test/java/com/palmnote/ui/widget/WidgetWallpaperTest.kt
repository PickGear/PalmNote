package com.palmnote.ui.widget

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 小组件**按实例背景**的渲染决策与存取：
 * - 未配置（null）→ 不接管背景层（默认卡片底色 + 布局里写死的颜色，内容色调为 null）
 * - "#RRGGBB" → 接管；内容色调按一刀切规则（白底深字、其他背景一律浅字，见 [WidgetTonePicker]）
 * - "img:<uri>" → 源图读不出来（失效/换设备）时**自动回退**默认卡底，不留白卡
 * - 不透明度只落在**背景层**：卡底 tint / 位图 alpha，卡上的文字图标保持实色
 * - 写回 null 直接删键，不留脏值
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetWallpaperTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val preferences = PreferencesManager(context, CoroutineScope(Dispatchers.IO))

    private fun views(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_net_worth_unified)

    private fun render(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun alphaOf(opacity: Float): Int =
        (opacity.coerceIn(0f, 1f) * 255f).roundToInt()

    @Test
    fun `未配置背景时不接管背景层`() = runBlocking {
        preferences.setWidgetBackgroundFor(1, null)

        val background = WidgetWallpaper.load(context, preferences, 1, 160, 74)

        assertNull("null = 默认卡片底色，不该接管", background)
        assertNull("没接管背景就不做内容色调改写", WidgetWallpaper.toneOf(context, background))
    }

    @Test
    fun `纯色背景接管渲染且存取一致`() = runBlocking {
        preferences.setWidgetBackgroundFor(1, "#FF0000")

        val background = WidgetWallpaper.load(context, preferences, 1, 160, 74)

        assertNotNull("配置了纯色应接管背景层", background)
        assertEquals(0xFFFF0000.toInt(), background!!.color)
        assertNull("纯色不建位图", background.image)
        assertEquals("#FF0000", preferences.widgetBackgroundFor(1).first())
    }

    @Test
    fun `写回 null 回到默认卡片底色`() = runBlocking {
        preferences.setWidgetBackgroundFor(1, "#FF0000")
        preferences.setWidgetBackgroundFor(1, null)

        assertNull(WidgetWallpaper.load(context, preferences, 1, 160, 74))
        assertNull("写回 null 就该删键，不留脏值", preferences.widgetBackgroundFor(1).first())
    }

    @Test
    fun `图片背景渲染走通且不崩溃`() = runBlocking {
        // Robolectric 对不存在的文件也返回占位位图：这条测的是「图片路径不炸 + 拿到位图」
        preferences.setWidgetBackgroundFor(1, "img:/nonexistent/missing.jpg")

        val background = WidgetWallpaper.load(context, preferences, 1, 160, 74)

        assertNotNull("图片背景应正常接管背景层", background)
        assertNotNull("背景层应是裁好的圆角位图", background!!.image)
        assertNull(background.color)
    }

    @Test
    fun `背景配置按组件实例隔离互不影响`() = runBlocking {
        preferences.setWidgetBackgroundFor(1, "#FF0000")
        preferences.setWidgetBackgroundFor(2, "#00FF00")

        assertEquals(0xFFFF0000.toInt(), WidgetWallpaper.load(context, preferences, 1, 160, 74)?.color)
        assertEquals(0xFF00FF00.toInt(), WidgetWallpaper.load(context, preferences, 2, 160, 74)?.color)
        assertNull(WidgetWallpaper.load(context, preferences, 3, 160, 74))
    }

    // ── 内容色调：背景明暗 → on-color ──

    @Test
    fun `黑底判成深色卡（内容用浅色那套）`() {
        assertEquals(WidgetTone.DARK, toneOf("#000000"))
    }

    @Test
    fun `白底判成浅色卡（内容用深色那套）`() {
        assertEquals(WidgetTone.LIGHT, toneOf("#FFFFFF"))
    }

    @Test
    fun `青底判成深色卡（内容用浅色那套）`() {
        // 主题青 #0891B2：非白底一律浅色字（用户 2026-10-20 定）
        assertEquals(WidgetTone.DARK, toneOf("#0891B2"))
    }

    @Test
    fun `自定义色的色调不随不透明度变`() {
        // 半透明黑在浅壁纸上其实接近浅灰，但用户挑的是黑底 —— 仍按黑底给浅字
        // （色调只看颜色本身；「越低越由壁纸决定」是这类功能的固有近似）
        assertEquals(WidgetTone.DARK, toneOf("#80000000"))
        assertEquals(WidgetTone.LIGHT, toneOf("#33FFFFFF"))
    }

    private fun toneOf(colorHex: String): WidgetTone? = runBlocking {
        preferences.setWidgetBackgroundFor(9, colorHex)
        val background = WidgetWallpaper.load(context, preferences, 9, 160, 74)
        WidgetWallpaper.toneOf(context, background)
    }
    // ── 不透明度只作用在背景层 ──

    @Test
    fun `图片背景的不透明度只打在背景层位图上`() = runBlocking {
        preferences.setWidgetBackgroundFor(1, "img:/nonexistent/missing.jpg")
        val background = WidgetWallpaper.load(context, preferences, 1, 160, 74)
        val views = views()

        WidgetWallpaper.apply(context, views, background, widthDp = 160, heightDp = 74, opacity = 0.4f)

        val root = render(views)
        val bg = root.findViewById<ImageView>(R.id.widget_bg)
        assertEquals(View.VISIBLE, bg.visibility)
        assertNotNull(bg.drawable)
        assertEquals("alpha 打在背景层，卡上的内容不跟着发灰", alphaOf(0.4f), bg.drawable.alpha)
        // 卡底清透明，背景从底下透出
        assertEquals(
            Color.TRANSPARENT,
            (root.findViewById<View>(R.id.widget_layout).background as ColorDrawable).color
        )
    }

    @Test
    fun `31 以下纯色背景也走背景层位图`() = runBlocking {
        // 31 以下没有 ColorStateList 入口：纯色按不透明度裁成圆角位图铺在背景层，卡底清透明
        preferences.setWidgetBackgroundFor(1, "#FF0000")
        val background = WidgetWallpaper.load(context, preferences, 1, 160, 74)
        val views = views()

        WidgetWallpaper.apply(context, views, background, widthDp = 160, heightDp = 74, opacity = 0.4f)

        val root = render(views)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_bg).visibility)
        assertEquals(
            Color.TRANSPARENT,
            (root.findViewById<View>(R.id.widget_layout).background as ColorDrawable).color
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `31 以下默认卡底也按精确 alpha 画成圆角位图，与内层面板一致`() = runBlocking {
        val views = views()

        WidgetWallpaper.apply(context, views, background = null, widthDp = 160, heightDp = 74, opacity = 0.4f)

        val root = render(views)
        val bg = root.findViewById<ImageView>(R.id.widget_bg)
        assertEquals(View.VISIBLE, bg.visibility)
        val bitmap = (bg.drawable as BitmapDrawable).bitmap
        // 卡底的 alpha 精确等于 40%：早先按 40/60/80 三档取整，最小档 20% 时卡底还画 40%，
        // 与内层面板的 atAlpha 差出两倍 —— 这条锁住「不再取整」
        assertEquals(102, Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
        assertEquals(
            Color.TRANSPARENT,
            (root.findViewById<View>(R.id.widget_layout).background as ColorDrawable).color
        )
    }

    @Test
    fun `不透明度 100% 时卡底回到标准圆角底`() = runBlocking {
        preferences.setWidgetBackgroundFor(1, null)
        val views = views()

        WidgetWallpaper.apply(context, views, background = null, widthDp = 160, heightDp = 74, opacity = 1f)

        val card = render(views).findViewById<View>(R.id.widget_layout)
        assertEquals(R.drawable.widget_card_round, shadowOf(card.background).createdFromResId)
    }

    @Test
    fun `从图片背景切回默认卡底时卡底底色会被复位`() = runBlocking {
        preferences.setWidgetBackgroundFor(1, "img:/nonexistent/missing.jpg")
        val views = views()
        // 先按图片背景落一次（卡底被清成透明）
        WidgetWallpaper.apply(context, views, WidgetWallpaper.load(context, preferences, 1, 160, 74), 160, 74, 1f)
        // 再切回默认（用户点了「默认」）：卡底必须复位，不能留一张透明卡
        WidgetWallpaper.apply(context, views, background = null, widthDp = 160, heightDp = 74, opacity = 1f)

        val root = render(views)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_bg).visibility)
        assertEquals(
            R.drawable.widget_card_round,
            shadowOf(root.findViewById<View>(R.id.widget_layout).background).createdFromResId
        )
    }
}
