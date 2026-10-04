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

    private fun createEngine(scope: kotlinx.coroutines.CoroutineScope) = TriggerEngine(context, itemRepoProvider, crossLinkRepo, scope)

    @Test
    fun `deposit made meeting target updates status to completed`() = runTest {
        coEvery { itemRepo.updateStatus(any(), any()) } just runs
        val item = lifeItem(fieldsData = """{"targetAmount":"10000","currentAmount":"10000"}""")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify { itemRepo.updateStatus(1, "COMPLETED") }
    }

    @Test
    fun `deposit made meeting target completes without the phantom asset link`() = runTest {
        coEvery { itemRepo.updateStatus(any(), any()) } just runs
        coEvery { crossLinkRepo.createLink(any()) } returns 1L
        val item = lifeItem(fieldsData = """{"targetAmount":"10000","currentAmount":"10000"}""")
        createEngine(this).evaluate(TriggerEvent.DEPOSIT_MADE, item)
        advanceUntilIdle()
        coVerify { itemRepo.updateStatus(1, "COMPLETED") }
        // 这条规则**不该**建关联。旧实现里那条 `CreateAutoLink(ASSET, targetId = item.id)`
        // 拿「条目 id」当「资产 id」，会造出指向**不存在资产**的幽灵关联（详情页关联计数凭空 +1）；
        // 而引擎的自关联守卫只拦 `EntityType.ITEM`，拦不住它。
        // 旧测试恰好用 item#1 / asset#1 的同号数据把这个 bug 固化了下来，现已改为断言正确行为。
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
    fun `item created has no rule left (was a no-op)`() = runTest {
        coEvery { crossLinkRepo.createLink(any()) } returns 1L
        val item = lifeItem()
        createEngine(this).evaluate(TriggerEvent.ITEM_CREATED, item)
        advanceUntilIdle()
        // 该规则原本就是空转（引擎对 ITEM 自关联有守卫），已于 2026-10-01 删除。
        // 这里守住"删干净了"：即使事件被接上，也不会有任何动作。
        coVerify(exactly = 0) { crossLinkRepo.createLink(any()) }
        coVerify(exactly = 0) { itemRepo.updateStatus(any(), any()) }
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
