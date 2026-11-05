package com.palmnote.feature.vault.vault

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.palmnote.feature.vault.VaultEntry
import com.palmnote.feature.vault.VaultLockManager.LockState
import com.palmnote.ui.theme.PalmNoteTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 密码本详情页 2026-11-06 重设计的渲染钉：hero 横排（头像 + 标题/分类）、
 * 字段行单行紧凑结构（图标 + 标签/值 + 动作按钮）、网站行带显式「打开网站」按钮。
 * 交互与遮罩逻辑不变：密码默认 8 点遮罩、明文绝不因重设计意外出现。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// qualifiers=zh：断言的是资源文案（登录信息 / 复制 / 打开网站），默认 en-US 会解析到 values-en
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class VaultDetailScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val entry = VaultEntry(
        id = 1L,
        title = "git",
        username = "user1",
        email = "git@example.com",
        phone = "13800000000",
        passwordEncrypted = ByteArray(0),
        url = "example.com",
        category = "开发"
    )

    private fun vmOf(entry: VaultEntry): VaultDetailViewModel {
        val vm = mockk<VaultDetailViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(
            VaultDetailUiState(
                lockState = LockState.UNLOCKED,
                requireAuth = false,
                entry = entry,
                isLoading = false
            )
        )
        every { vm.autoLockMode } returns MutableStateFlow("off")
        every { vm.autoLockTimeoutMinutes } returns MutableStateFlow(30)
        every { vm.passwordForDisplay(any()) } returns "secret123"
        return vm
    }

    @Test
    fun `字段行单行结构与打开按钮渲染`() {
        compose.setContent {
            PalmNoteTheme {
                VaultDetailScreen(
                    viewModel = vmOf(entry),
                    onNavigateBack = {},
                    onNavigateToEdit = {}
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("登录信息").assertIsDisplayed()
        compose.onNodeWithText("用户名").assertIsDisplayed()
        compose.onNodeWithText("user1").assertIsDisplayed()
        compose.onNodeWithText("开发").assertIsDisplayed()
        // 站点信息在折叠线以下时先滚入（Robolectric 窗口 320×470）
        compose.onNodeWithText("站点信息").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("example.com").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("打开网站").assertIsDisplayed()
        // 复制按钮：用户名 / 邮箱 / 手机号 / 密码 / 网站 各一 → 共 5 个
        compose.onAllNodesWithContentDescription("复制").fetchSemanticsNodes().let {
            assertEquals("复制按钮应 5 个", 5, it.size)
        }
    }

    @Test
    fun `密码默认遮罩且明文不出现`() {
        compose.setContent {
            PalmNoteTheme {
                VaultDetailScreen(
                    viewModel = vmOf(entry),
                    onNavigateBack = {},
                    onNavigateToEdit = {}
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("••••••••").assertIsDisplayed()
        // 遮罩逻辑不被重设计破坏：明文（passwordForDisplay 的返回值）不得出现在树上
        compose.onAllNodesWithText("secret123").fetchSemanticsNodes().let {
            assertEquals("明文泄漏", 0, it.size)
        }
    }
}
