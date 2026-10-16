package com.palmnote.ui.settings

import android.app.Application
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.ui.theme.PalmNoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 出图用的诊断类，不是断言测试：把添加页整页画成 PNG 落 app/build/reports/ 供目视校验。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class WidgetPinLayoutDumpTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun dump(name: String, scrollTo: String? = null, search: String? = null) {
        shadowOf(AppWidgetManager.getInstance(context)).setRequestPinAppWidgetSupported(true)
        compose.setContent { PalmNoteTheme { WidgetPinScreen() } }
        if (search != null) compose.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput(search)
        if (scrollTo != null) {
            compose.onNodeWithTag(CATALOG_LIST_TAG).performScrollToNode(hasText(scrollTo))
        }
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
    fun `顶部`() = dump("top")

    @Test
    fun `习惯多尺寸`() = dump("habit", scrollTo = "今日打卡")

    @Test
    fun `搜索记账`() = dump("search", search = "记账")
}
