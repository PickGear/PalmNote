package com.palmnote.ui.bills

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.palmnote.ui.components.ChoiceDialog
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 选择弹窗必须真的把选项渲染出来。
 *
 * 预算页「分类预算 → 添加」走的正是这个组件，如果它在 Dialog 里高度塌了、
 * 或者列表不渲染，用户看到的就是「点添加没有分类可选」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ChoiceDialogRenderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `有选项时每一项都渲染`() {
        compose.setContent {
            PalmNoteTheme {
                ChoiceDialog(
                    title = "选择分类",
                    options = listOf("餐饮", "交通", "娱乐"),
                    // 预算页传的就是 null：没有当前值，只做「挑一个」入口
                    selected = null,
                    optionLabel = { it },
                    optionIcon = { Icons.Filled.Restaurant },
                    optionTint = { Color(0xFF4285F4) },
                    onSelect = {},
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("选择分类").assertExists()
        compose.onNodeWithText("餐饮").assertIsDisplayed()
        compose.onNodeWithText("交通").assertIsDisplayed()
        compose.onNodeWithText("娱乐").assertIsDisplayed()
    }

    @Test
    fun `有选中值时表头正常且选项仍在`() {
        compose.setContent {
            PalmNoteTheme {
                ChoiceDialog(
                    title = "选择账本",
                    options = listOf("日常", "旅行"),
                    selected = "日常",
                    optionLabel = { it },
                    optionIcon = { Icons.Filled.Restaurant },
                    optionTint = { Color(0xFF9C27B0) },
                    onSelect = {},
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("日常").assertIsDisplayed()
        compose.onNodeWithText("旅行").assertIsDisplayed()
    }
}
