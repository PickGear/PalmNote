package com.palmnote.domain.service

import android.util.Log
import com.palmnote.R
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.repository.CrossLinkRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.ui.notification.NotificationHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class TriggerEngineTest {

    private lateinit var context: android.content.Context
    private lateinit var itemRepo: LifeItemRepository
    private lateinit var crossLinkRepo: CrossLinkRepository
    private lateinit var itemRepoProvider: Provider<LifeItemRepository>

    private fun lifeItem(
        id: Long = 1,
        title: String = "存钱计划",
        fieldsData: String = "{}",
        status: String = "ACTIVE"
    ) = LifeItem(id = id, templateId = 1, title = title, fieldsData = fieldsData, status = status)

    @Before
    fun setup() {
        context = mockk {
            every { getString(R.string.trigger_saving_goal_title) } returns "存款目标达成"
            every { getString(R.string.trigger_saving_goal_message, *anyVararg<Any>()) } returns "恭喜你达成目标"
            every { getString(R.string.trigger_status_updated_title) } returns "状态已更新"
            every { getString(R.string.trigger_status_updated_message, *anyVararg<Any>()) } returns "状态更新"
        }
        itemRepo = mockk()
        crossLinkRepo = mockk()
        itemRepoProvider = mockk { every { get() } returns itemRepo }
        mockkStatic(Log::class)
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        mockkObject(NotificationHelper)
        every { NotificationHelper.show(any(), any(), any(), any()) } just runs
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun createEngine(scope: kotlinx.coroutines.CoroutineScope) =
        TriggerEngine(context, itemRepoProvider, crossLinkRepo, scope)

    @Test
    fun `deposit made meeting target updates status to completed`() = runTest {
        coEvery { itemRepo.updateStatus(any(), any()) } just runs
        val item = lifeItem(fieldsData = """{"targetAmount":"10000","currentAmount":"10000"}""")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify { itemRepo.updateStatus(1, "COMPLETED") }
    }

    @Test
    fun `deposit made meeting target does not create self link`() = runTest {
        coEvery { itemRepo.updateStatus(any(), any()) } just runs
        val item = lifeItem(fieldsData = """{"targetAmount":"10000","currentAmount":"10000"}""")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify(exactly = 0) { crossLinkRepo.createLink(any()) }
    }

    @Test
    fun `deposit made below target does not complete`() = runTest {
        val item = lifeItem(fieldsData = """{"targetAmount":"10000","currentAmount":"5000"}""")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify(exactly = 0) { itemRepo.updateStatus(any(), any()) }
        coVerify(exactly = 0) { crossLinkRepo.createLink(any()) }
    }

    @Test
    fun `deposit made without target data does not complete`() = runTest {
        val item = lifeItem(fieldsData = """{}""")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify(exactly = 0) { itemRepo.updateStatus(any(), any()) }
        coVerify(exactly = 0) { crossLinkRepo.createLink(any()) }
    }

    @Test
    fun `deposit made with saved_amount fallback meets target`() = runTest {
        coEvery { itemRepo.updateStatus(any(), any()) } just runs
        val item = lifeItem(fieldsData = """{"targetAmount":"10000","saved_amount":"12000"}""")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify { itemRepo.updateStatus(1, "COMPLETED") }
    }

    @Test
    fun `deposit made current below zero target does not complete`() = runTest {
        val item = lifeItem(fieldsData = """{"targetAmount":"0","currentAmount":"0"}""")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify(exactly = 0) { itemRepo.updateStatus(any(), any()) }
    }

    @Test
    fun `item status changed to completed shows notification`() = runTest {
        val item = lifeItem(status = "COMPLETED")
        createEngine(this).evaluate(TriggerEvent.ITEM_STATUS_CHANGED, item)
        advanceUntilIdle()
        verify { NotificationHelper.show(any(), "trigger_1", "状态已更新", "状态更新") }
    }
    @Test
    fun `item status changed non completed no action`() = runTest {
        val item = lifeItem(status = "ACTIVE")
        createEngine(this).evaluate(TriggerEvent.ITEM_STATUS_CHANGED, item)
        advanceUntilIdle()
        coVerify(exactly = 0) { itemRepo.updateStatus(any(), any()) }
        coVerify(exactly = 0) { crossLinkRepo.createLink(any()) }
    }

    @Test
    fun `item created rule does not create self link`() = runTest {
        val item = lifeItem()
        createEngine(this).evaluate(TriggerEvent.ITEM_CREATED, item)
        advanceUntilIdle()
        coVerify(exactly = 0) { crossLinkRepo.createLink(any()) }
    }

    @Test
    fun `malformed fields data does not crash`() = runTest {
        val item = lifeItem(fieldsData = "not json")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify(exactly = 0) { itemRepo.updateStatus(any(), any()) }
    }

    @Test
    fun `notification rule uses item title in message`() = runTest {
        val item = lifeItem(title = "我的存款", status = "COMPLETED")
        createEngine(this).evaluate(TriggerEvent.ITEM_STATUS_CHANGED, item)
        advanceUntilIdle()
        verify { context.getString(R.string.trigger_status_updated_message, "我的存款") }
    }
}
