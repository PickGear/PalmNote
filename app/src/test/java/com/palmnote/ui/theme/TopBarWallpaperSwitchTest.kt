package com.palmnote.ui.theme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.palmnote.ui.components.CompactTopAppBar
import com.palmnote.ui.components.SecondaryTopAppBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 换壁纸那一刻，顶栏（含状态栏区域）**不能闪一下暗色**。
 *
 * 成因：M3 的 `TopAppBar` 内部对 `containerColor` 做无条件的 `animateColorAsState`。
 * 有壁纸时主题的 `background` 是 `Color.Transparent`（= 全透明**黑**），从 `BackgroundLight`
 * spring 过去时，插值会经过「低亮度 + 半透明」的中间色 —— 顶栏（屏幕上最大的一块背景色区域）
 * 就在换壁纸后压过一层半透明灰。页面其余部分用的是 `Modifier.background`，立即切换，
 * 所以只有顶栏这一条会闪。
 *
 * 判据不依赖动画曲线：**颜色第一次变化的那个渲染帧就必须已经是终点色**。
 * 实测未修复时依次是 `#E2E0DE → #B4B3B0 → #838180 → …`，即测试会红。
 * 注：`captureToImage()` 在 Robolectric 下会超时，这里沿用仓库里 `decorView.draw(Canvas)` 的做法。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class TopBarWallpaperSwitchTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `CompactTopAppBar 换壁纸时底色立即切换`() = assertSwitchesInstantly {
        CompactTopAppBar(title = "外观与个性化")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `SecondaryTopAppBar 换壁纸时底色立即切换`() = assertSwitchesInstantly {
        SecondaryTopAppBar(
            title = "外观与个性化",
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
    }

    /**
     * [bar] 铺在深色底（充当深色壁纸）上，切到壁纸 / 关掉壁纸各推一帧，
     * 断言顶栏第一帧变色就等于终点色。
     */
    private fun assertSwitchesInstantly(bar: @Composable () -> Unit) {
        var style by mutableStateOf("none")
        compose.setContent {
            PalmNoteTheme(darkTheme = false, wallpaperStyle = style) {
                Box(Modifier.fillMaxSize().background(WallpaperColor)) {
                    bar()
                }
            }
        }
        compose.waitForIdle()

        val wallpaperPixel = backdropPixel()
        assertEquals("无壁纸时顶栏应是不透明的浅底色", BackgroundLight.toArgb(), topBarPixel())
        compose.mainClock.autoAdvance = false

        // 无壁纸 → 壁纸：第一帧变色就必须露出壁纸，不能先压一层灰
        compose.runOnUiThread { style = "color" }
        assertEquals(
            "切到壁纸后顶栏的第一帧变色就该是壁纸色；先出现灰（或浅色）说明还在 spring 动画途中",
            wallpaperPixel,
            firstPixelAfterChange(from = BackgroundLight.toArgb())
        )

        // 壁纸 → 无壁纸：同理，第一帧就必须回到不透明底色
        compose.runOnUiThread { style = "none" }
        assertEquals(
            "关掉壁纸后顶栏第一帧就该是不透明底色，而不是从透明渐亮（那同样是闪）",
            BackgroundLight.toArgb(),
            firstPixelAfterChange(from = wallpaperPixel)
        )
    }

    /** 逐帧推进，返回顶栏像素**第一次**偏离 [from] 时的颜色。 */
    private fun firstPixelAfterChange(from: Int): Int {
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            val pixel = topBarPixel()
            if (pixel != from) return pixel
        }
        throw AssertionError("顶栏颜色在 30 帧内完全没变，说明主题切换没生效")
    }

    /** 顶栏右侧一点（避开标题文字）的像素。 */
    private fun topBarPixel(): Int = pixelAt(y = 40)

    /** 顶栏下方、模拟壁纸那一层的像素。 */
    private fun backdropPixel(): Int = pixelAt(y = compose.activity.window.decorView.height - 40)

    private fun pixelAt(y: Int): Int {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap.getPixel(bitmap.width - 20, y)
    }

    private companion object {
        /** 故意用深色：修复前从浅底色到透明的中间灰在深色底上最显眼。 */
        val WallpaperColor = Color(0xFF101010)
    }
}
