package com.palmnote.data.worker

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.palmnote.app.R
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.Asset
import com.palmnote.domain.repository.AssetRepository
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.ui.notification.NotificationHelper
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * 物品到期提醒的**触发**核对。
 *
 * 只测「窗口算得对」是不够的——那证明不了它真的走到了发通知那一步（比如检查没接进
 * `doWork` 的链子里、或者查询拿不到数据）。所以这里跑真实的 `doWork()`，只把数据源换成假的。
 *
 * 三条关键行为：**质保也提醒**（与卡片同一套「更紧迫的那个」判定）、**过期后仍提醒**、
 * **同一件物品用固定 id**（就地更新，不会在通知栏堆成一摞）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "zh")
class LifeDailyCheckWorkerAssetExpiryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() = unmockkAll()

    private fun millisFromToday(days: Long): Long =
        LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun buildWorker(
        assets: List<Asset>,
        enabled: Boolean = true,
        advanceDays: Int = 3,
        dailyEnabled: Boolean = false,
        itemsCreatedToday: Int = 0
    ): LifeDailyCheckWorker {
        val pm = mockk<PreferencesManager>(relaxed = true) {
            every { dailyReminderEnabled } returns flowOf(dailyEnabled)
            every { billReminderEnabled } returns flowOf(false)
            every { assetExpiryReminderEnabled } returns flowOf(enabled)
            every { reminderAdvanceDays } returns flowOf(advanceDays)
        }
        val assetRepo = mockk<AssetRepository>(relaxed = true) {
            every { getHeldAssetsWithExpiry() } returns flowOf(assets)
        }
        val itemRepo = mockk<LifeItemRepository>(relaxed = true) {
            coEvery { countItemsCreatedBetween(any(), any()) } returns itemsCreatedToday
        }
        val templateRepo = mockk<LifeTemplateRepository>(relaxed = true) {
            every { getAllVisibleTemplates() } returns flowOf(emptyList())
        }
        return LifeDailyCheckWorker(
            context = context,
            params = mockk(relaxed = true),
            templateRepo = templateRepo,
            itemRepo = itemRepo,
            focusRepo = mockk(relaxed = true),
            reportRepo = mockk(relaxed = true),
            billRepo = mockk(relaxed = true),
            assetRepo = assetRepo,
            pm = pm
        )
    }

    private fun stubNotifications() {
        mockkObject(NotificationHelper)
        every { NotificationHelper.show(any(), any(), any(), any(), any(), any(), any()) } just Runs
    }

    private fun verifyNoNotification() =
        verify(exactly = 0) { NotificationHelper.show(any(), any(), any(), any(), any(), any(), any()) }

    @Test
    fun `保质期临近时提醒，标题说的是保质期`() = runBlocking {
        stubNotifications()

        buildWorker(listOf(Asset(name = "牛奶", category = "食品", shelfLifeExpireDate = millisFromToday(3))))
            .doWork()

        verify {
            NotificationHelper.show(
                any(),
                NotificationHelper.Channel.ASSET_EXPIRY,
                context.getString(R.string.notification_asset_expiry_shelf_life_title),
                any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `质保更紧迫时也提醒，标题说的是质保`() = runBlocking {
        stubNotifications()

        buildWorker(
            listOf(
                Asset(
                    name = "相机",
                    category = "数码",
                    warrantyExpireDate = millisFromToday(2),
                    shelfLifeExpireDate = millisFromToday(300)
                )
            )
        ).doWork()

        verify {
            NotificationHelper.show(
                any(),
                NotificationHelper.Channel.ASSET_EXPIRY,
                context.getString(R.string.notification_asset_expiry_warranty_title),
                any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `过期后仍提醒，文案说已过期`() = runBlocking {
        stubNotifications()

        buildWorker(listOf(Asset(name = "牛奶", category = "食品", shelfLifeExpireDate = millisFromToday(-1))))
            .doWork()

        val expiredMessage = context.resources.getQuantityString(
            R.plurals.notification_asset_expiry_expired_message, 1, "牛奶", 1L
        )
        verify { NotificationHelper.show(any(), any(), any(), expiredMessage, any(), any(), any()) }
    }

    @Test
    fun `同一件物品用固定 id，重复跑不会堆出两条`() = runBlocking {
        stubNotifications()
        val worker = buildWorker(
            listOf(Asset(id = 42, name = "牛奶", category = "食品", shelfLifeExpireDate = millisFromToday(3)))
        )

        worker.doWork()
        worker.doWork()

        // 两次都必须是这一个固定 id：若退回自增，两次的 id 不同，这条断言就不成立
        verify(exactly = 2) {
            NotificationHelper.show(any(), any(), any(), any(), any(), any(), assetExpiryNotifyId(42))
        }
    }

    @Test
    fun `开关关掉时一条都不发`() = runBlocking {
        stubNotifications()

        buildWorker(
            listOf(Asset(name = "牛奶", category = "食品", shelfLifeExpireDate = millisFromToday(3))),
            enabled = false
        ).doWork()

        verifyNoNotification()
    }

    /**
     * 「每日提醒」原来无条件就发，正文却写着「今天还没有记录生活哦」。改成先查今天有没有记录，
     * 这两条钉住新行为（原来那条路径在测试里从没走到过——helper 里 dailyReminderEnabled 一直是 false）。
     */
    @Test
    fun `今天已经记过生活记录时不再发每日提醒`() = runBlocking {
        stubNotifications()

        buildWorker(emptyList(), dailyEnabled = true, itemsCreatedToday = 1).doWork()

        verify(exactly = 0) {
            NotificationHelper.show(any(), NotificationHelper.Channel.CHECKIN, any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `今天一条记录都没有时才发每日提醒`() = runBlocking {
        stubNotifications()

        buildWorker(emptyList(), dailyEnabled = true, itemsCreatedToday = 0).doWork()

        verify {
            NotificationHelper.show(any(), NotificationHelper.Channel.CHECKIN, any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `窗口两头之外都不发`() = runBlocking {
        stubNotifications()

        buildWorker(
            listOf(
                Asset(name = "还早", category = "食品", shelfLifeExpireDate = millisFromToday(30)),
                Asset(name = "早就过期", category = "食品", shelfLifeExpireDate = millisFromToday(-30))
            )
        ).doWork()

        verifyNoNotification()
    }
}
