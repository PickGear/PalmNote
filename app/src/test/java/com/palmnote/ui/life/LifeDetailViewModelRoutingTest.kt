package com.palmnote.ui.life

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.ChecklistRow
import com.palmnote.domain.model.encodeChecklist
import com.palmnote.domain.repository.CrossLinkRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.ui.widget.WidgetUpdateHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 详情页就地写路径的**收口验收**：打卡 / 专注会话 / 清单勾选必须走仓储（事务 + 统计副本双写），
 * 不允许再直连 [LifeItemDao] 绕过双写。双写本身的正确性由 [com.palmnote.data.repository.LifeDataSafetyRoomTest]
 * 在真实 Room 上钉住，这里只钉「路由」。
 *
 * 跑在 Robolectric 上：[WidgetUpdateHelper] 的广播是真实副作用，与其 mock 对象不如给它
 * `init()` 一个 Robolectric 应用上下文（刷新走 shadow 的 AppWidgetManager，查到空 id 列表即空转）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LifeDetailViewModelRoutingTest {

    private val testDispatcher = StandardTestDispatcher()

    private val itemDao = mockk<LifeItemDao>(relaxUnitFun = true)
    private val templateDao = mockk<LifeTemplateDao>(relaxUnitFun = true)
    private val repository = mockk<LifeItemRepository>(relaxUnitFun = true)
    private val preferences = mockk<PreferencesManager>(relaxUnitFun = true)

    /** 本测建过的所有 VM：tearDown 必须逐个取消 viewModelScope（同 LifeTemplateEditViewModelTest）。 */
    private val createdViewModels = mutableListOf<LifeDetailViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        WidgetUpdateHelper.init(InstrumentationRegistry.getInstrumentation().targetContext)
        every { preferences.lifeDemoMode } returns flowOf(false)
        every { preferences.lifeFocusTimerStartAt } returns flowOf(0L)
        every { preferences.lifeFocusTimerAccumMs } returns flowOf(0L)
        every { preferences.lifeFocusTimerItemId } returns flowOf(0L)
        coEvery { repository.insertItem(any()) } returns 100L
        // VM 构造期就会急切调用一次（flatMapLatest 的实参求值）；state 是 WhileSubscribed，
        // 测试里没人订阅，流本体不会被收集，给个空流即可。
        every { itemDao.getItemByIdFlow(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LifeDetailViewModel {
        val vm = LifeDetailViewModel(
            appContext = InstrumentationRegistry.getInstrumentation().targetContext,
            savedStateHandle = SavedStateHandle(mapOf(LifeDetailViewModel.ARG_ITEM_ID to 1L)),
            itemDao = itemDao,
            templateDao = templateDao,
            repository = repository,
            crossLinkRepository = mockk<CrossLinkRepository>(relaxed = true),
            preferences = preferences
        )
        createdViewModels += vm
        return vm
    }

    private fun item(fieldsData: String) = LifeItem(
        templateId = 5L, title = "记录", fieldsData = fieldsData, createdAt = 1_000L
    )

    private fun stubItem(fieldsData: String) {
        coEvery { itemDao.getItemById(1L) } returns item(fieldsData)
        coEvery { templateDao.getTemplateById(5L) } returns mockk<LifeTemplate>(relaxed = true)
    }

    // ── 清单勾选（M4）──

    @Test
    fun `toggleChecklist goes through the repository write path`() = runTest(testDispatcher.scheduler) {
        val encoded = encodeChecklist(listOf(ChecklistRow("a", false), ChecklistRow("b", true)))
        stubItem(JsonObject(mapOf("todo" to JsonPrimitive(encoded))).toString())

        createViewModel().toggleChecklist("todo", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        val expected = JsonObject(
            mapOf("todo" to JsonPrimitive(encodeChecklist(listOf(ChecklistRow("a", true), ChecklistRow("b", true)))))
        ).toString()
        coVerify(exactly = 1) { repository.updateFieldsData(1L, expected) }
        coVerify(exactly = 0) { itemDao.updateFieldsData(any(), any()) }
    }

    // ── 打卡（S2）──

    @Test
    fun `toggleCheckIn inserts through the repository write path`() = runTest(testDispatcher.scheduler) {
        stubItem("{}")
        coEvery { itemDao.getDayItemsOfTemplate(5L, any(), false, any()) } returns emptyList()

        createViewModel().toggleCheckIn()
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<LifeItem>()
        coVerify(exactly = 1) { repository.insertItem(capture(slot)) }
        coVerify(exactly = 0) { itemDao.insertItem(any()) }
        assertEquals(5L, slot.captured.templateId)
        assertNotNull(slot.captured.dueDate)
        assertNull(slot.captured.meta)
    }

    // ── 专注会话（S2）──

    @Test
    fun `saveFocusSession inserts through the repository write path`() = runTest(testDispatcher.scheduler) {
        stubItem("{}")
        every { preferences.lifeFocusTimerItemId } returns flowOf(1L)

        createViewModel().saveFocusSession(60_000L)
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<LifeItem>()
        coVerify(exactly = 1) { repository.insertItem(capture(slot)) }
        coVerify(exactly = 0) { itemDao.insertItem(any()) }
        assertEquals("""{"duration":60000}""", slot.captured.fieldsData)
        assertNull(slot.captured.meta)
    }

    @Test
    fun `saveFocusSession rejects a timer bound to another item`() = runTest(testDispatcher.scheduler) {
        stubItem("{}")
        every { preferences.lifeFocusTimerItemId } returns flowOf(999L)

        createViewModel().saveFocusSession(60_000L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.insertItem(any()) }
        coVerify { preferences.setFocusTimer(0L, 0L, 0L) }
    }

    // ── 防御：清单字段不可解析时不动库 ──

    @Test
    fun `toggleChecklist ignores unparsable data without writing`() = runTest(testDispatcher.scheduler) {
        stubItem("not json")

        createViewModel().toggleChecklist("todo", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateFieldsData(any(), any()) }
        coVerify(exactly = 0) { itemDao.updateFieldsData(any(), any()) }
    }
}
