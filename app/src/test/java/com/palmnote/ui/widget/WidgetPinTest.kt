package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.ui.settings.WidgetPinScreen
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 「设置内添加小组件」的目录与页面：
 * - 目录必须覆盖全部 8 个组件（Provider 不重复，标题/描述/预览资源可解析）；
 * - 两个分支都显式锁定（不依赖阴影默认值）：启动器支持 pin → 按钮可用、页脚提示确认框；
 *   不支持 → 按钮禁用、页脚给「从桌面添加」引导。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetPinTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setPinSupported(supported: Boolean) {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(supported)
    }

    @Test
    fun `目录覆盖 8 个组件且资源都存在`() {
        assertEquals(8, WidgetPin.entries.size)
        assertEquals(8, WidgetPin.entries.map { it.providerClass }.distinct().size)
        WidgetPin.entries.forEach { entry ->
            // 引用不存在会直接抛 NotFoundException
            context.getString(entry.titleRes)
            context.getString(entry.descRes)
            context.resources.getResourceEntryName(entry.previewLayoutRes)
        }
    }

    @Test
    fun `启动器不支持时按钮禁用并显示引导文案`() {
        setPinSupported(false)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }

        WidgetPin.entries.forEach { entry ->
            compose.onNodeWithText(context.getString(entry.titleRes)).assertExists()
            compose.onNodeWithText(context.getString(entry.descRes)).assertExists()
        }
        compose.onNodeWithText(context.getString(R.string.widget_pin_unsupported_tip)).assertExists()
        // 8 行都有「添加」按钮且全部禁用
        val buttons = compose.onAllNodesWithText(context.getString(R.string.widget_pin_add)).fetchSemanticsNodes()
        assertEquals(8, buttons.size)
        buttons.forEach { node ->
            assertTrue("pin unsupported 时添加按钮应禁用", node.config.getOrNull(SemanticsProperties.Disabled) != null)
        }
    }

    @Test
    fun `启动器支持时按钮可用并显示确认框提示`() {
        setPinSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }

        compose.onNodeWithText(context.getString(R.string.widget_pin_tip)).assertExists()
        val buttons = compose.onAllNodesWithText(context.getString(R.string.widget_pin_add)).fetchSemanticsNodes()
        assertEquals(8, buttons.size)
        buttons.forEach { node ->
            assertTrue("pin supported 时添加按钮应可用", node.config.getOrNull(SemanticsProperties.Disabled) == null)
        }
    }
}
