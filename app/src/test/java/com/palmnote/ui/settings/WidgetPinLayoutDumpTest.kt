package com.palmnote.ui.settings

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
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
        // 账单 / 待办已在桌面：首屏两张卡右上角应出现「已在桌面」小勾（底行换成实例数）
        shadowOf(manager).bindAppWidgetId(1, ComponentName(context, BillWidgetProvider::class.java))
        shadowOf(manager).bindAppWidgetId(2, ComponentName(context, TodoWidgetProvider::class.java))
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        capture("top-added")
    }

    @Test
    fun `搜索记账`() {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        compose.onNodeWithContentDescription(context.getString(R.string.search)).performClick()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput(context.getString(R.string.widget_group_billing))
        capture("search")
    }

    @Test
    fun `搜索无结果`() {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        compose.onNodeWithContentDescription(context.getString(R.string.search)).performClick()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("zzz")
        capture("empty")
    }

    @Test
    fun `底部面板_多尺寸`() {
        compose.setContent { PalmNoteTheme { SheetMock(R.string.widget_todo_title, bound = 2) } }
        capture("sheet")
    }

    @Test
    fun `底部面板_单尺寸`() {
        compose.setContent { PalmNoteTheme { SheetMock(R.string.widget_bill_title, bound = 0) } }
        capture("sheet-one")
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
     * 把面板内容摆成一个贴底浮层：形状、内边距、**高度**都照抄 `AppBottomSheet`，出图才和真机一致。
     * 高度必须显式给（真机上由 `AppBottomSheet` 的 heightFraction 定住）：面板内容用 weight
     * 分配余量，高度不定的话预览拿到 0 高度、整张图会空掉。
     */
    @Composable
    private fun SheetMock(titleRes: Int, bound: Int) {
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier
                        // 0.92 = WidgetPinScreen 的 SHEET_HEIGHT_FRACTION（真机上相对可用高度）
                        .fillMaxHeight(0.92f)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    WidgetPinSheetContent(
                        component = component,
                        selected = component.entries.first(),
                        bound = bound,
                        pinSupported = true,
                        verifying = false,
                        opacity = null,
                        onSelectSize = {},
                        onSelectOpacity = {},
                        onAdd = {}
                    )
                }
            }
        }
    }
}
