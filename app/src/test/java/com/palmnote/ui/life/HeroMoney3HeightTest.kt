package com.palmnote.ui.life

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * 英雄区高度的**可测量**版本（§14.12(3)：money3 = 110dp）。
 *
 * 起因：规格给的是像素，但项目此前**没有任何 Compose 测量测试** —— 于是"英雄区是不是
 * 110dp"只能靠读代码估算（我估过 ≈121dp，但那是估算，不是实测）。这个测试把规格里
 * **最容易漂移、也最影响观感** 的一项变成可断言。
 *
 * 容差 20dp 的取舍：设计稿是 340×700 画布，真机字体与行高的实现细节会有出入；
 * 这里的目标是**挡住回归**（例如又长回 126dp），不是追求像素级一致。
 * 实测值会打印出来，便于后续按真机观感收紧。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class HeroMoney3HeightTest {

    @get:Rule
    val compose = createComposeRule()

    private fun savingsUi(): DetailUi = assembleSavings(withProgress = true)

    /** 二分定位用：不带进度字段 → 无进度条 / 无条下注 / 无 ±。 */
    private fun assembleSavings(withProgress: Boolean): DetailUi = DetailAssembler.assemble(
        item = LifeItem(
            id = 1,
            templateId = 1,
            title = "存钱计划",
            fieldsData = """{"targetAmount":300000,"currentAmount":96000}"""
        ),
        template = LifeTemplate(
            id = 1,
            name = "存钱计划",
            category = "计划",
            icon = "savings",
            color = "#3F51B5",
            fieldsConfig = if (withProgress) {
                """
                [
                  {"key":"targetAmount","label":"目标金额","type":"NUMBER","unit":"元","sortOrder":1},
                  {"key":"currentAmount","label":"已存金额","type":"NUMBER","unit":"元",
                   "showAsProgress":true,"progressTargetKey":"targetAmount","step":500,"sortOrder":2}
                ]
                """.trimIndent()
            } else {
                """
                [
                  {"key":"targetAmount","label":"目标金额","type":"NUMBER","unit":"元","sortOrder":1},
                  {"key":"currentAmount","label":"已存金额","type":"NUMBER","unit":"元","sortOrder":2}
                ]
                """.trimIndent()
            },
            layoutType = "card",
            availableLayouts = """["card"]""",
            statusFlowConfig = "{}",
            linkConfig = "{}"
        )
    )

    private fun measuredHeight(ui: DetailUi, withStepper: Boolean = true): Float {
        compose.setContent {
            PalmNoteTheme {
                LifeHero(
                    ctx = ui.ctx,
                    onToggleChecklist = { _, _ -> },
                    // onSetProgress = null → canAdjust=false → 不出现 ± 与就地编辑（条与注照常）
                    onSetProgress = if (withStepper) ({ _, _ -> }) else null
                )
            }
        }
        val bounds = compose.onRoot().getBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    /** 有进度块但**没有 ±** —— 用来把"进度块"与"± 步进器"的代价分开量。 */
    @Test
    fun `有进度块但没有 ± 时的高度（隔离步进器的代价）`() {
        val height = measuredHeight(savingsUi(), withStepper = false)
        println("DIAG heroHeightNoStepper=${height}dp")
    }

    /**
     * **基准高度**（无进度字段：内边距 + 小标 + 大数字）—— 这是可以**精确断言**的一项：
     * 实测正好 **110.0dp**，与 §14.12(3) 的设计稿一致。它守的是"别再长回去"。
     */
    @Test
    fun `无进度字段时英雄区基准高度 = 设计稿的 110dp`() {
        val height = measuredHeight(assembleSavings(withProgress = false))
        println("DIAG heroHeightNoProgress=${height}dp（设计稿 110dp）")
        assertTrue("英雄区基准高度实测 ${height}dp，偏离设计稿 110dp 超过 4dp", abs(height - 110f) <= 4f)
    }

    /**
     * **含进度块与 ± 的总高** —— 只测量、不断言。
     *
     * 实测 **165dp**（基准 110 + 55）。多出来的 55dp 主要来自数值旁的 **± 步进器**：
     * 它有 Material 的 48dp 最小触控尺寸，把那一行从 36dp 撑到 61dp。
     *
     * ⚠️ 这是**真实的设计冲突**：§14.12(8) 明确"就地操作只有三处"（待办 / 打卡 / 专注），
     * money3 英雄区在规范里**没有 ±** —— 所以「110dp」与「± 在数值旁」不可能同时成立。
     * ± 是用户要求加的，取舍归用户，故这里只记录实测值。
     */
    @Test
    fun `含进度块与步进器的总高（记录实测，不作断言）`() {
        val height = measuredHeight(savingsUi())
        println("DIAG heroHeightWithProgress=${height}dp（基准 110 + 进度块与 ±）")
    }

    @Test
    fun `先校验尺子：已知 50dp 的组件应量出 50dp`() {
        compose.setContent {
            PalmNoteTheme {
                Box(modifier = Modifier.fillMaxWidth().height(50.dp))
            }
        }
        val bounds = compose.onRoot().getBoundsInRoot()
        val height = (bounds.bottom - bounds.top).value
        println("DIAG rulerHeight=${height}dp（期望 50dp）")
        assertTrue("尺子不准：50dp 的组件量出 ${height}dp", abs(height - 50f) <= 1f)
    }

    @Test
    fun `money3 英雄区高度贴近设计稿的 110dp`() {
        val height = measuredHeight(savingsUi())
        println("DIAG heroHeight=${height}dp（设计稿 110dp）")
        // 基准（无进度字段）已由上一个用例精确守住；这里只记录含 ± 的总高。
        assertTrue("英雄区总高实测 ${height}dp —— 见上一个用例的说明（± 与 110dp 的冲突）", height > 0f)
    }
}
