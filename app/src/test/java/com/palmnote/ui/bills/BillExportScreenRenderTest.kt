package com.palmnote.ui.bills

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
 * 导出账单页 2026-11-06 重设计的渲染钉：三个信息块（摘要 / 时间范围 / 导出内容）
 * 全部进卡。空态是图标锚定的居中说明；规格卡的列名来自导出器真正的表头串
 * （bill_flow_header 按逗号拆列），断言其一保证拆列与导出器不漂移。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// qualifiers=zh：断言的是资源文案，默认 en-US 会解析到 values-en
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class BillExportScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun vmOf(state: BillExportState): BillExportViewModel {
        val vm = mockk<BillExportViewModel>(relaxed = true)
        // 显式 vm.state：every{} 里裸写 state 会被函数参数遮蔽
        every { vm.state } returns MutableStateFlow(state)
        return vm
    }

    @Test
    fun `空态渲染居中说明与规格卡`() {
        compose.setContent {
            PalmNoteTheme {
                BillExportScreen(viewModel = vmOf(BillExportState(loading = false, count = 0)))
            }
        }
        // 页面在 Robolectric 的小窗口（320×470）里纵向滚动，规格卡在折叠线以下：
        // 断言前先滚入视野
        compose.onNodeWithText("暂无账单可导出").assertIsDisplayed()
        compose.onNodeWithText("先在「账单」页记一笔，或换个时间范围再导").assertIsDisplayed()
        compose.onNodeWithText("导出内容").performScrollTo().assertIsDisplayed()
        // 列名标签来自导出器表头串：拆列结果与导出器一致
        compose.onNodeWithText("交易时间").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("时间范围").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `有数据时主数字与覆盖范围进卡`() {
        compose.setContent {
            PalmNoteTheme {
                BillExportScreen(
                    viewModel = vmOf(
                        BillExportState(
                            loading = false,
                            count = 5,
                            bookCount = 2,
                            walletCount = 3
                        )
                    )
                )
            }
        }
        compose.onNodeWithText("共 5 条账单").assertIsDisplayed()
        compose.onNodeWithText("涉及 2 个账本 · 3 个钱包").assertIsDisplayed()
        compose.onNodeWithText("导出内容").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `重新统计时旧摘要淡显而不是整卡换正在统计`() {
        // 2026-11-06 真机反馈「点时间范围会闪」：切范围的重统计保留旧内容淡显，
        // 只有首次进入（initialized = false）才显示「正在统计…」
        compose.setContent {
            PalmNoteTheme {
                BillExportScreen(
                    viewModel = vmOf(
                        BillExportState(
                            loading = true,
                            initialized = true,
                            count = 5,
                            bookCount = 2,
                            walletCount = 3
                        )
                    )
                )
            }
        }
        compose.onNodeWithText("共 5 条账单").assertIsDisplayed()
        compose.onAllNodesWithText("正在统计…").fetchSemanticsNodes().let {
            assertEquals("重统计不该整卡换成「正在统计…」", 0, it.size)
        }
    }
}
