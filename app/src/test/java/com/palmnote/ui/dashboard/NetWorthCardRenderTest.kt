package com.palmnote.ui.dashboard

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 净资产卡（issue #51）：主数字必须是「资产 − 欠款」，构成行只在存在信用卡钱包时出现。
 *
 * 信用卡欠款此前被 SQL 整类排除，用户给信用卡录欠款后首页纹丝不动——
 * 这里钉住卡面的三个可见行为，防止口径再漂。
 */
@RunWith(RobolectricTestRunner::class)
// qualifiers=zh：断言的是资源文案（资产/负债），默认 en-US 会解析到 values-en
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class NetWorthCardRenderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `有信用卡钱包时主数字为净值且构成行显示`() {
        compose.setContent {
            PalmNoteTheme {
                NetWorthCard(
                    state = DashboardState(
                        totalAssetValue = 500_000,
                        totalLiability = 28_000,
                        hasCreditCardWallet = true,
                        loaded = true
                    ),
                    onNavigateToBill = {}
                )
            }
        }
        // 主数字 = 5000 − 280 = 4720（formatCompact 小金额也保留两位小数）
        compose.onNodeWithText("¥4,720.00").assertIsDisplayed()
        compose.onNodeWithText("资产").assertIsDisplayed()
        compose.onNodeWithText("负债").assertIsDisplayed()
        // 分项金额各自出现
        compose.onNodeWithText("¥5,000.00").assertIsDisplayed()
        compose.onNodeWithText("¥280.00").assertIsDisplayed()
    }

    @Test
    fun `无信用卡钱包时不显示构成行`() {
        compose.setContent {
            PalmNoteTheme {
                NetWorthCard(
                    state = DashboardState(
                        totalAssetValue = 500_000,
                        hasCreditCardWallet = false,
                        loaded = true
                    ),
                    onNavigateToBill = {}
                )
            }
        }
        compose.onNodeWithText("¥5,000.00").assertIsDisplayed()
        compose.onAllNodesWithText("负债").assertCountEquals(0)
        compose.onAllNodesWithText("资产").assertCountEquals(0)
    }

    @Test
    fun `欠款超过资产时主数字为负`() {
        compose.setContent {
            PalmNoteTheme {
                NetWorthCard(
                    state = DashboardState(
                        totalAssetValue = 100_000,
                        totalLiability = 128_000,
                        hasCreditCardWallet = true,
                        loaded = true
                    ),
                    onNavigateToBill = {}
                )
            }
        }
        compose.onNodeWithText("-¥280.00").assertIsDisplayed()
    }

    @Test
    fun `隐私隐藏时主数字与构成行金额一起藏`() {
        compose.setContent {
            PalmNoteTheme {
                NetWorthCard(
                    state = DashboardState(
                        totalAssetValue = 500_000,
                        totalLiability = 28_000,
                        hasCreditCardWallet = true,
                        loaded = true
                    ),
                    onNavigateToBill = {},
                    amountsHidden = true
                )
            }
        }
        // 隐藏的是数不是结构：标签仍在，真实金额不得出现
        compose.onNodeWithText("资产").assertIsDisplayed()
        compose.onNodeWithText("负债").assertIsDisplayed()
        compose.onAllNodesWithText("¥4,720.00").assertCountEquals(0)
        compose.onAllNodesWithText("¥5,000.00").assertCountEquals(0)
        compose.onAllNodesWithText("¥280.00").assertCountEquals(0)
    }
}
