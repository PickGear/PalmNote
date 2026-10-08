package com.palmnote.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.palmnote.ui.theme.PalmNoteTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * **出图用的诊断类，不是断言测试**（同 `AssetFormLayoutDumpTest` / `DetailRowHeightSpecTest`）。
 *
 * 提醒设置页「项太多、看不出区别」这类问题只能看图判断，所以把整页画成 PNG 落 `app/build/reports/`。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class, qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class ReminderSettingsLayoutDumpTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun viewModel(): SettingsViewModel = mockk(relaxed = true) {
        // 全开 = 内容最多的状态，最容易看出乱不乱
        every { state } returns MutableStateFlow(SettingsState())
    }

    private fun dump(name: String, scrollTo: String? = null) {
        compose.setContent {
            PalmNoteTheme { ReminderSettingsScreen(onNavigateBack = {}, viewModel = viewModel()) }
        }
        if (scrollTo != null) {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(scrollTo))
        }
        compose.waitForIdle()

        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val out = File("build/reports/reminder-$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("DIAG wrote ${out.absolutePath}")
    }

    @Test
    fun `顶部`() = dump("top")

    @Test
    fun `底部（提前天数那段）`() = dump("bottom", scrollTo = "提前提醒")

    /**
     * 提前天数弹窗单独出图：弹窗是**独立窗口**（Compose `Dialog`），整页 decorView 画不到它，
     * 得把它自己的 decorView 取出来画。底色先铺一层，否则卡片圆角外全是透明。
     */
    @Test
    fun `提前天数弹窗`() {
        compose.setContent {
            PalmNoteTheme { ReminderSettingsScreen(onNavigateBack = {}, viewModel = viewModel()) }
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("提前提醒"))
        compose.onNodeWithText("提前提醒").performClick()
        compose.waitForIdle()

        val dialogView = ShadowDialog.getLatestDialog()!!.window!!.decorView
        println("DIAG dialog size ${dialogView.width}x${dialogView.height}")
        val bitmap = Bitmap.createBitmap(
            dialogView.width.coerceAtLeast(1),
            dialogView.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#8899AA"))
        dialogView.draw(canvas)
        val out = File("build/reports/reminder-advance-dialog.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("DIAG wrote ${out.absolutePath}")
    }
}
