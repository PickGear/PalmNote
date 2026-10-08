package com.palmnote.ui.theme

import android.app.Application
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 弹窗 / 弹层的面色核对。
 *
 * M3 的 `surfaceContainer*` 系列默认留在**基线调色板**上（浅 #ECE6F0、深 #2B2930，两者都是
 * B > R > G 的**紫调**），而 `AlertDialog`、`ModalBottomSheet`、`DropdownMenu` 的容器色恰好取自这一组。
 * 主题漏掉这组覆盖时，「账单已加密」这类弹窗会整片发紫——本测试钉住它别再漏。
 *
 * [AlertDialogDefaults.containerColor] 就是弹窗容器实际用的颜色，等价于对弹窗本体做像素采样。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class ThemeSurfaceNeutralityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `弹窗容器色是 App 面色而不是 M3 基线紫`() {
        var light: Color? = null
        var dark: Color? = null
        compose.setContent {
            PalmNoteTheme(darkTheme = false) { light = AlertDialogDefaults.containerColor }
            PalmNoteTheme(darkTheme = true) { dark = AlertDialogDefaults.containerColor }
        }
        compose.waitForIdle()

        assertEquals("浅色弹窗应落在 App 面白上", SurfaceLight, requireNotNull(light))
        assertEquals("深色弹窗应落在 App 面灰上", SurfaceDark, requireNotNull(dark))
    }

    @Test
    fun `带标题的弹窗能正常渲染出来`() {
        compose.setContent {
            PalmNoteTheme(darkTheme = true) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("账单已加密") },
                    text = { Text("请输入解压密码") },
                    confirmButton = { Text("确定") }
                )
            }
        }
        compose.onNodeWithText("账单已加密").assertIsDisplayed()
    }

    @Test
    fun `surfaceContainer 全组都不带紫调`() {
        val schemes = listOf(
            "light" to ThemePackages.lightScheme(ThemePackages.packages.first().lightPrimary),
            "dark" to ThemePackages.darkScheme(ThemePackages.packages.first().darkPrimary)
        )
        for ((tag, scheme) in schemes) {
            val roles = listOf(
                "Lowest" to scheme.surfaceContainerLowest,
                "Low" to scheme.surfaceContainerLow,
                "Container" to scheme.surfaceContainer,
                "High" to scheme.surfaceContainerHigh,
                "Highest" to scheme.surfaceContainerHighest
            )
            for ((name, color) in roles) {
                // 紫调的判据：蓝通道高于红、绿（M3 基线就是 B > R > G）
                assertTrue(
                    "$tag/surfaceContainer$name 仍偏紫：$color",
                    color.blue <= color.red + TOLERANCE && color.blue <= color.green + TOLERANCE
                )
            }
        }
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
