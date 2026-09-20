package com.palmnote.data

import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.life.LifeDemoData
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
    private val itemDao: LifeItemDao,
    private val templateDao: LifeTemplateDao
) {

    companion object {
        /**
         * 示例内容（[LifeDemoData]）的**版本号**。
         *
         * **改动示例条目或标题后必须 +1**：示例是真实写进库的行，改代码不会自动改写库里已有的行；
         * 版本号落后时启动 / 开启演示会**自动重播种**（见 `PreferencesManager.lifeDemoSeedVersion`）。
         * 不 +1 的话，改示例只对全新用户生效，老用户看不到任何变化。
         */
        const val SEED_VERSION = 6
    }

    /**
     * **移除示例数据**：物理删除所有带标记的示例行。
     *
     * 关闭演示模式时调用。为什么必须真删、不能只隐藏：备份是**整库拷贝**，
     * 只要行还在库里就一定会被打包进去；用户定案是「关闭后等同没有数据、不能导出」，
     * 所以只有删掉才能保证页面和备份都干净。
     */
    suspend fun clear() {
        itemDao.clearDemoItems(LIFE_DEMO_META)
    }

    /**
     * **重建（重置）**示例数据：先清掉带标记的旧行，再按 [LifeDemoData] 重新播种。
     *
     * 语义来自用户定案：**再次开启 = 重置** —— 无论用户之前是否编辑、删除过示例，
     * 都会复原成初始的 50 条。
     * 因此这里不做「已存在就跳过」，而是每次调用都重建；**是否重建**由调用方
     * （依 `lifeDemoSeeded` 偏好）判断：关闭时该标记被清，于是下次开启必然重建。
     *
     * @return 本次写入的条数。
     */
    suspend fun reseed(): Int {
        itemDao.clearDemoItems(LIFE_DEMO_META)

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
            val (fieldsData, datesByKey) = normalizeDates(spec.fieldsData)
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
                    title = spec.title,
                    fieldsData = fieldsData,
                    status = spec.status,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    dueDate = dueDate,
                    meta = LIFE_DEMO_META
                )
            )
            inserted++
        }
        return inserted
    }

    /** 演示模式**关闭**时的清理：物理删除示例行，并清「已播种」标记（下次开启必然重建）。 */
    suspend fun clearAll(preferences: PreferencesManager) {
        clear()
        preferences.setLifeDemoSeeded(false)
    }

    /**
     * **保证示例数据是最新的**：演示模式开启且（尚未播种 **或** 内容版本落后 [SEED_VERSION]）⟹ 重建。
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
        if (upToDate) return 0
        val inserted = reseed()
        if (inserted > 0) {
            preferences.setLifeDemoSeeded(true)
            preferences.setLifeDemoSeedVersion(SEED_VERSION)
        }
        return inserted
    }

    /**
     * 种子里的日期写成 `yyyy-MM-dd` **文本**（人读友好），但全仓的日期读取方
     * ——[com.palmnote.domain.model.DerivedEvaluator] 派生字段（倒计时 / 正数日）、`LifeItemDao` 执行列镜像、
     * `LifeDailyCheckWorker` 提醒——**一律按毫秒解读**。种子里不转就会整体退化：算不出天数、
     * 卡片上出现「01月01日 / 已过 20716 天」。所以写入前统一转成毫秒，与真实条目的存储形态一致。
     *
     * @return 转换后的 `fieldsData`，以及**按字段 key 索引**的日期毫秒（供执行列取用）。
     */
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
