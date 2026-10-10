package com.palmnote.ui.bills

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「如何导出官方账单」教程的**离屏渲染**核对。
 *
 * 钉住两点口径，防止文案回退：① 微信的账单是**经「微信支付」消息下发**（不是邮箱），
 * 支付宝仍走邮箱；② 微信导出的是**表格文件**、支付宝的是**加密压缩包**。
 * 断言用 `assertExists` 而非 `assertIsDisplayed`（Robolectric 的文字度量与窗口尺寸不等于真机）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class BillImportTutorialRenderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `微信步骤写的是微信支付消息下发、支付宝写的是邮箱`() {
        compose.setContent { PalmNoteTheme { ImportTutorialSheet(onDismiss = {}) } }

        compose.onNodeWithText("微信的账单通过「微信支付」消息下发", substring = true).assertExists()
        compose.onNodeWithText("在消息里下载保存到手机", substring = true).assertExists()
        compose.onNodeWithText("填邮箱", substring = true).assertExists()
    }

    @Test
    fun `脚注说明微信是表格文件、支付宝是压缩包`() {
        compose.setContent { PalmNoteTheme { ImportTutorialSheet(onDismiss = {}) } }

        compose.onNodeWithText("表格文件", substring = true).assertExists()
        compose.onNodeWithText("压缩包", substring = true).assertExists()
    }
}
