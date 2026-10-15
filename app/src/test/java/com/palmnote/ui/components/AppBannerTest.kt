package com.palmnote.ui.components

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「示例模式」状态条关闭叉的**离屏渲染**核对：不传 onClose 不渲染叉；
 * 传了就渲染，点叉触发回调（是否记住「本次已关」是壳层内存态，
 * 见 [com.palmnote.ui.navigation.DemoModeViewModel.bannerDismissed]）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class AppBannerTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `不传 onClose 就没有关闭叉`() {
        compose.setContent {
            PalmNoteTheme { AppBanner(text = "示例模式 · 数据为预置示例", accent = Color.Cyan) }
        }

        compose.onNodeWithContentDescription("关闭").assertDoesNotExist()
    }

    @Test
    fun `点关闭叉触发回调`() {
        var closed = false
        compose.setContent {
            PalmNoteTheme {
                AppBanner(
                    text = "示例模式 · 数据为预置示例",
                    accent = Color.Cyan,
                    onClose = { closed = true }
                )
            }
        }

        compose.onNodeWithContentDescription("关闭").performClick()
        assertTrue(closed)
    }
}
