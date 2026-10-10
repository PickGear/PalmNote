package com.palmnote.ui.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.AccountBook
import com.palmnote.ui.theme.PalmNoteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 账单组件的「按实例选账本」：
 * - 配置页与桌面之间的结果约定（id 回传是落桌的前提）；
 * - 偏好按 appWidgetId 分开存，默认全账本（无键），清空即删键；
 * - 配置页的选项渲染与选中回传。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class WidgetConfigTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val books = listOf(AccountBook(id = 3, name = "旅行账本", bookType = "TRAVEL"))

    // ── 结果约定 ──

    @Test
    fun `缺 id 的意图不能配置`() {
        assertEquals(WidgetConfigContract.INVALID_ID, WidgetConfigContract.widgetIdOf(null))
        assertEquals(WidgetConfigContract.INVALID_ID, WidgetConfigContract.widgetIdOf(Intent()))
        assertFalse(WidgetConfigContract.canConfigure(WidgetConfigContract.INVALID_ID))
    }

    @Test
    fun `带 id 的意图可以配置`() {
        val intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7)
        assertEquals(7, WidgetConfigContract.widgetIdOf(intent))
        assertTrue(WidgetConfigContract.canConfigure(7))
    }

    @Test
    fun `确认结果原样带回组件 id`() {
        val result = WidgetConfigContract.okResult(7)
        assertEquals(7, result.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    // ── 偏好 ──

    @Test
    fun `账本选择按组件实例分开存且默认全账本`() = runBlocking {
        val preferences = PreferencesManager(context, CoroutineScope(Dispatchers.IO))

        assertNull("未设置时应是全账本", preferences.widgetBook(11).first())

        preferences.setWidgetBook(11, 42L)
        assertEquals(42L, preferences.widgetBook(11).first())
        assertNull("另一个实例不受影响", preferences.widgetBook(12).first())

        // 传 null 表示回到全账本：键要删掉，不能留下 "null" 之类的脏值
        preferences.setWidgetBook(11, null)
        assertNull(preferences.widgetBook(11).first())
    }

    @Test
    fun `单组件透明度默认跟随全局，可覆盖也可恢复`() = runBlocking {
        val preferences = PreferencesManager(context, CoroutineScope(Dispatchers.IO))

        // 没覆盖过 → 跟随全局（默认 1.0）
        assertEquals(1f, preferences.widgetOpacityFor(21).first(), 0.001f)
        assertNull(preferences.widgetOpacityOverride(21).first())

        // 改全局默认 → 没覆盖的实例跟着变
        preferences.setWidgetOpacity(0.6f)
        assertEquals(0.6f, preferences.widgetOpacityFor(21).first(), 0.001f)

        // 覆盖一个实例 → 只有它变，另一个仍跟全局
        preferences.setWidgetOpacityFor(21, 0.4f)
        assertEquals(0.4f, preferences.widgetOpacityFor(21).first(), 0.001f)
        assertEquals(0.6f, preferences.widgetOpacityFor(22).first(), 0.001f)

        // 恢复跟随默认 → 覆盖键删掉，值重新跟全局
        preferences.setWidgetOpacityFor(21, null)
        assertNull(preferences.widgetOpacityOverride(21).first())
        assertEquals(0.6f, preferences.widgetOpacityFor(21).first(), 0.001f)
    }

    // ── 配置页 ──

    @Test
    fun `配置页列出全账本与账本并提供确定`() {
        compose.setContent { TestScreen(onConfirm = {}, onCancel = {}) }

        compose.onNodeWithText(context.getString(R.string.widget_config_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.account_book_all_name)).assertExists()
        // bookType=TRAVEL 走本地化名（该文案在 core 资源里），不显示原始 name
        compose.onNodeWithText(context.getString(com.palmnote.R.string.account_book_travel_name)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_config_confirm)).assertExists()
        // 列表加了透明度卡后变长，页脚那条预算口径要滚下去才可见
        compose.onNodeWithTag(WIDGET_CONFIG_LIST_TAG)
            .performScrollToNode(hasText(context.getString(R.string.widget_config_budget_note)))
        compose.onNodeWithText(context.getString(R.string.widget_config_budget_note)).assertExists()
    }

    @Test
    fun `选中的账本随确定回传`() {
        var confirmed: Long? = -99L
        compose.setContent { TestScreen(onConfirm = { confirmed = it.bookId }, onCancel = {}) }

        compose.onNodeWithText(context.getString(com.palmnote.R.string.account_book_travel_name)).performClick()
        compose.onNodeWithText(context.getString(R.string.widget_config_confirm)).performClick()

        assertEquals(3L, confirmed)
    }

    @Test
    fun `直接确定保持全账本`() {
        var confirmed: Long? = -99L
        compose.setContent { TestScreen(onConfirm = { confirmed = it.bookId }, onCancel = {}) }

        compose.onNodeWithText(context.getString(R.string.widget_config_confirm)).performClick()

        assertNull(confirmed)
    }

    @Test
    fun `默认跟随全局时透明度不回传覆盖`() {
        var override: Float? = -1f
        compose.setContent { TestScreen(onConfirm = { override = it.opacityOverride }, onCancel = {}) }

        compose.onNodeWithText(context.getString(R.string.widget_config_confirm)).performClick()

        assertNull("没动过透明度就不该写覆盖值", override)
    }

    @Test
    fun `点改为自定义后才回传覆盖值`() {
        var override: Float? = null
        compose.setContent { TestScreen(onConfirm = { override = it.opacityOverride }, onCancel = {}) }

        compose.onNodeWithText(context.getString(R.string.widget_config_opacity_customize)).performClick()
        compose.onNodeWithText(context.getString(R.string.widget_config_confirm)).performClick()

        // 全局默认是 1.0，点「改为自定义」即以当前档位作为起点
        assertEquals(1f, override)
    }

    @Test
    fun `返回键走取消`() {
        var cancelled = false
        compose.setContent { TestScreen(onConfirm = {}, onCancel = { cancelled = true }) }

        compose.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()

        assertTrue(cancelled)
    }

    @androidx.compose.runtime.Composable
    private fun TestScreen(
        onConfirm: (WidgetConfigResult) -> Unit,
        onCancel: () -> Unit,
        showBooks: Boolean = true
    ) {
        PalmNoteTheme {
            WidgetConfigScreen(
                showBooks = showBooks,
                booksFlow = flowOf(books),
                selectedBookFlow = flowOf(null),
                opacityOverrideFlow = flowOf(null),
                defaultOpacityFlow = flowOf(PreferencesManager.DEFAULT_WIDGET_OPACITY),
                onConfirm = onConfirm,
                onCancel = onCancel
            )
        }
    }

    /** 除账单外的组件：配置页只有透明度那一段，不该出现账本相关文案。 */
    @Test
    fun `不带账本的配置页不显示账本与预算说明`() {
        compose.setContent { TestScreen(onConfirm = {}, onCancel = {}, showBooks = false) }

        compose.onNodeWithText(context.getString(R.string.widget_config_title_generic)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_config_desc_opacity)).assertExists()
        compose.onNodeWithText(context.getString(R.string.account_book_all_name)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.widget_config_budget_note)).assertDoesNotExist()
        // 透明度那一段对所有组件都在
        compose.onNodeWithText(context.getString(R.string.widget_config_opacity_label)).assertExists()
    }

    /**
     * 已存过的设置必须**回显**出来，而不是显示默认值。
     * 曾经播种写错（首次组合就置 seeded），真实值到达时被跳过 → 显示默认值，
     * 用户直接点「完成」就把已存的账本/透明度清掉了。
     */
    @Test
    fun `已存的账本与透明度会回显，直接完成不会清掉`() {
        var result: WidgetConfigResult? = null
        compose.setContent {
            PalmNoteTheme {
                WidgetConfigScreen(
                    showBooks = true,
                    booksFlow = flowOf(books),
                    selectedBookFlow = flowOf(3L),
                    opacityOverrideFlow = flowOf(0.6f),
                    defaultOpacityFlow = flowOf(PreferencesManager.DEFAULT_WIDGET_OPACITY),
                    onConfirm = { result = it },
                    onCancel = {}
                )
            }
        }

        // 账本回显：旅行（id=3）那一行被选中
        compose.onNodeWithText(context.getString(com.palmnote.R.string.account_book_travel_name)).assertExists()
        // 透明度回显：覆盖态文案 + 60%
        compose.onNodeWithText(context.getString(R.string.widget_config_opacity_only_this)).assertExists()
        compose.onNodeWithText("60%").assertExists()

        compose.onNodeWithText(context.getString(R.string.widget_config_confirm)).performClick()

        assertEquals("账本不该被清成全账本", 3L, result?.bookId)
        assertEquals("透明度覆盖不该被清掉", 0.6f, result?.opacityOverride)
    }
}
