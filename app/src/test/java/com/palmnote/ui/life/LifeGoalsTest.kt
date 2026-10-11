package com.palmnote.ui.life

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.Goal
import com.palmnote.domain.repository.GoalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 目标（P5d）的验收：进度比例口径、± 快选的方向语义、保存路由与演示标记。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LifeGoalsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<GoalRepository>(relaxUnitFun = true)
    private val preferences = mockk<PreferencesManager>(relaxUnitFun = true)
    private val created = mutableListOf<GoalEditViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { preferences.lifeDemoMode } returns flowOf(false)
        // insertGoal 返回 Long，relaxUnitFun 不覆盖
        coEvery { repository.insertGoal(any()) } returns 1L
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    // ── 进度比例口径 ──

    @Test
    fun `cumulative progress is current over target`() {
        assertEquals(0.5f, goalProgressFraction(Goal(title = "读书", totalCount = 12, currentCount = 6)), 0.001f)
        assertEquals(0f, goalProgressFraction(Goal(title = "读书", totalCount = 0)), 0.001f)
        // 超额完成不溢出
        assertEquals(1f, goalProgressFraction(Goal(title = "读书", totalCount = 12, currentCount = 15)), 0.001f)
    }

    @Test
    fun `decreasing target progress measures from initial toward target`() {
        val quitting = Goal(
            title = "戒烟", goalType = "TARGET", direction = "DECREASE",
            totalCount = 0, currentCount = 8, initialValue = 20
        )
        assertEquals(0.6f, goalProgressFraction(quitting), 0.001f)
    }

    // ── 快选 ± 的方向语义 ──

    @Test
    fun `nudge forward increases cumulative and decreases a decreasing target`() = runTest(testDispatcher.scheduler) {
        val vm = GoalListViewModel(repository, preferences)
        coEvery { repository.getGoalById(1L) } returns Goal(id = 1, title = "读书", totalCount = 12, currentCount = 6)
        coEvery { repository.getGoalById(2L) } returns Goal(
            id = 2, title = "戒烟", goalType = "TARGET", direction = "DECREASE",
            totalCount = 0, currentCount = 8, initialValue = 20
        )

        vm.nudge(Goal(id = 1, title = "读书"), towardGoal = true)
        vm.nudge(Goal(id = 2, title = "戒烟"), towardGoal = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setGoalProgress(1L, 7) }
        coVerify { repository.setGoalProgress(2L, 7) }
    }

    @Test
    fun `nudge clamps to zero and target`() = runTest(testDispatcher.scheduler) {
        val vm = GoalListViewModel(repository, preferences)
        coEvery { repository.getGoalById(1L) } returns Goal(id = 1, title = "读书", totalCount = 12, currentCount = 0)

        vm.nudge(Goal(id = 1, title = "读书"), towardGoal = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setGoalProgress(1L, 0) }
    }

    // ── 保存路由与演示标记 ──

    @Test
    fun `save inserts a new goal with demo flag following demo mode`() = runTest(testDispatcher.scheduler) {
        every { preferences.lifeDemoMode } returns flowOf(true)
        val vm = createEditViewModel(goalId = 0L)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEdit { it.copy(title = "读书", target = 12, unit = "本") }

        var saved = false
        vm.save { saved = true }
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<Goal>()
        coVerify(exactly = 1) { repository.insertGoal(capture(slot)) }
        coVerify(exactly = 0) { repository.updateGoal(any()) }
        assertTrue(slot.captured.isDemo)
        assertEquals("CUMULATIVE", slot.captured.goalType)
    }

    @Test
    fun `save with blank title or zero target is a no-op`() = runTest(testDispatcher.scheduler) {
        val vm = createEditViewModel(goalId = 0L)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEdit { it.copy(title = "  ") }
        vm.save { }
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { repository.insertGoal(any()) }

        vm.onEdit { it.copy(title = "读书", target = 0) }
        vm.save { }
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { repository.insertGoal(any()) }
    }

    @Test
    fun `edit loads the goal and saves through update preserving demo state`() = runTest(testDispatcher.scheduler) {
        val existing = Goal(id = 9, title = "读书", totalCount = 12, currentCount = 5, isDemo = true)
        coEvery { repository.getGoalById(9L) } returns existing

        val vm = createEditViewModel(goalId = 9L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.isEdit)
        assertEquals("读书", vm.state.value.title)

        vm.onEdit { it.copy(title = "读更多书") }
        vm.save { }
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<Goal>()
        coVerify(exactly = 1) { repository.updateGoal(capture(slot)) }
        coVerify(exactly = 0) { repository.insertGoal(any()) }
        assertEquals(9L, slot.captured.id)
        assertEquals("读更多书", slot.captured.title)
        assertEquals("编辑不改演示标记与进度", true, slot.captured.isDemo)
        assertEquals(5, slot.captured.currentCount)
    }

    @Test
    fun `list hides habit rows and applies demo exclusion`() = runTest(testDispatcher.scheduler) {
        val listVm = GoalListViewModel(repository, preferences)
        val rows = listOf(
            Goal(id = 1, title = "累计", goalType = "CUMULATIVE"),
            Goal(id = 2, title = "习惯", goalType = "HABIT"),
            Goal(id = 3, title = "示例", goalType = "CUMULATIVE", isDemo = true)
        )
        coEvery { repository.getAllGoals() } returns flowOf(rows)

        val goals = listVm.goals
        // WhileSubscribed：没人订阅上游不启动，先挂一个收集器
        backgroundScope.launch { goals.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()
        // 演示模式关：只看自己的、且不含 HABIT 行
        assertEquals(listOf(1L), goals.value.map { it.id })
        assertFalse(goals.value.any { it.goalType == "HABIT" })
    }

    private fun createEditViewModel(goalId: Long): GoalEditViewModel {
        val vm = GoalEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("goalId" to goalId)),
            goalRepository = repository,
            preferences = preferences
        )
        created += vm
        return vm
    }
}
