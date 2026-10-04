package com.palmnote.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.palmnote.data.datastore.PreferencesManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DemoDataSeeder.ensureSeeded] 的三条策略：
 *
 * 1. **语言变了就整批重播** —— 演示文案是播种时按当时语言写进库的，不重播英文界面就是中文；
 * 2. **演示关闭时一条都不写** —— 重播是写库操作，必须和三个播种器 `ensureSeeded` 里那道
 *    「未开启演示就直接返回」的门同口径；漏了它，「从没开过演示 + 系统语言变了」会在启动时
 *    凭空冒出示例数据（这是本轮真出现过的 bug，钉在这里）；
 * 3. **并发只能重播一次** —— 「设置页切语言」与「重建后的生活页收集器」会同时进来，
 *    不加锁会插出两份示例数据（真机截图实证：今日安排 3 条变 14 条）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "en")
class DemoDataSeederTest {

    private val life = mockk<LifeDemoSeeder>(relaxed = true)
    private val wealth = mockk<WealthDemoSeeder>(relaxed = true)
    private val habit = mockk<HabitDemoSeeder>(relaxed = true)

    private fun seeder() = DemoDataSeeder(
        life,
        wealth,
        habit,
        ApplicationProvider.getApplicationContext(),
        // 应用级 scope 只服务于 onLanguageChanged（fire-and-forget）；本测试直接 await ensureSeeded
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )

    /**
     * 有状态的偏好替身：`setDemoSeedLanguage` 真的会改掉 `demoSeedLocale` 的返回值。
     * 返回常量的话「重播后第二个调用应当看到新值」这件事根本测不出来。
     */
    private fun preferences(demoOn: Boolean, seededLocale: String?): PreferencesManager {
        var locale = seededLocale
        val prefs = mockk<PreferencesManager>(relaxed = true)
        every { prefs.lifeDemoMode } returns flowOf(demoOn)
        every { prefs.demoSeedLanguage } answers { flowOf(locale) }
        coEvery { prefs.setDemoSeedLanguage(any()) } answers { locale = firstArg() }
        return prefs
    }

    @Test
    fun `demo off never writes demo rows`() = runBlocking {
        // 演示关闭 + 语言与播种时不同（正是会凭空冒出示例数据的组合）
        val prefs = preferences(demoOn = false, seededLocale = "zh-CN")

        seeder().ensureSeeded(prefs)

        coVerify(exactly = 0) { life.reseed(any()) }
        coVerify(exactly = 0) { wealth.reseed(any()) }
        coVerify(exactly = 0) { habit.reseed(any()) }
        coVerify(exactly = 0) { life.ensureSeeded(any()) }
        coVerify(exactly = 0) { wealth.ensureSeeded(any()) }
        coVerify(exactly = 0) { habit.ensureSeeded(any()) }
    }

    @Test
    fun `locale change reseeds all three modules`() = runBlocking {
        val prefs = preferences(demoOn = true, seededLocale = "zh-CN")

        seeder().ensureSeeded(prefs)

        coVerify(exactly = 1) { life.reseed(prefs) }
        coVerify(exactly = 1) { wealth.reseed(prefs) }
        coVerify(exactly = 1) { habit.reseed(prefs) }
        coVerify(exactly = 1) { prefs.setDemoSeedLanguage("en") }
    }

    @Test
    fun `same locale goes through the idempotent path`() = runBlocking {
        val prefs = preferences(demoOn = true, seededLocale = "en")

        seeder().ensureSeeded(prefs)

        coVerify(exactly = 0) { life.reseed(any()) }
        coVerify(exactly = 1) { life.ensureSeeded(prefs) }
        coVerify(exactly = 1) { wealth.ensureSeeded(prefs) }
        coVerify(exactly = 1) { habit.ensureSeeded(prefs) }
    }

    @Test
    fun `missing locale record also reseeds`() = runBlocking {
        // 升级自「还没有 demo_seed_locale 这个偏好」的旧版本：库里是按**旧语言**播的种，
        // 而偏好为空。必须当作「不一致」重播一次 —— 否则首次运行把当前语言写进偏好之后，
        // 数据就永远停在旧语言了（英文界面 + 中文示例内容）。
        val prefs = preferences(demoOn = true, seededLocale = null)

        seeder().ensureSeeded(prefs)

        coVerify(exactly = 1) { life.reseed(prefs) }
        coVerify(exactly = 1) { wealth.reseed(prefs) }
        coVerify(exactly = 1) { habit.reseed(prefs) }
        coVerify(exactly = 1) { prefs.setDemoSeedLanguage("en") }
    }

    @Test
    fun `concurrent calls reseed only once`() = runBlocking {
        // 切语言时「设置页」与「重建后的生活页收集器」会同时进来。
        // 播种放慢，把竞态窗口拉开：没有闸门时两个调用都会读到旧语言、各插一份示例。
        coEvery { life.reseed(any()) } coAnswers {
            delay(80)
            61
        }
        val prefs = preferences(demoOn = true, seededLocale = "zh-CN")
        val seeder = seeder()

        val jobs = List(2) { launch(Dispatchers.Default) { seeder.ensureSeeded(prefs) } }
        jobs.joinAll()

        coVerify(exactly = 1) { life.reseed(prefs) }
        coVerify(exactly = 1) { wealth.reseed(prefs) }
        coVerify(exactly = 1) { habit.reseed(prefs) }
    }
}
