package com.palmnote.ui.bills

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import com.palmnote.ui.theme.PalmNoteTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 枢纽页「文件导入」卡直选文件进来（Routes.BillImport.fileUri）的进页即解析行为。
 *
 * 2026-11-06 定案：点卡直接拉起系统选择器，选中才带 Uri 进导入页——导入页不再
 * 停留「选择账单文件」首屏。这里钉住两件事：
 * 1. 带初始 Uri 进入 → 首帧即触发一次 parseFile（不再等用户点「选择账单文件」）；
 * 2. 常规进入（无初始 Uri）→ 不触发，仍从首屏开始。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// qualifiers=zh：页面文案断言依赖 values-zh
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class BillImportInitialUriTest {

    @get:Rule
    val compose = createComposeRule()

    private fun newViewModel(): BillImportViewModel =
        mockk(relaxed = true) {
            every { state } returns MutableStateFlow(BillImportState())
        }

    @Test
    fun `带初始文件Uri进入即触发一次解析`() {
        val vm = newViewModel()
        compose.setContent {
            PalmNoteTheme {
                BillImportScreen(
                    initialFileUri = "content://com.android.providers.downloads.documents/document/1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        compose.waitForIdle()
        verify(exactly = 1) { vm.parseFile(any(), any(), any()) }
    }

    @Test
    fun `常规进入不触发解析`() {
        val vm = newViewModel()
        compose.setContent {
            PalmNoteTheme {
                BillImportScreen(
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        compose.waitForIdle()
        verify(exactly = 0) { vm.parseFile(any(), any(), any()) }
    }
}
