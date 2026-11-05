package com.palmnote.ui.dashboard

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.palmnote.data.db.entity.Budget
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.model.BudgetScope
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 预算预警卡四态（超支 / 达阈值 / 隐私超支 / 隐私充足）的渲染钉。
 *
 * 2026-11-06 定高打磨前：隐私态少画进度条行、行一标签行框（16sp）与主数字
 * （20sp）不同高，卡片高度随状态跳变；英文长标签 + 主数字还在 softWrap=false
 * 下被 Budget 胶囊硬裁。这里钉住两条卡面契约：
 * 1. **四态卡高完全一致**（含隐私态与超长标签态）；
 * 2. 主数字永远完整落在 Budget 胶囊左侧，不被遮挡。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// qualifiers=zh：断言的是资源文案（预算管理 / 金额与比例已隐藏），默认 en-US 会解析到 values-en
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class BudgetAlertCardRenderTest {

    @get:Rule
    val compose = createComposeRule()

    /** 额度 ¥3,000；[spentCents] 分。长标签态走 BOOK 维度挂一个超长账本名。 */
    private fun progress(spentCents: Long, longBookName: String? = null): BudgetProgress {
        val budget = if (longBookName == null) {
            Budget(totalBudget = 300_000L)
        } else {
            Budget(scope = BudgetScope.BOOK.name, bookId = 1L, totalBudget = 300_000L)
        }
        return BudgetProgress(budget, spentCents, windowStart = 0L, windowEnd = 0L)
    }

    private fun cardState(
        progress: BudgetProgress,
        bookNames: Map<Long, String> = emptyMap()
    ): DashboardState = DashboardState(
        budgetStatus = progress,
        bookNames = bookNames,
        budgetReminderEnabled = true
    )

    @Test
    fun `四种状态与长标签卡高完全一致`() {
        val heights = mutableMapOf<String, Int>()
        compose.setContent {
            PalmNoteTheme {
                Column {
                    // 超支：¥22,607 / ¥3,000 → 主数字 ¥2.0万；达阈值：87%；
                    // 两种隐私态；长标签：BOOK 维度挂超长账本名（标签必触发省略）
                    val cases = listOf(
                        "over" to cardState(progress(2_260_700L)),
                        "threshold" to cardState(progress(261_000L)),
                        "hiddenOver" to cardState(progress(2_260_700L)),
                        "hiddenNormal" to cardState(progress(261_000L)),
                        "longLabel" to cardState(
                            progress(2_100_000L, longBookName = "一个特别特别特别长的账本名称用来撑爆标签行"),
                            bookNames = mapOf(1L to "一个特别特别特别长的账本名称用来撑爆标签行")
                        )
                    )
                    cases.forEach { (key, state) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { heights[key] = it.size.height }
                        ) {
                            BudgetAlertCard(
                                state = state,
                                amountsHidden = key.startsWith("hidden")
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        // 四态 + 长标签共 5 张卡，高度逐一相等（同一组合内 px 可精确比较）
        val base = heights.getValue("over")
        listOf("threshold", "hiddenOver", "hiddenNormal", "longLabel").forEach { key ->
            assertEquals("「$key」卡高与超支态不一致", base, heights.getValue(key))
        }
        assertTrue("卡高应非零", base > 0)

        // 隐私态不漏数：超支额与金额对比行只出现一次（只在非隐私的超支卡上）
        compose.onAllNodesWithText("¥2.0万", substring = true).fetchSemanticsNodes().let {
            // 数字与标签合在同一个文本节点里（autoSize 整行缩放），按子串匹配
            assertEquals("隐私态泄漏了主数字", 1, it.size)
        }
        compose.onAllNodesWithText("¥22607.00 / ¥3000.00").fetchSemanticsNodes().let {
            assertEquals("隐私态泄漏了金额对比行", 1, it.size)
        }
        // 遮罩说明两张隐私卡各一行
        compose.onAllNodesWithText("金额与比例已隐藏").fetchSemanticsNodes().let {
            assertEquals(2, it.size)
        }
    }

    @Test
    fun `长标签时主数字完整落在胶囊左侧不被遮挡`() {
        compose.setContent {
            PalmNoteTheme {
                BudgetAlertCard(
                    state = cardState(
                        progress(2_100_000L, longBookName = "一个特别特别特别长的账本名称用来撑爆标签行"),
                        bookNames = mapOf(1L to "一个特别特别特别长的账本名称用来撑爆标签行")
                    )
                )
            }
        }
        compose.waitForIdle()

        val numberBounds = compose.onNodeWithText("¥1.8万", substring = true).fetchSemanticsNode().boundsInRoot
        // 单卡组合：唯一的胶囊节点
        val capsuleBounds = compose.onAllNodesWithText("预算管理")
            .fetchSemanticsNodes()
            .single()
            .boundsInRoot
        assertTrue(
            "主数字被胶囊遮挡 number=$numberBounds capsule=$capsuleBounds",
            numberBounds.right <= capsuleBounds.left + 0.5f
        )
    }
}
