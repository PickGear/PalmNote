package com.palmnote.ui.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.palmnote.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 内容色调的两块地基：
 * 1. [WidgetToneContext]：把色调编码进 `uiMode`，同一个 bind 里的每一处取色自动走对应明暗的令牌；
 * 2. [WidgetTonePicker]：**白底用深色字、其他任何背景色都用浅色字**（只看颜色，不看不透明度）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetToneTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `默认跟随系统浅色`() {
        assertEquals(WidgetTone.LIGHT, WidgetTone.ofSystem(context))
    }

    @Test
    fun `带色调的 Context 读到对应明暗的令牌`() {
        val day = WidgetToneContext.of(context, WidgetTone.LIGHT)
        val night = WidgetToneContext.of(context, WidgetTone.DARK)

        assertEquals(0xFF15181A.toInt(), day.getColor(R.color.widget_v2_text_primary))
        assertEquals(0xFFECEFF0.toInt(), night.getColor(R.color.widget_v2_text_primary))
        // 未改动的限定符照抄原 Context：语言、密度、屏幕尺寸都还在
        assertEquals(
            context.resources.configuration.locales,
            night.resources.configuration.locales
        )
    }

    @Test
    fun `tone 为 null 时原样返回不做包装`() {
        assertSame(context, WidgetToneContext.of(context, null))
    }

    @Test
    fun `已经带同一色调的 Context 不重复包装`() {
        val night = WidgetToneContext.of(context, WidgetTone.DARK)

        assertSame(night, WidgetToneContext.of(night, WidgetTone.DARK))
        // 换成另一套时要真的换过去，不能留着旧色调
        assertEquals(WidgetTone.LIGHT, WidgetToneContext.of(night, WidgetTone.LIGHT).let { it as WidgetToneContext }.tone)
    }

    @Test
    fun `白底用深色文字`() {
        assertEquals(WidgetTone.LIGHT, WidgetTonePicker.of(context, 0xFFFFFFFF.toInt()))
    }

    @Test
    fun `未接管背景时按默认卡底判明暗`() {
        // 没有自定义背景 = 用户没挑色，内容色交给桌面按系统明暗解析（这里只验证判定是浅色卡）
        assertEquals(WidgetTone.LIGHT, WidgetTonePicker.of(context, null))
    }

    @Test
    fun `其他背景色一律用浅色文字`() {
        // 用户 2026-10-20 定：只有白底例外，黄/橙这类亮底也给浅字（面板里已不再提供手动覆盖）
        listOf(
            0xFF000000.toInt(), // 黑
            0xFF0891B2.toInt(), // 主题青
            0xFF666666.toInt(), // 深灰
            0xFFFFD54F.toInt(), // 黄
            0xFFFF8A3D.toInt() // 橙
        ).forEach { color ->
            assertEquals(
                "背景色 ${Integer.toHexString(color)} 应给浅色文字",
                WidgetTone.DARK,
                WidgetTonePicker.of(context, color)
            )
        }
    }

    @Test
    fun `带 alpha 的背景色按 RGB 判，不看透明度`() {
        // 色调只看用户挑的色：20% 白仍是「白底」，20% 黑仍是「黑底」
        assertEquals(WidgetTone.LIGHT, WidgetTonePicker.of(context, 0x33FFFFFF.toInt()))
        assertEquals(WidgetTone.DARK, WidgetTonePicker.of(context, 0x33000000.toInt()))
    }

    @Test
    fun `近白但不算白的浅色仍用浅色文字`() {
        // 门槛 0.9：黄 `#FFD54F` 相对亮度 0.69、浅灰 `#E0E0E0` 0.75，都落在「其他颜色」那一侧
        assertEquals(WidgetTone.DARK, WidgetTonePicker.of(context, 0xFFFFD54F.toInt()))
        assertEquals(WidgetTone.DARK, WidgetTonePicker.of(context, 0xFFE0E0E0.toInt()))
    }
}
