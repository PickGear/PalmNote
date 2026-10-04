package com.palmnote.ui.life

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §14.12(5) 的**行高**用真实渲染器实测。
 *
 * 此前"行高符合规范"是**读 dp 字面量**得出的结论（`RowShell(32.dp)` …）——
 * 那只能证明"代码写了 32"，不能证明"渲染出来是 32"（内边距 / 最小触控 / 字体行高
 * 都可能把它改掉；英雄区就是这么从 110 变成 165 的）。
 *
 * 这里把 `StructureGroupBlock` / `MetricRow` / `TrackBarRow`（已提为 internal）直接渲染并量高度。
 *
 * ## ⚠️ 可信边界：**固定高度的组件可信，靠文字撑高的不可信**
 *
 * 实测发现的分界很清楚：
 * - **与规格精确吻合的全是有固定高度的**：kv `RowShell(32.dp)` = 32.0 ✓、chips 38.0 ✓、
 *   指标卡 `height(Spacing.xxl)` = 48.0 ✓、轨道 `height = 7.dp` = 7.0 ✓ —— 这些可信；
 * - **对不上的全是靠文字自然高度撑开的**：单个 `bodySmall`（12sp，主题行高 16）的 `Text`
 *   竟量出 **35.0dp** —— 这在真机上不可能（主题无密度/字号缩放覆盖，行高 12→16 正常）。
 *
 * ⟹ 判断：Robolectric 的**文字度量与真机不一致**（字体 stub / 回退度量）。因此
 * **文字驱动的数字（bar 行 68、英雄区 165）不能当作真机事实**，只能用于
 * "同一环境内的相对比较"（例如 A/B 两个变体谁更高）。
 *
 * 所以本类只打印、不断言；要判断真机高度，仍需真机或截图。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DetailRowHeightSpecTest {

    @get:Rule
    val compose = createComposeRule()

    private val accent = Color(0xFF3F51B5)

    private fun measure(content: @Composable () -> Unit): Float {
        compose.setContent { PalmNoteTheme { content() } }
        val bounds = compose.onRoot().getBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    @Test
    fun `kv 行高（规格 32）`() {
        val h = measure {
            StructureGroupBlock(
                group = DetailGroup(rows = listOf(DetailRowModel.Kv("品牌", "喜茶"))),
                accent = accent,
                showTitle = false
            )
        }
        println("DIAG rowHeightKv=$h（规格 32）")
    }

    @Test
    fun `bar 行高（规格 34）`() {
        val h = measure {
            StructureGroupBlock(
                group = DetailGroup(rows = listOf(DetailRowModel.Bar("已存", 0.32f, "32%"))),
                accent = accent,
                showTitle = false
            )
        }
        println("DIAG rowHeightBar=$h（规格 34）")
    }

    @Test
    fun `chips 行高（规格 38）`() {
        val h = measure {
            StructureGroupBlock(
                group = DetailGroup(rows = listOf(DetailRowModel.Chips("标签", listOf("A", "B")))),
                accent = accent,
                showTitle = false
            )
        }
        println("DIAG rowHeightChips=$h（规格 38）")
    }

    @Test
    fun `单量一个 bodySmall 的 Text（预期约 16）`() {
        val h = measure {
            androidx.compose.material3.Text(
                "已存",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
        println("DIAG bareBodySmallText=$h（预期约 16）")
    }

    @Test
    fun `单量轨道组件本身（规格 高 7）`() {
        val h = measure { LifeTrackBar(fraction = 0.32f, color = accent, height = 7.dp) }
        println("DIAG trackBarAlone=$h（规格 7）")
    }

    @Test
    fun `bar 行内部 A：只有标签与轨道（无值、无步进器）`() {
        val h = measure {
            TrackBarRow(DetailRowModel.Bar("已存", 0.32f, ""), accent, null, null, null)
        }
        println("DIAG barRowLabelOnly=$h")
    }

    @Test
    fun `bar 行内部 B：加就地编辑的值`() {
        val h = measure {
            TrackBarRow(DetailRowModel.Bar("已存", 0.32f, "32%"), accent, null, null, null)
        }
        println("DIAG barRowWithValue=$h")
    }

    @Test
    fun `bar 行内部 C：再加步进器`() {
        val h = measure {
            TrackBarRow(
                row = DetailRowModel.Bar(
                    "已存",
                    0.32f,
                    "32%",
                    stepper = BarStepper("spent", 500.0, 96_000.0, null, null)
                ),
                accent = accent,
                onSetProgress = { _, _ -> },
                onNudgeProgress = { _, _, _, _ -> },
                config = null
            )
        }
        println("DIAG barRowWithStepper=$h")
    }

    @Test
    fun `指标卡高度（规格 48）`() {
        val h = measure {
            MetricRow(
                listOf(
                    DetailMetric("a", "已存", "¥96,000"),
                    DetailMetric("b", "日均", "¥1,200")
                )
            )
        }
        println("DIAG metricRowHeight=$h（规格 48）")
    }
}
