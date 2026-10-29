package com.palmnote.data

import android.content.Context
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeRecycleBinDao
import com.palmnote.data.db.dao.FieldValueDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生活页演示播种器（**已空载**）。
 *
 * 历史上它按设计稿播种 62 条生活示例（目标 / 习惯 / 纪念日 / 打卡……）；
 * 2026-10-30 起演示数据改用真实导出（掌记_数据.zip），其中没有生活条目——
 * 生活页功能尚未打磨，演示模式不再虚构生活内容，本播种器只剩「清干净」的职责。
 *
 * 模板不在此列：内置模板是**应用结构**（所有用户一致，由 BuiltinTemplates 播种），
 * 不是演示数据；关闭演示不会清模板。
 *
 * 保留 [SEED_VERSION] / reseed / clearAll 的骨架：调用方（[DemoDataSeeder] 门面）
 * 的协议不变，将来生活演示要恢复内容时从这里长回来。
 */
@Singleton
class LifeDemoSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemDao: LifeItemDao,
    private val recycleBinDao: LifeRecycleBinDao,
    private val fieldValueDao: FieldValueDao
) {

    companion object {
        /**
         * 演示内容版本号（协议保留；当前演示不含生活条目，版本号只随结构变化推进）。
         */
        const val SEED_VERSION = 23
    }

    data class DemoClearResult(val removed: Int, val kept: Int)

    /** 演示模式**关闭**时的清理：示例行物理删除、用户数据毕业、清「已播种」标记。 */
    suspend fun clear() {
        itemDao.clearSeedItems()
    }

    /** 重建示例数据：生活侧不再虚构内容，只清掉历史示例行并落「已播种」标记。 */
    suspend fun reseed(preferences: PreferencesManager): Int {
        itemDao.clearSeedItems()
        preferences.setLifeDemoSeeded(true)
        preferences.setLifeDemoSeedVersion(SEED_VERSION)
        return 0
    }

    /**
     * 演示模式**关闭**时的清理：示例行物理删除、用户数据毕业、清「已播种」标记。
     *
     * 顺序不能反：先毕业（把 `meta` 置空）再删示例行。否则演示期间用户自建的记录
     * 会既留着行、又被互斥口径排除在外，用户从此再也看不到它们。
     */
    suspend fun clearAll(preferences: PreferencesManager, keepUserCreated: Boolean = true): DemoClearResult {
        // 先量数量再动数据：移除的是播种示例；用户自建的按询问结果「毕业保留」或「一并删除」。
        val removed = itemDao.countSeedItems()
        val kept = itemDao.countDemoCreatedItems(LIFE_DEMO_META)
        if (keepUserCreated) {
            itemDao.graduateDemoCreatedItems(LIFE_DEMO_META)
            clear()
        } else {
            // 演示期自建条目走过双写、有统计副本：先删副本再删主行（副本反查依赖条目行）
            fieldValueDao.deleteByItemMeta(LIFE_DEMO_META)
            itemDao.deleteAllByDemoMeta(LIFE_DEMO_META)
        }
        // 回收站里随原行进站的示例行一并清掉（备份是整库拷贝，留在站里同样不干净）
        recycleBinDao.clearDemoRows(LIFE_DEMO_META)
        // 示例图一并清掉（只删 demo_ 前缀，用户图片不受影响）
        DemoImageStore.clear(context)
        preferences.setLifeDemoSeeded(false)
        return DemoClearResult(removed = removed, kept = kept)
    }

    /** 演示期用户自建、需要决定归宿的条数（0 = 不必询问）。 */
    suspend fun countDemoCreated(preferences: PreferencesManager): Int {
        if (!preferences.lifeDemoMode.first()) return 0
        return itemDao.countDemoCreatedItems(LIFE_DEMO_META)
    }

    /**
     * **保证演示状态是最新的**：演示模式开启且版本落后时落一次「已播种」标记。
     * 生活侧不再播种内容，本方法只剩协议职责（调用方三处：应用启动 / 生活页 VM / 设置开关）。
     */
    suspend fun ensureSeeded(preferences: PreferencesManager): Int {
        if (!preferences.lifeDemoMode.first()) return 0
        val upToDate = preferences.lifeDemoSeeded.first() &&
            preferences.lifeDemoSeedVersion.first() >= SEED_VERSION
        if (upToDate) return 0
        return reseed(preferences)
    }
}
