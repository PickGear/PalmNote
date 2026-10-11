package com.palmnote.ui.life

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.palmnote.data.db.entity.Goal
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
 * 目标两页（P5d）的**离屏渲染**核对：列表卡渲染标题与进度文案、空态、编辑表单字段齐全。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class GoalScreensRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun listViewModel(goals: List<Goal>) = mockk<GoalListViewModel>(relaxed = true) {
        every { this@mockk.goals } returns MutableStateFlow(goals)
    }

    @Test
    fun `goal list renders title and progress`() {
        compose.setContent {
            PalmNoteTheme {
                GoalListScreen(
                    onBack = {}, onEditGoal = {}, onCreateGoal = {},
                    viewModel = listViewModel(
                        listOf(Goal(id = 1, title = "今年读 12 本书", totalCount = 12, currentCount = 5, unit = "本"))
                    )
                )
            }
        }

        compose.onNodeWithText("今年读 12 本书").assertExists()
        compose.onNodeWithText("5 / 12 本").assertExists()
    }

    @Test
    fun `goal list empty state shows when no goals`() {
        compose.setContent {
            PalmNoteTheme {
                GoalListScreen(onBack = {}, onEditGoal = {}, onCreateGoal = {}, viewModel = listViewModel(emptyList()))
            }
        }

        compose.onNodeWithText("还没有目标").assertExists()
    }

    @Test
    fun `edit form renders type chips and fields`() {
        val vm = mockk<GoalEditViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(
                GoalEditViewModel.UiState(title = "", loaded = true)
            )
        }
        compose.setContent {
            PalmNoteTheme { GoalEditScreen(onBack = {}, viewModel = vm) }
        }

        compose.onNodeWithText("新建目标").assertExists()
        compose.onNodeWithText("名称").assertExists()
        // 「目标值」同时是类型 chip 与输入框标签，用 all-nodes 断言存在即可
        compose.onAllNodesWithText("目标值")[0].assertExists()
        compose.onAllNodesWithText("累计")[0].assertExists()
    }
}
