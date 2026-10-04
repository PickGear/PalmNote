package com.palmnote.data

import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import android.content.Context
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.life.LifeDemoData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 演示模式的示例数据播种器（**生活页专用**；记账 / 资产等页面后续照此扩展）。
 *
 * 关键点：示例数据是**真实写进 `life_items` 表的行**，不是画在界面上的假数据 ——
 * 用户可以点开看、编辑、删掉，也会跟着一起被导出和备份。它们统一带 `meta` 标记
 * （[LIFE_DEMO_META]），**关闭演示模式时靠这个标记把它们整批删除**（不是隐藏：
 * 备份是整库拷贝，留在库里就会被打包进去）。
 *
 * 本类只负责「清干净 / 播一遍」；**什么时候做**由调用方决定，统一入口见 [ensureSeeded]：
 * 应用启动（[com.palmnote.PalmNoteApp]）、生活页 `LifeCalendarViewModel`、设置里的演示开关三处。
 *
 * ⚠️ **改动示例内容（`LifeDemoData`）后必须把 [SEED_VERSION] +1**，否则库里已有的旧示例行不会被更新。
 */
@Singleton
class LifeDemoSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemDao: LifeItemDao,
    private val templateDao: LifeTemplateDao
) {

    /** [clearAll] 的结果：移除的示例条数 / 保留（毕业）的用户自建条数。 */
    data class DemoClearResult(val removed: Int, val kept: Int)

    companion object {
        /**
         * 示例内容（[LifeDemoData]）的**版本号**。
         *
         * **改动示例条目或标题后必须 +1**：示例是真实写进库的行，改代码不会自动改写库里已有的行；
         * 版本号落后时启动 / 开启演示会**自动重播种**（见 `PreferencesManager.lifeDemoSeedVersion`）。
         * 不 +1 的话，改示例只对全新用户生效，老用户看不到任何变化。
         *
         * v21：修掉「并发播种插出两份示例」后必须重播一次——受影响的安装里库里躺着 122 条
         * （两份 61 条），只有重建（先清后插）能清干净。
         */
        const val SEED_VERSION = 21
    }

    /**
     * **移除示例数据**：物理删除所有带标记的示例行。
     *
     * 关闭演示模式时调用。为什么必须真删、不能只隐藏：备份是**整库拷贝**，
     * 只要行还在库里就一定会被打包进去；用户定案是「关闭后等同没有数据、不能导出」，
     * 所以只有删掉才能保证页面和备份都干净。
     */
    suspend fun clear() {
        itemDao.clearSeedItems()
    }

    /**
     * **重建（重置）**示例数据：先清掉示例行，再按 [LifeDemoData] 重新播种。
     *
     * 语义来自用户定案：**再次开启 = 重置** —— 无论用户之前是否编辑、删除过示例，
     * 都会复原成初始的 61 条。
     * 因此这里不做「已存在就跳过」，而是每次调用都重建；**是否重建**由调用方
     * （依 `lifeDemoSeeded` 偏好）判断：关闭时该标记被清，于是下次开启必然重建。
     *
     * **播种标记在这里落**（与 `WealthDemoSeeder` / `HabitDemoSeeder` 的 reseed 一致）：
     * 调用方除了「开启演示」还有一条「语言变了 → 重播」的路径，那条不经过 `ensureSeeded`，
     * 漏了标记会让下一次 `ensureSeeded` 认为「版本落后」，于是**每次启动都重播一遍**——
     * 演示期用户改过的示例内容会被反复抹掉。
     *
     * @return 本次写入的条数。
     */
    suspend fun reseed(preferences: PreferencesManager): Int {
        // 只清示例行：演示期间用户自建的记录带 meta 但不是示例行，必须留下
        itemDao.clearSeedItems()

        val templates = runCatching { templateDao.getBuiltinTemplates().first() }.getOrNull().orEmpty()
        if (templates.isEmpty()) return 0
        val tplByIcon = templates.associateBy { it.icon }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        var inserted = 0
        LifeDemoData.items.forEach { spec ->
            val tpl = tplByIcon[spec.templateIcon] ?: return@forEach
            val createdAt = LocalDateTime.of(
                today.minusDays(spec.daysAgo.toLong()),
                java.time.LocalTime.of(spec.hour.coerceIn(0, 23), spec.minute.coerceIn(0, 59))
            ).atZone(zone).toInstant().toEpochMilli()
            // 日期先转成毫秒（见 normalizeDates 注释），再按模板的日期字段决定执行列 dueDate ——
            // 与 LifeItemRepositoryImpl.mirrorExecutionColumns 同一口径，示例条目才和真实条目行为一致。
            // 相对日期占位符："+12d" / "-365d" / "+0d"（今天）——日期字段绝不能写死 ISO：
            // 写死的"未来日期"过一阵子就变成过去，会与执行列（按 daysAgo/dueInDays 相对今天算）
            // 互相打架（演示数据里出现过「截止日写 10-06、条目却排在今天」这种自相矛盾）。
            val localized = DemoTexts.localizeJson(context, spec.fieldsData)
            val (fieldsData, datesByKey) = normalizeDates(expandRelativeDates(localized, today))
            // IMAGE 字段演示图：assets 里的插画物化到 filesDir（覆盖写，幂等），
            // fieldsData 里写绝对路径 —— 与用户选图（content URI）同被详情页 Thumb/Coil 消费。
            val withImages = mergeImages(fieldsData, spec.images)
            val dateKey = dateFieldKey(tpl.fieldsConfig)
            val dueDate = spec.dueInDays?.let {
                today.plusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            } ?: dateKey?.let { datesByKey[it] }
                // 记录类模板（打卡 / 心情 / 日记 / 专注 / 身体记录）没有任何日期字段：
                // 日期 = 记录发生当天（createdAt），与 LifeItemRepositoryImpl.recordFallbackDueDate
                // 同一规则 —— 否则这些示例既不上日历也不进当日看板（"该有日期的没日期"）。
                ?: if (dateKey == null && tpl.category == BuiltinTemplates.RECORD_CATEGORY) createdAt else null
            itemDao.insertItem(
                LifeItem(
                    templateId = tpl.id,
                    title = DemoTexts.localize(context, spec.title),
                    fieldsData = withImages,
                    status = spec.status,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    dueDate = dueDate,
                    meta = LIFE_DEMO_META,
                    // 「可丢弃」的唯一判据：只有播种器写 1
                    isSeedSample = true
                )
            )
            inserted++
        }
        if (inserted > 0) {
            preferences.setLifeDemoSeeded(true)
            preferences.setLifeDemoSeedVersion(SEED_VERSION)
        }
        return inserted
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
            itemDao.deleteAllByDemoMeta(LIFE_DEMO_META)
        }
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
     * **保证示例数据是最新的**：演示模式开启且（尚未播种 **或** 内容版本落后 [SEED_VERSION]
     * **或** 库里其实一条示例行都没有）⟹ 重建。
     *
     * 播种「什么时候做」的策略集中在此，三处调用：**应用启动**（[com.palmnote.PalmNoteApp]，
     * 保证只看首页/仪表盘也能更新，不必先进生活页）、生活页 ViewModel、设置里的演示开关。
     * **改示例内容后把 [SEED_VERSION] +1** 即自动生效。
     *
     * @return 本次写入条数（0 = 无需重建或写入失败）。
     */
    suspend fun ensureSeeded(preferences: PreferencesManager): Int {
        if (!preferences.lifeDemoMode.first()) return 0
        val upToDate = preferences.lifeDemoSeeded.first() &&
            preferences.lifeDemoSeedVersion.first() >= SEED_VERSION
        // 标记说「已播种」不等于库里真有示例行：clearAllTables() 清库但不清 DataStore。
        // 只信标记的话这里会一直返回 0，生活页在演示模式下永久空白且毫无提示。
        if (upToDate && itemDao.countSeedItems() > 0) return 0
        return reseed(preferences)
    }

    /**
     * 种子里的日期写成 `yyyy-MM-dd` **文本**（人读友好），但全仓的日期读取方
     * ——[com.palmnote.domain.model.DerivedEvaluator] 派生字段（倒计时 / 正数日）、`LifeItemDao` 执行列镜像、
     * `LifeDailyCheckWorker` 提醒——**一律按毫秒解读**。种子里不转就会整体退化：算不出天数、
     * 卡片上出现「01月01日 / 已过 20716 天」。所以写入前统一转成毫秒，与真实条目的存储形态一致。
     *
     * @return 转换后的 `fieldsData`，以及**按字段 key 索引**的日期毫秒（供执行列取用）。
     */
    /**
     * 演示图物化到 filesDir/images（demo_ 前缀）后写绝对路径：
     * 与用户选图（content URI 复制到同一目录）完全同构，所有渲染链路真机验证过。
     */
    private fun mergeImages(fieldsData: String, images: Map<String, String>): String {
        if (images.isEmpty()) return fieldsData
        val obj = runCatching { Json.decodeFromString<JsonObject>(fieldsData) }.getOrNull()
            ?: return fieldsData
        val merged = JsonObject(
            obj + images.mapValues { (_, asset) -> JsonPrimitive(DemoImageStore.materialize(context, asset)) }
        )
        return merged.toString()
    }

    /**
     * 展开日期占位符：字段值写成 `"+12d"`（12 天后）/ `"-365d"`（365 天前）/ `"+0d"`（今天），
     * 播种时按当天展开成 ISO 日期。**只匹配整值**（含引号），不会误伤正文里的 "90d" 之类文本。
     */
    private fun expandRelativeDates(fieldsData: String, today: LocalDate): String =
        Regex("\"([+-])(\\d+)d\"").replace(fieldsData) { m ->
            val days = m.groupValues[2].toLong() * (if (m.groupValues[1] == "-") -1 else 1)
            "\"" + today.plusDays(days).toString() + "\""
        }

    private fun normalizeDates(fieldsData: String): Pair<String, Map<String, Long>> {
        val obj = runCatching { Json.decodeFromString<JsonObject>(fieldsData) }.getOrNull()
            ?: return fieldsData to emptyMap()
        val dates = mutableMapOf<String, Long>()
        val converted = obj.mapValues { (key, el) ->
            val raw = (el as? JsonPrimitive)?.content
            val ms = DateUtils.parseDateValueOrNull(raw) ?: return@mapValues el
            dates[key] = ms
            JsonPrimitive(ms)
        }
        return JsonObject(converted).toString() to dates
    }

    /** 模板里第一个 `DATE`/`DATETIME` 字段的 key；没有则 null（订阅这类无日期字段的模板不会误设执行列）。 */
    private fun dateFieldKey(fieldsConfig: String): String? = try {
        Json.decodeFromString<JsonArray>(fieldsConfig).firstNotNullOfOrNull { el ->
            val o = el as? JsonObject ?: return@firstNotNullOfOrNull null
            val type = (o["type"] as? JsonPrimitive)?.content
            if (type == "DATE" || type == "DATETIME") (o["key"] as? JsonPrimitive)?.content else null
        }
    } catch (_: Exception) {
        null
    }
}
