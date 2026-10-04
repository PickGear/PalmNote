package com.palmnote.data

import android.content.Context
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 演示数据的**统一入口**：生活（[LifeDemoSeeder]）+ 记账/物品（[WealthDemoSeeder]）+ 目标/习惯（[HabitDemoSeeder]）。
 *
 * 为什么要这层门面：演示开关的语义是「这个 app 有没有示例数据」，
 * 而实现上是两个模块各自的播种器。调用方（应用启动 / 设置开关 / 引导页 / 生活页 VM）
 * 只该知道「开或关」，不该知道有几个播种器、每个播种器怎么判断幂等。
 * 将来补记账 / 物品之外的模块（目标等），也只在这里加一行委托。
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val life: LifeDemoSeeder,
    private val wealth: WealthDemoSeeder,
    private val habit: HabitDemoSeeder,
    @ApplicationContext private val context: Context,
    /** 只服务于 [onLanguageChanged]：见该方法的说明。 */
    @ApplicationScope private val appScope: CoroutineScope
) {

    /**
     * **串行化闸门**：播种 / 清理会先删后插，两个并发调用会插出**两份示例数据**
     * （真机截图实证：切语言后生活页「今日安排」从 3 条变 14 条、记录 6 条变 12 条，
     * 正是「设置页切语言」与「重建后的生活页收集器」同时进 [ensureSeeded] 撞上的）。
     *
     * 判据是「先读 `demoSeedLanguage`、后写」——并发的第二个调用在第一个写完之前读到的还是旧值，
     * 于是也走重播分支。加锁后第二个调用会等第一个写完，再读就是一致值，自然走幂等路径。
     */
    private val seedMutex = Mutex()

    /** 全量演示数据的清理结果：移除的示例条数 / 保留（毕业）的用户自建条数。 */
    data class ClearResult(val removed: Int, val kept: Int)

    /** 演示期用户自建、关演示时需要决定归宿的条数（目前只有生活模块的 meta 自建；0 = 不必询问）。 */
    suspend fun countUserCreatedInDemo(preferences: PreferencesManager): Int =
        life.countDemoCreated(preferences)

    /**
     * 保证所有模块的示例内容存在且为最新（幂等，多调用点安全）。
     *
     * **语言变化时整批重播**：演示文案是播种时按当时的应用语言写进库的（见 [DemoTexts]），
     * 不是运行时翻译——中文界面播的种，切到英文后库里仍是中文（真机截图暴露过）。
     * 示例内容可安全重建，所以语言一变就重播一遍。副作用是**演示期对示例内容的编辑会被复原**，
     * 这与既有的「再次开启 = 重置」同一口径（见 [LifeDemoSeeder.reseed] 的说明）。
     *
     * **`demoSeedLanguage` 为空也算不一致**：升级自「还没有这个偏好」的旧版本时，库里是按**旧语言**
     * 播的种，而偏好为空。只比对「非空且不同」会漏掉这一整批安装——数据永远停在旧语言，
     * 因为第一次运行会把当前语言写进偏好，此后就再也对不上了。
     *
     * **演示关闭时一律不动库**：上面的重播是**写库**操作，而三个播种器的 `ensureSeeded`
     * 各自都有一道「未开启演示就直接返回」的门 —— 重播必须同样受它约束，
     * 否则「从没开过演示 + 系统语言变了」会在启动时凭空冒出示例数据。
     */
    suspend fun ensureSeeded(preferences: PreferencesManager) = seedMutex.withLock {
        if (!preferences.lifeDemoMode.first()) return@withLock
        val language = DemoTexts.currentLocaleLanguage(context)
        if (preferences.demoSeedLanguage.first() != language) {
            life.reseed(preferences)
            wealth.reseed(preferences)
            habit.reseed(preferences)
            preferences.setDemoSeedLanguage(language)
            return@withLock
        }
        life.ensureSeeded(preferences)
        wealth.ensureSeeded(preferences)
        habit.ensureSeeded(preferences)
    }

    /**
     * 应用语言变了：在**应用级 scope** 上按新语言重播示例数据。
     *
     * 为什么不直接让调用方在自己的作用域里 await [ensureSeeded]：切语言会重建 Activity，
     * 发起方（设置页 ViewModel）的作用域当场取消，而重播是「先清后插」——
     * 被取消在中途会留下**空的**示例数据，比不重播更糟。这里 fire-and-forget，
     * 由闸门保证与其它调用串行。
     */
    fun onLanguageChanged(preferences: PreferencesManager) {
        appScope.launch { ensureSeeded(preferences) }
    }

    /**
     * 关闭演示：所有模块的示例整批物理移除。
     * [keepUserCreated] = 演示期自建数据的归宿（true=毕业保留 / false=一并删除），
     * 由「毕业询问」对话框决定——只有生活模块需要问（记账/物品的自建数据本就不带标记，
     * 打标模型下天然保留；习惯同理）。
     *
     * 与 [ensureSeeded] 共用闸门：两者都是「先删后插」，交叠执行会留下半新半旧的数据。
     */
    suspend fun clearAll(preferences: PreferencesManager, keepUserCreated: Boolean = true): ClearResult =
        seedMutex.withLock {
            val lifeResult = life.clearAll(preferences, keepUserCreated)
            val wealthResult = wealth.clearAll(preferences)
            val habitResult = habit.clearAll(preferences)
            ClearResult(
                removed = lifeResult.removed + wealthResult.removed + habitResult.removed,
                kept = lifeResult.kept
            )
        }
}
