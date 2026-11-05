package com.palmnote.ui.life

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 生活页「新建记录」面板 2026-11-06 重设计的渲染钉：标题升格、分类改分段控件
 * （选中段 = 类别 tint 底）、模板卡图标位圆形化。过滤行为不变：分段点击应真实
 * 过滤网格（原 CategoryTab 的行为契约平移）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// qualifiers=zh：断言的是资源文案（新建记录 / 全部 / 从未），默认 en-US 会解析到 values-en
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class LifeFabSheetRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val templates = listOf(
        tpl(1, "生日", BuiltinTemplates.TIME_CATEGORY),
        tpl(2, "倒计时", BuiltinTemplates.TIME_CATEGORY),
        tpl(3, "待办", BuiltinTemplates.PLAN_CATEGORY),
        tpl(4, "读书笔记", BuiltinTemplates.RECORD_CATEGORY)
    )

    private fun tpl(id: Long, name: String, category: String) = LifeTemplate(
        id = id,
        name = name,
        category = category,
        icon = "cake",
        color = "#E91E63",
        fieldsConfig = "[]",
        layoutType = "card",
        availableLayouts = "[]",
        statusFlowConfig = "[]",
        linkConfig = "[]"
    )

    @Test
    fun `标题分段与模板卡渲染`() {
        compose.setContent {
            PalmNoteTheme {
                FabSheet(
                    templates = templates,
                    lastLogByTemplate = emptyMap(),
                    onDismiss = {},
                    onTemplateClick = {}
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("新建记录").assertIsDisplayed()
        compose.onNodeWithText("搜索模板").assertIsDisplayed()
        // 分段控件四段
        compose.onNodeWithText("全部").assertIsDisplayed()
        compose.onNodeWithText("时间").assertIsDisplayed()
        compose.onNodeWithText("计划").assertIsDisplayed()
        compose.onNodeWithText("记录").assertIsDisplayed()
        // 模板卡：图标位圆形化后的名称与「从未」短文案
        compose.onNodeWithText("生日").assertIsDisplayed()
        compose.onAllNodesWithText("从未").fetchSemanticsNodes().let {
            assertEquals("四张模板卡各一个「从未」", 4, it.size)
        }
    }

    @Test
    fun `分段点击过滤模板网格`() {
        compose.setContent {
            PalmNoteTheme {
                FabSheet(
                    templates = templates,
                    lastLogByTemplate = emptyMap(),
                    onDismiss = {},
                    onTemplateClick = {}
                )
            }
        }
        compose.waitForIdle()
        // 点「计划」段 → 网格只剩计划类模板，时间类的消失
        compose.onNodeWithText("计划").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("待办").assertIsDisplayed()
        compose.onAllNodesWithText("生日").fetchSemanticsNodes().let {
            assertEquals("切到计划分类后时间类模板应被过滤", 0, it.size)
        }
        // 回到「全部」→ 三张都在
        compose.onNodeWithText("全部").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("倒计时").assertIsDisplayed()
    }
}
