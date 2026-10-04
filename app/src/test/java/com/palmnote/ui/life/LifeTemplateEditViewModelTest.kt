package com.palmnote.ui.life

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.ReminderSpec
import com.palmnote.domain.repository.LifeTemplateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class LifeTemplateEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `field actions ignore an invalid index`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel()

        viewModel.disableField(0)
        viewModel.deleteField(0)

        assertEquals(emptyList<Any>(), viewModel.state.value.fields)
    }

    @Test
    fun `builtin field with data can only be disabled`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(
            template = builtinTemplate(
                fieldsConfig = """
                    [
                      {"key":"amount","label":"金额","type":"NUMBER","showInCard":true,"showAsProgress":true},
                      {"key":"note","label":"备注","type":"TEXT"}
                    ]
                """.trimIndent()
            ),
            fieldsData = listOf("""{"amount":100}""")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteField(0)
        assertEquals(listOf("amount", "note"), viewModel.state.value.fields.map { it.key })

        viewModel.disableField(0)
        val amount = viewModel.state.value.fields[0]
        assertTrue(amount.disabled)
        assertFalse(amount.showInCard)
        assertFalse(amount.showAsProgress)

        viewModel.deleteField(1)
        val expected = FieldConfig(
            key = "amount",
            label = "金额",
            type = FieldType.NUMBER,
            showAsProgress = false
        ).copy(disabled = true)
        assertEquals(listOf(expected), viewModel.state.value.fields)
    }

    @Test
    fun `custom field with data can still be deleted`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(
            template = builtinTemplate(
                fieldsConfig = """[{"key":"amount","label":"金额","type":"NUMBER"}]"""
            ).copy(isBuiltin = false),
            fieldsData = listOf("""{"amount":100}""")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteField(0)

        assertTrue(viewModel.state.value.fields.isEmpty())
    }

    @Test
    fun `moveFieldTo reorders and renumbers sortOrder`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveFieldTo(0, 2)

        val fields = viewModel.state.value.fields
        assertEquals(listOf("b", "c", "a"), fields.map { it.key })
        assertEquals(listOf(0, 1, 2), fields.map { it.sortOrder })
        assertTrue(viewModel.state.value.dirty)
    }

    @Test
    fun `moveFieldTo ignores out of range and no-op moves`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveFieldTo(-1, 1)
        viewModel.moveFieldTo(0, 9)
        viewModel.moveFieldTo(1, 1)

        assertEquals(listOf("a", "b", "c"), viewModel.state.value.fields.map { it.key })
        assertFalse(viewModel.state.value.dirty)
    }

    @Test
    fun `moveField keeps the delta path consistent with moveFieldTo`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveField(2, -1)

        assertEquals(listOf("a", "c", "b"), viewModel.state.value.fields.map { it.key })
    }

    @Test
    fun `patchField transforms one field and leaves others untouched`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.patchField(1) { it.copy(label = "改名了", unit = "kg") }

        val fields = viewModel.state.value.fields
        assertEquals("改名了", fields[1].label)
        assertEquals("kg", fields[1].unit)
        assertEquals("A", fields[0].label)
        assertEquals("C", fields[2].label)
    }

    @Test
    fun `patchField ignores an invalid index`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.patchField(7) { it.copy(label = "x") }

        assertEquals(listOf("a", "b", "c"), viewModel.state.value.fields.map { it.key })
    }

    @Test
    fun `staged field is added only once on consume`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.stageAddField(FieldType.RATING)

        // 暂存不等于入列：字段库页刚选中、编辑页还没消费
        assertEquals(3, viewModel.state.value.fields.size)
        assertEquals(FieldType.RATING, viewModel.state.value.pendingAddType)

        viewModel.consumePendingAddField()
        assertEquals(4, viewModel.state.value.fields.size)
        assertEquals(FieldType.RATING, viewModel.state.value.fields.last().type)
        assertEquals(null, viewModel.state.value.pendingAddType)

        // 再消费一次不应重复添加
        viewModel.consumePendingAddField()
        assertEquals(4, viewModel.state.value.fields.size)
    }

    @Test
    fun `consumePendingAddField is a no-op when nothing is staged`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.consumePendingAddField()

        assertEquals(3, viewModel.state.value.fields.size)
    }

    @Test
    fun `progress target candidates exclude self, disabled and non numeric fields`() =
        runTest(testDispatcher.scheduler) {
            val viewModel = createViewModel(
                template = builtinTemplate(
                    fieldsConfig = """
                        [
                          {"key":"amount","label":"当前","type":"CURRENCY"},
                          {"key":"goal","label":"目标","type":"CURRENCY"},
                          {"key":"note","label":"备注","type":"TEXT"},
                          {"key":"off","label":"已停用","type":"NUMBER","disabled":true}
                        ]
                    """.trimIndent()
                )
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val candidates = viewModel.progressTargetCandidates("amount").map { it.key }

            assertEquals(listOf("goal"), candidates)
        }

    @Test
    fun `options and default value are exposed by field capability`() {
        val viewModel = createViewModel()

        assertTrue(viewModel.hasOptions(FieldType.SELECT))
        assertTrue(viewModel.hasOptions(FieldType.CHECKLIST))
        assertFalse(viewModel.hasOptions(FieldType.TEXT))

        assertTrue(viewModel.supportsDefaultValue(FieldType.NUMBER))
        // 派生字段零输入，没有默认值可设
        assertFalse(viewModel.supportsDefaultValue(FieldType.FORMULA))
    }

    // ---- 提醒配置（§提醒显式化） ----

    @Test
    fun `setReminderEnabled defaults to countdown on the first date field`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(
            template = builtinTemplate(
                fieldsConfig = """
                    [
                      {"key":"note","label":"备注","type":"TEXT"},
                      {"key":"targetDate","label":"目标日期","type":"DATE"}
                    ]
                """.trimIndent()
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setReminderEnabled(true)

        val reminder = viewModel.state.value.reminder
        assertEquals(ReminderSpec.Kind.COUNTDOWN, reminder?.kind)
        assertEquals("targetDate", reminder?.dateKey)
        assertTrue(viewModel.state.value.dirty)
    }

    @Test
    fun `setReminderEnabled off clears and updateReminder is ignored while off`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setReminderEnabled(true)
        viewModel.setReminderEnabled(false)
        assertEquals(null, viewModel.state.value.reminder)

        viewModel.updateReminder(ReminderSpec(ReminderSpec.Kind.BIRTHDAY, dateKey = "a"))
        assertEquals(null, viewModel.state.value.reminder)
    }

    @Test
    fun `updateReminder patches kind and date key`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(template = threeFieldTemplate())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setReminderEnabled(true)
        viewModel.updateReminder(ReminderSpec(ReminderSpec.Kind.BIRTHDAY, dateKey = "a"))

        val reminder = viewModel.state.value.reminder
        assertEquals(ReminderSpec.Kind.BIRTHDAY, reminder?.kind)
        assertEquals("a", reminder?.dateKey)
    }

    @Test
    fun `loaded template's reminderConfig lands in state`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(
            template = builtinTemplate(fieldsConfig = threeFieldTemplate().fieldsConfig)
                .copy(reminderConfig = ReminderSpec(ReminderSpec.Kind.SUBSCRIPTION).toJson())
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReminderSpec.Kind.SUBSCRIPTION, viewModel.state.value.reminder?.kind)
    }

    @Test
    fun `save persists reminderConfig onto the template row`() = runTest(testDispatcher.scheduler) {
        // save 落库要捕获传给 repo 的行，这里不复用 createViewModel（它把 repo 藏在局部），
        // 单独搭一套同样的桩。
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val itemDao = mockk<LifeItemDao>(relaxed = true)
        coEvery { repo.getTemplateById(1L) } returns builtinTemplate(
            fieldsConfig = """[{"key":"targetDate","label":"目标日期","type":"DATE"}]"""
        )
        every { itemDao.getFieldsDataByTemplate(1L) } returns flowOf(emptyList())
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getString(any()) } returns "label"
        every { appContext.getString(any(), any()) } returns "label"
        val viewModel = LifeTemplateEditViewModel(
            appContext,
            SavedStateHandle(mapOf("templateId" to 1L)),
            repo,
            itemDao,
            LifeDataSeeder(
                mockk<LifeTemplateRepository>(relaxed = true),
                mockk<AppDatabase>(relaxed = true)
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setReminderEnabled(true)
        viewModel.save {}
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = slot<LifeTemplate>()
        coVerify { repo.updateTemplate(capture(saved)) }
        val persisted = ReminderSpec.fromJson(saved.captured.reminderConfig)
        assertEquals(ReminderSpec.Kind.COUNTDOWN, persisted?.kind)
        assertEquals("targetDate", persisted?.dateKey)
    }

    private fun threeFieldTemplate(): LifeTemplate = builtinTemplate(
        fieldsConfig = """
            [
              {"key":"a","label":"A","type":"NUMBER"},
              {"key":"b","label":"B","type":"NUMBER"},
              {"key":"c","label":"C","type":"NUMBER"}
            ]
        """.trimIndent()
    )

    /**
     * 派生字段配不全 = 一定算不出，必须**硬阻断保存**。
     *
     * 此前只有字段行上一行红字：用户仍可先存下一个永远空着的行，
     * 之后在卡片与详情页里它直接不出现，且没有任何地方能解释为什么。
     */
    @Test
    fun `派生字段缺参考会阻断保存`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(
            template = builtinTemplate(
                fieldsConfig = """
                    [
                      {"key":"target","label":"目标","type":"NUMBER"},
                      {"key":"remain","label":"还差","type":"REMAINING"}
                    ]
                """.trimIndent()
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val blocks = viewModel.validate().blocks
            .filterIsInstance<LifeTemplateEditViewModel.Validation.Block.DerivedUnconfigured>()
        assertEquals(listOf("还差"), blocks.map { it.label })
        assertFalse(viewModel.validate().canSave)
    }

    @Test
    fun `参考选好之后不再阻断`() = runTest(testDispatcher.scheduler) {
        val viewModel = createViewModel(
            template = builtinTemplate(
                fieldsConfig = """
                    [
                      {"key":"target","label":"目标","type":"NUMBER"},
                      {"key":"current","label":"当前","type":"NUMBER"},
                      {"key":"remain","label":"还差","type":"REMAINING","options":["target","current"]}
                    ]
                """.trimIndent()
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.validate().canSave)
    }

    /**
     * 分类归一：模板的 `category` 必须是「计划 / 时间 / 记录」之一。
     *
     * 为什么钉这条：生活页只认这三张分类卡，任何自造分类的模板在首页都没有入口
     * （真机反馈「新建自定义模板不生效」的另一半原因）。历史数据里存着自造分类的，
     * 保存时要收敛回来，而不是继续把它写回去。
     */
    @Test
    fun `category is normalized to one of the three`() {
        val vm = createViewModel()
        assertEquals("计划", vm.normalizedCategory("计划", null))
        assertEquals("时间", vm.normalizedCategory("时间", null))
        assertEquals("记录", vm.normalizedCategory("记录", null))
        // 自造分类 → 归到「计划」
        assertEquals("计划", vm.normalizedCategory("理财", null))
        // 空值 → 也归到「计划」（旧实现会写「自定义分类」，那是生活页认不出的值）
        assertEquals("计划", vm.normalizedCategory("", null))
        // 自造分类 + 原模板的分类合法 → 保留原分类（编辑时不因字段为空而改掉分类）
        assertEquals("记录", vm.normalizedCategory("", "记录"))
        assertEquals("记录", vm.normalizedCategory("理财", "记录"))
    }

    private fun createViewModel(
        template: LifeTemplate? = null,
        fieldsData: List<String> = emptyList()
    ): LifeTemplateEditViewModel {
        val repo = mockk<LifeTemplateRepository>(relaxed = true)
        val itemDao = mockk<LifeItemDao>(relaxed = true)
        template?.let { coEvery { repo.getTemplateById(1L) } returns it }
        every { itemDao.getFieldsDataByTemplate(1L) } returns flowOf(fieldsData)
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getString(any()) } returns "label"
        every { appContext.getString(any(), any()) } returns "label"
        return LifeTemplateEditViewModel(
            appContext,
            SavedStateHandle(mapOf("templateId" to 1L)),
            repo,
            itemDao,
            LifeDataSeeder(
                mockk<LifeTemplateRepository>(relaxed = true),
                mockk<AppDatabase>(relaxed = true)
            )
        )
    }

    private fun builtinTemplate(fieldsConfig: String): LifeTemplate = LifeTemplate(
        id = 1L,
        name = "存钱计划",
        category = "目标",
        icon = "savings",
        color = "#3F51B5",
        fieldsConfig = fieldsConfig,
        layoutType = "card",
        availableLayouts = """["card"]""",
        statusFlowConfig = "{}",
        linkConfig = "{}",
        isBuiltin = true
    )
}
