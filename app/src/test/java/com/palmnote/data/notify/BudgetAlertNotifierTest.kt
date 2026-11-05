package com.palmnote.data.notify

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.domain.model.BudgetPeriod
import com.palmnote.domain.model.BudgetProgress
import com.palmnote.domain.repository.AccountBookRepository
import com.palmnote.domain.repository.BudgetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 预算通知链路的**行为契约**测试。
 *
 * 这一层几乎所有失败模式都是「不崩、也不报错，只是悄悄做错」：
 * 演示预算弹真告警、同一期反复弹、回落后再涨回又弹一次、通知栏留着已删预算的告警。
 * 所以这里不测「有没有调用某个函数」，而是直接看**通知栏里实际有几条**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BudgetAlertNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val budgetRepository = mockk<BudgetRepository>()
    private val accountBookRepository = mockk<AccountBookRepository>()
    private val preferences = mockk<PreferencesManager>(relaxed = true)

    /** 模拟 DataStore 里的去重账本：读-改-写都落在这份内存 map 上。 */
    private var notifiedStore = mutableMapOf<String, Int>()
    private var demoMode = false
    private var notifyEnabled = true

    private val notificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 通知栏当前条数 = 真正弹出去的通知数（同 id 就地更新不会增加条数）。 */
    private fun notificationCount(): Int = shadowOf(notificationManager).size()

    @Before
    fun setUp() {
        every { accountBookRepository.getAllBooks() } returns flowOf(emptyList())
        // 通知里的账本名要能落到隐藏账本，实现已改走 IncludingHidden，这里同步铺桩
        every { accountBookRepository.getAllBooksIncludingHidden() } returns flowOf(emptyList())
        every { preferences.lifeDemoMode } answers { flowOf(demoMode) }
        every { preferences.budgetNotifyEnabled } answers { flowOf(notifyEnabled) }
        coEvery { preferences.budgetAlertNotified() } answers { notifiedStore.toMap() }
        coEvery { preferences.setBudgetAlertNotified(any()) } answers {
            notifiedStore = firstArg<Map<String, Int>>().toMutableMap()
        }
    }

    private fun notifier() =
        BudgetAlertNotifier(context, budgetRepository, accountBookRepository, preferences)

    private fun budget(id: Long, limit: Long = 10_000, threshold: Double = 0.8) =
        com.palmnote.data.db.entity.Budget
            .total(BudgetPeriod.MONTH, limit)
            .copy(id = id, reminderThreshold = threshold)

    private fun progress(
        id: Long,
        spent: Long,
        windowStart: Long = 1_000L,
        limit: Long = 10_000
    ) = BudgetProgress(
        budget = budget(id, limit),
        spent = spent,
        windowStart = windowStart,
        windowEnd = windowStart + 100L
    )

    private fun givenProgresses(vararg progresses: BudgetProgress) {
        every { budgetRepository.getProgresses() } returns flowOf(progresses.toList())
    }

    @Test
    fun `演示模式下不发系统通知`() = runBlocking {
        demoMode = true
        // 演示种子是故意做成超支的：不隔离的话新装用户必收一条假告警
        givenProgresses(progress(id = 1, spent = 20_000))

        notifier().checkAndNotify()

        assertEquals(0, notificationCount())
        coVerify(exactly = 0) { preferences.setBudgetAlertNotified(any()) }
    }

    @Test
    fun `关掉预算通知开关后不发`() = runBlocking {
        notifyEnabled = false
        givenProgresses(progress(id = 1, spent = 20_000))

        notifier().checkAndNotify()

        assertEquals(0, notificationCount())
    }

    @Test
    fun `同一期同一级只提醒一次`() = runBlocking {
        givenProgresses(progress(id = 1, spent = 9_000))
        val notifier = notifier()

        notifier.checkAndNotify()
        notifier.checkAndNotify()

        assertEquals("同一条预算同一期不该重复弹", 1, notificationCount())
    }

    @Test
    fun `同一期从达阈值升级到超支会再提醒一次`() = runBlocking {
        val notifier = notifier()

        givenProgresses(progress(id = 1, spent = 9_000))
        notifier.checkAndNotify()
        givenProgresses(progress(id = 1, spent = 12_000))
        notifier.checkAndNotify()

        // 同一个固定 id：就地更新成「已超支」，条数仍是 1，但确实又提醒了一次
        assertEquals(1, notificationCount())
        assertEquals(2, notifiedStore["1:1000"])
    }

    @Test
    fun `退款回落后再涨回同一级不重复提醒`() = runBlocking {
        val notifier = notifier()

        givenProgresses(progress(id = 1, spent = 9_000))
        notifier.checkAndNotify()
        // 退款/删账单让进度回到阈值以下
        givenProgresses(progress(id = 1, spent = 1_000))
        notifier.checkAndNotify()
        // 又花回去：仍然是「达阈值」这一级，不该再打扰一次
        givenProgresses(progress(id = 1, spent = 9_000))
        notifier.checkAndNotify()

        assertEquals("回落不能把去重记录清掉", 1, notifiedStore["1:1000"])
    }

    @Test
    fun `进入新一期会重新提醒`() = runBlocking {
        val notifier = notifier()

        givenProgresses(progress(id = 1, spent = 9_000, windowStart = 1_000L))
        notifier.checkAndNotify()
        // 新的一期：键变了，旧键被淘汰、新键重新提醒
        givenProgresses(progress(id = 1, spent = 9_000, windowStart = 2_000_000L))
        notifier.checkAndNotify()

        assertEquals("旧期的记录应被淘汰，记录数上限就是预算条数", 1, notifiedStore.size)
        assertEquals(1, notifiedStore["1:2000000"])
    }

    @Test
    fun `单条预算关掉提醒开关时不发`() = runBlocking {
        val silent = BudgetProgress(
            budget = budget(id = 2).copy(reminderEnabled = false),
            spent = 20_000,
            windowStart = 1_000L,
            windowEnd = 1_100L
        )
        givenProgresses(silent)

        notifier().checkAndNotify()

        assertEquals(0, notificationCount())
    }

    @Test
    fun `删除预算后撤销通知栏里那条告警`() = runBlocking {
        givenProgresses(progress(id = 1, spent = 20_000))
        val notifier = notifier()
        notifier.checkAndNotify()
        assertEquals(1, notificationCount())

        notifier.cancelForBudget(1)

        assertEquals("预算没了，指向它的告警也该撤掉", 0, notificationCount())
    }

    @Test
    fun `两条预算各自一条通知互不覆盖`() = runBlocking {
        givenProgresses(progress(id = 1, spent = 20_000), progress(id = 2, spent = 20_000))

        notifier().checkAndNotify()

        assertEquals(2, notificationCount())
    }
}
