package com.palmnote.ui.widget

import android.app.Application
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.palmnote.app.R
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android 12+ 的 tint 路径：换底色**不换 shape**（圆角留着、不建位图）。
 *
 * 单独一个类是因为 [RemoteViews.setColorStateList] 是 API 31 起才有；
 * 30 那套退回**位图**的行为在 [WidgetToneSweepTest] 与 [WidgetWallpaperTest] 里。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class WidgetTintBackgroundTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun render(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    @Test
    fun `扫色换面板底用 tint，圆角形状留着`() {
        val dark = WidgetToneContext.of(context, WidgetTone.DARK)
        val root = render(widgetViews(dark, R.layout.widget_bill_item))

        assertTrue("tint 不该把 shape 换掉", root.background is GradientDrawable)
        assertEquals(
            WidgetToneSweep.surfaceOverlay(WidgetTone.DARK),
            root.backgroundTintList?.defaultColor
        )
    }

    @Test
    fun `卡片相对的面板覆盖层固定 6%，不随实例不透明度加深`() {
        // 与 WidgetToneSweepTest 的同名场景一致，但走 31+ 的 tint 路径：圆角/形状不该被换掉
        val faded = WidgetToneContext.of(context, WidgetTone.DARK, 0.4f)
        val root = render(widgetViews(faded, R.layout.widget_bill_item))

        assertTrue("tint 不该把 shape 换掉", root.background is GradientDrawable)
        val tint = root.backgroundTintList?.defaultColor ?: 0
        assertEquals(
            (WidgetToneSweep.SURFACE_OVERLAY_ALPHA * 255f).roundToInt(),
            android.graphics.Color.alpha(tint)
        )
        assertEquals("深色卡叠极淡白", 0xFFFFFF, tint and 0x00FFFFFF)
    }

    @Test
    fun `默认卡底的不透明度写进 tint 颜色（不建位图）`() {
        val views = NetWorthWidgetProvider().bindViews(context, 1, 123456L, 200)
        WidgetWallpaper.apply(context, views, null, widthDp = 160, heightDp = 74, opacity = 0.4f)

        val root = render(views)
        val card = root.findViewById<View>(R.id.widget_layout)
        val tint = card.backgroundTintList
        assertNotNull("31+ 应该用 tint 落不透明度", tint)
        val alpha = (0.4f * 255f).roundToInt()
        assertEquals(alpha, android.graphics.Color.alpha(tint!!.defaultColor))
        // 背景层的位图路径没被启用：卡底自己就是半透明的
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_bg).visibility)
        assertTrue(card.background is GradientDrawable)
    }

    @Test
    fun `纯色背景的 alpha 也只落在卡底 tint 上`() {
        val views = NetWorthWidgetProvider().bindViews(context, 1, 123456L, 200)
        WidgetWallpaper.apply(
            context,
            views,
            WidgetWallpaper.Background(color = 0xFF0891B2.toInt(), image = null, toneSource = 0xFF0891B2.toInt()),
            widthDp = 160,
            heightDp = 74,
            opacity = 0.5f
        )

        val tint = render(views).findViewById<View>(R.id.widget_layout).backgroundTintList
        assertNotNull(tint)
        // #0891B2 @ 50% → alpha 128，色相不动
        assertEquals(0xFF0891B2.toInt() and 0x00FFFFFF, tint!!.defaultColor and 0x00FFFFFF)
        assertEquals(128, android.graphics.Color.alpha(tint.defaultColor))
    }

    @Test
    fun `切回默认卡底会把上一轮的自定义色 tint 复位`() {
        // 第一轮：这个实例是青色半透明底
        val root = render(
            NetWorthWidgetProvider().bindViews(context, 1, 123456L, 200).also { views ->
                WidgetWallpaper.apply(
                    context,
                    views,
                    WidgetWallpaper.Background(color = 0xFF0891B2.toInt(), image = null, toneSource = 0xFF0891B2.toInt()),
                    widthDp = 160,
                    heightDp = 74,
                    opacity = 0.5f
                )
            }
        )

        // 第二轮：同一个实例切回默认卡底、100%。桌面复用视图树，且 setBackgroundResource 不会清 tint
        val reset = NetWorthWidgetProvider().bindViews(context, 1, 123456L, 200)
        WidgetWallpaper.apply(context, reset, null, widthDp = 160, heightDp = 74, opacity = 1f)
        reset.reapply(context, root)

        val tint = root.findViewById<View>(R.id.widget_layout).backgroundTintList
        assertNotNull("100% 也必须覆写 tint，否则会一直停在上一轮的半透明青色", tint)
        assertEquals(0xFFFFFFFF.toInt(), tint!!.defaultColor)
        assertTrue(root.findViewById<View>(R.id.widget_layout).background is GradientDrawable)
    }

    @Test
    fun `31+ 底色落在 tint 上时，预览分层判定也认得出这是面板底`() {
        // 预览图分层（内容层 / 面板层）靠 WidgetToneSweep.isSurface 在**渲染后**的视图树上判底色。
        // 31+ 这边底色在 backgroundTintList 上、drawable 自身颜色还是布局里的浅色 ——
        // 判定要把 tint 算进去，否则预览的面板层会是空的（低不透明度下「卡边透了、里面还是实色」）。
        // 卡片相对模式写的是覆盖层（不是令牌色），预览出图走的是默认卡底那条路，所以这里点名测
        // 「默认路径 + 夜间令牌 tint」这一组：出图时真正会遇到的组合。
        val dark = WidgetToneContext.of(context, WidgetTone.DARK)
        val views = RemoteViews(context.packageName, R.layout.widget_bill_item)
        WidgetToneSweep.apply(dark, views, R.layout.widget_bill_item, WidgetTone.DARK, 1f, cardRelative = false)
        val root = render(views)

        assertTrue(WidgetToneSweep.isSurface(root, dark))
        // 卡底自己的 tint 不算内层面板：它是预览单独垫的那一层
        val cardRoot = render(NetWorthWidgetProvider().bindViews(context, 1, 123456L, 200))
        assertFalse("卡底不该被当成内层面板", WidgetToneSweep.isSurface(cardRoot, context))
    }
}
