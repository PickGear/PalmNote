package com.palmnote.ui.settings

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.ui.components.COMPACT_DRAG_HANDLE_PADDING
import com.palmnote.ui.components.DEFAULT_DRAG_HANDLE_PADDING
import com.palmnote.ui.components.SheetDragHandle
import com.palmnote.ui.theme.BottomSheetShape
import com.palmnote.ui.theme.PalmNoteTheme
import com.palmnote.ui.widget.BillWidgetProvider
import com.palmnote.ui.widget.TodoWidgetProvider
import com.palmnote.ui.widget.WidgetPin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 出图用的诊断类，不是断言测试：把添加页画成 PNG 落 `app/build/reports/` 供目视校验。
 *
 * 底部面板是 `ModalBottomSheet`（独立窗口），`decorView.draw()` 抓不到；而
 * `captureToImage()` 在 Robolectric 下会超时。所以面板单独把内容件裁成一个悬浮层来画。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class WidgetPinLayoutDumpTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val out = File("build/reports/pin-$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("DIAG wrote ${out.absolutePath}")
    }

    @Test
    fun `画廊首屏`() {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        capture("top")
    }

    @Test
    fun `画廊首屏_带已在桌面角标`() {
        val manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).setRequestPinAppWidgetSupported(true)
        // 账单 / 待办已在桌面：首屏两张卡右上角应出现「已在桌面」小勾，右端圆钮换成「编辑」
        shadowOf(manager).bindAppWidgetId(1, ComponentName(context, BillWidgetProvider::class.java))
        shadowOf(manager).bindAppWidgetId(2, ComponentName(context, TodoWidgetProvider::class.java))
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        capture("top-added")
    }

    @Test
    fun `画廊滚到记账段`() {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        compose.onNodeWithTag(CATALOG_LIST_TAG)
            .performScrollToNode(hasText(context.getString(R.string.widget_subscription_title_short)))
        capture("billing")
    }

    /** 画廊滚到底：核对末尾几张卡（稿 §5 之外的尾部次序）。 */
    @Test
    fun `画廊末尾`() {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        compose.onNodeWithTag(CATALOG_LIST_TAG)
            .performScrollToNode(hasText(context.getString(R.string.widget_next_up_title_short)))
        capture("tail")
    }

    @Test
    fun `底部面板_多尺寸`() {
        compose.setContent { PalmNoteTheme { SheetMock(R.string.widget_todo_title, bound = 2) } }
        capture("sheet")
    }

    /** 把手留白按 M3 默认（48dp 槽）的对照版：用来比「贴顶把手 vs M3 默认」对顶栏 / 标题位置的影响。 */
    @Test
    fun `底部面板_把手按M3默认`() {
        compose.setContent {
            PalmNoteTheme {
                SheetMock(
                    R.string.widget_todo_title,
                    bound = 2,
                    dragHandlePadding = DEFAULT_DRAG_HANDLE_PADDING
                )
            }
        }
        capture("sheet-m3handle")
    }

    @Test
    fun `底部面板_单样式`() {
        compose.setContent { PalmNoteTheme { SheetMock(R.string.widget_asset_title, bound = 0) } }
        capture("sheet-one")
    }

    /** 本月记账：一个组件里三套真样式（数据版 / 柱状版 / 预算版），弹窗里靠圆点与左右滑切换。 */
    @Test
    fun `底部面板_本月记账三样式`() {
        compose.setContent { PalmNoteTheme { SheetMock(R.string.widget_bill_title, bound = 0) } }
        capture("sheet-bill")
    }

    /** 本组最大只有 1×1 / 4×1 的组件（快捷入口）：页高按全局 4×3 算，圆点行应与多尺寸那版落在同一行。 */
    @Test
    fun `底部面板_矮尺寸组`() {
        compose.setContent { PalmNoteTheme { SheetMock(R.string.widget_shortcuts_title, bound = 0) } }
        capture("sheet-short")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w393dp-h852dp-night-xxhdpi")
    fun `画廊首屏_深色`() {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        capture("top-night")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w393dp-h852dp-night-xxhdpi")
    fun `底部面板_深色`() {
        compose.setContent { PalmNoteTheme { SheetMock(R.string.widget_todo_title, bound = 2) } }
        capture("sheet-night")
    }

    @Test
    @Config(qualifiers = "en-rUS-w393dp-h852dp-xxhdpi")
    fun `底部面板_英文`() {
        compose.setContent { PalmNoteTheme { SheetMock(R.string.widget_todo_title, bound = 2) } }
        capture("sheet-en")
    }

    /**
     * 把面板内容摆成一个贴底浮层：形状、**把手**、内边距、**高度**都照抄 `AppBottomSheet`，出图才和真机一致。
     * 高度必须显式给（真机上由 `AppBottomSheet` 的 heightFraction 定住）：面板内容用 weight
     * 分配余量，高度不定的话预览拿到 0 高度、整张图会空掉。
     *
     * 把手直接用 `AppBottomSheet` 里那个 `SheetDragHandle`（同源，不会画歪）：
     * 少了它，面板内容会整体上移一个槽高，顶栏与标题的位置就和真机对不上、没法跟竞品逐行比。
     */
    @Composable
    private fun SheetMock(
        titleRes: Int,
        bound: Int,
        dragHandlePadding: Dp = COMPACT_DRAG_HANDLE_PADDING
    ) {
        val component = WidgetPin.catalog()
            .flatMap { it.components }
            .first { it.titleRes == titleRes }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = BottomSheetShape,
                color = MaterialTheme.colorScheme.surface,
                // 0.92 = WidgetPinScreen 的 SHEET_HEIGHT_FRACTION（真机上相对可用高度）
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)
            ) {
                Column(Modifier.fillMaxSize()) {
                    SheetDragHandle(dragHandlePadding)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        WidgetPinSheetContent(
                            component = component,
                            selected = WidgetPin.defaultStyleEntry(component),
                            bound = bound,
                            pinSupported = true,
                            verifying = false,
                            opacity = null,
                            background = null,
                            onBack = {},
                            onSelectStyle = {},
                            onSelectOpacity = {},
                            onSelectBackground = {},
                            onAdd = {}
                        )
                    }
                }
            }
        }
    }
}
