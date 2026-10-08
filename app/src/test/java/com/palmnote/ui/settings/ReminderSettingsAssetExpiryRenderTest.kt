package com.palmnote.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.palmnote.ui.theme.PalmNoteTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 提醒设置页新增的「物品到期提醒」开关与提前天数，用**离屏渲染**核对。
 *
 * 只编译过证明不了它真的出现在页面上（漏挂 item、放错卡片、被别的行挤掉都可能）。
 * 两行分别滚到再断言：LazyColumn 会把滚出视口的行销毁，一次性断言两行并不可靠。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class ReminderSettingsAssetExpiryRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun viewModel(): SettingsViewModel = mockk(relaxed = true) {
        every { state } returns MutableStateFlow(SettingsState())
    }

    @Test
    fun `提醒页有物品到期提醒开关与提前天数`() {
        compose.setContent {
            PalmNoteTheme { ReminderSettingsScreen(onNavigateBack = {}, viewModel = viewModel()) }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("物品到期提醒"))
        compose.onNodeWithText("物品到期提醒").assertExists()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("提前提醒"))
        compose.onNodeWithText("提前提醒").assertExists()
    }

    @Test
    fun `提前天数可以直接填数字并保存`() {
        val vm: SettingsViewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(SettingsState())
        }
        compose.setContent {
            PalmNoteTheme { ReminderSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("提前提醒"))
        compose.onNodeWithText("提前提醒").performClick()

        // 打开的是可输入框（原来只有 1/2/3/5/7/14 固定档，10 天、30 天填不了）
        compose.onNodeWithText("可填 0 - 365 天；填 0 表示只在到期当天提醒").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("30")
        compose.onNodeWithText("确定").performClick()

        verify { vm.setReminderAdvanceDays(30) }
    }

    @Test
    fun `提前天数填 0 表示只在当天提醒`() {
        val vm: SettingsViewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(SettingsState())
        }
        compose.setContent {
            PalmNoteTheme { ReminderSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("提前提醒"))
        compose.onNodeWithText("提前提醒").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("确定").performClick()

        verify { vm.setReminderAdvanceDays(0) }
    }

    @Test
    fun `提前天数超出范围时确定键不可用`() {
        val vm: SettingsViewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(SettingsState())
        }
        compose.setContent {
            PalmNoteTheme { ReminderSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("提前提醒"))
        compose.onNodeWithText("提前提醒").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("400")

        compose.onNodeWithText("确定").assertIsNotEnabled()
    }

    @Test
    fun `提前天数为 0 时页面不写提前 0 天`() {
        val vm: SettingsViewModel = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(SettingsState(reminderAdvanceDays = 0))
        }
        compose.setContent {
            PalmNoteTheme { ReminderSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("提前提醒"))
        compose.onNodeWithText("仅当天提醒").assertExists()
    }
}
