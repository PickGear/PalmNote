package com.palmnote.ui.settings

import android.app.Application
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.palmnote.data.db.entity.LifeRecycleBin
import com.palmnote.ui.theme.PalmNoteTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回收站新增「生活」页签的**离屏渲染**核对：只编译过证明不了页签真挂上了、
 * 行真的渲染出「模板名 - 标题」。带一条站内数据，断言页签可点、行可见。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class RecycleBinLifeTabRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun viewModel() = mockk<RecycleBinViewModel>(relaxed = true) {
        every { state } returns MutableStateFlow(
            RecycleBinState(
                deletedLifeItems = listOf(
                    LifeRecycleBin(
                        id = 7L, originalId = 100L, templateId = 5L,
                        templateName = "打卡", title = "晨跑",
                        fieldsData = "{}", status = "ACTIVE", note = "",
                        sortOrder = 0, isFavorite = false, createdAt = 1L, updatedAt = 1L
                    )
                )
            )
        )
    }

    @Test
    fun `生活页签可点且行渲染出模板名与标题`() {
        compose.setContent {
            PalmNoteTheme { RecycleBinScreen(viewModel = viewModel()) }
        }

        compose.onNodeWithText("生活").performClick()
        compose.onNode(hasText("打卡 - 晨跑")).assertExists()
    }

    @Test
    fun `空站时生活页签显示空态`() {
        compose.setContent {
            PalmNoteTheme { RecycleBinScreen(viewModel = mockk(relaxed = true) {
                every { state } returns MutableStateFlow(RecycleBinState())
            }) }
        }

        compose.onNodeWithText("生活").performClick()
        compose.onNodeWithText("回收站为空").assertExists()
    }
}
