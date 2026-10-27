package com.palmnote.ui.asset

import android.app.Application
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.palmnote.data.db.entity.Asset
import com.palmnote.ui.components.CategoryItem
import com.palmnote.ui.theme.PalmNoteTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 物品页**整页统一滚动**的离屏渲染核对：汇总卡、筛选行、物品卡是同一个滚动整体，
 * 相互位置固定不产生位移——
 * 1. 首屏：汇总卡与筛选行都在，且首行物品卡不被筛选行遮挡；
 * 2. 滚过汇总卡与筛选行后，二者随内容一起离开语义树（整体滚走，无残留浮层）。
 *
 * ViewModel 用 mockk 顶掉（配方同 [[palmnote-test-device-limits]] 记的离屏渲染做法）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class AssetBoardStickyFilterRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val assets = (1..40).map { i ->
        Asset(id = i.toLong(), name = "测试物品%02d".format(i), category = "数码")
    }

    private fun mockViewModel(): AssetViewModel =
        mockk(relaxed = true) {
            every { state } returns MutableStateFlow(
                AssetState(assets = assets, filteredAssets = assets, heldCount = 20, heldValue = 100_00)
            )
            every { customCategories } returns MutableStateFlow<List<CategoryItem>>(emptyList())
            every { presetCategoryOverrides } returns MutableStateFlow<Map<String, String>>(emptyMap())
        }

    @Test
    fun `首屏有汇总卡与筛选行`() {
        compose.setContent {
            PalmNoteTheme { AssetScreen(viewModel = mockViewModel()) }
        }
        // 「持有中/暂离中」在语义树里有多处命中（汇总卡标题、状态筛选项等），断言存在即可
        compose.onAllNodesWithText("持有中").onFirst().assertExists()
        compose.onAllNodesWithText("暂离中").onFirst().assertExists()
        // 筛选行折叠态显示的「全部」（状态筛选默认值）
        compose.onAllNodesWithText("全部").onFirst().assertExists()
    }

    @Test
    fun `静止时首行物品卡不被筛选行遮挡`() {
        compose.setContent {
            PalmNoteTheme { AssetScreen(viewModel = mockViewModel()) }
        }
        // 等高度测量传导到占位（筛选行落到汇总卡下方、全部按钮离开顶栏附近），
        // 再断言最终几何。若此处超时说明占位机制失效，而非瞬态。
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("全部").onFirst()
                .fetchSemanticsNode().boundsInRoot.top > 100
        }
        val filterNode = compose.onAllNodesWithText("全部").onFirst().fetchSemanticsNode()
        val cardNode = compose.onNodeWithText("测试物品01").fetchSemanticsNode()
        println("DEBUG 全部=${filterNode.boundsInRoot} 物品01=${cardNode.boundsInRoot}")
        val filterBottom = filterNode.boundsInRoot.bottom
        val firstCardTop = cardNode.boundsInRoot.top
        assertTrue(
            "首行物品卡顶边($firstCardTop)不得高于筛选行底边($filterBottom)",
            firstCardTop >= filterBottom
        )
    }

    @Test
    fun `滚过汇总卡后面板收起、筛选行常驻固定`() {
        compose.setContent {
            PalmNoteTheme { AssetScreen(viewModel = mockViewModel()) }
        }
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("测试物品40"))
        compose.onNodeWithText("测试物品40").assertExists()
        // 面板收起是 0 高度裁切（内容仍组合），汇总卡文本节点仍在语义树；
        // 筛选行常驻固定，任何滚动位置都存在
        compose.onAllNodesWithText("全部").onFirst().assertExists()
    }

    @Test
    fun `回顶按钮只滚网格归零、面板保持收起`() {
        compose.setContent {
            PalmNoteTheme { AssetScreen(viewModel = mockViewModel()) }
        }
        compose.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("测试物品40"))
        // 面板收起态：筛选行贴着顶栏（全部按钮 top 很小）。回顶按钮在滚动结束后
        // 600ms 才自动隐藏，须立即点击
        val collapsedTop = compose.onAllNodesWithText("全部").onFirst()
            .fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithContentDescription("返回顶部").performClick()
        compose.mainClock.advanceTimeBy(1_500)
        // 网格归零：第一张物品卡可见
        compose.onNodeWithText("测试物品01").assertExists()
        // 面板保持收起：筛选行仍在顶部，没有弹开下移
        val topAfter = compose.onAllNodesWithText("全部").onFirst()
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "回顶后面板应保持收起：筛选行 top 收起=$collapsedTop 回顶后=$topAfter",
            topAfter < collapsedTop + 100
        )
    }
}
