package com.palmnote.data

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.ui.theme.lifeTemplateIdentityHex
import kotlinx.coroutines.flow.first

/**
 * 生活模板种子（P2，§五 / §7.3）。
 * 种子版本 2（2026-09-20）：16 个内置模板按 §5.0 表重写字段配置 + 新增「身体记录」「物品维护」2 个（定案 25）。
 * 铁律：不删除任何模板行；已有字段不改 key / type；重写只做「配置变更 + 纯新增」（§5.0）。
 * 投递红线：用户改过的模板（customizedTemplateIds 显式声明 或 current != lastSynced 隐式判定）绝不被覆盖。
 */
class LifeDataSeeder(
    private val templateRepo: LifeTemplateRepository,
    private val appDatabase: AppDatabase,
    private val syncPrefs: android.content.SharedPreferences? = null
) {
    companion object {
        /** 当前种子版本；每次重写种子默认值时 +1（§7.3 seedVersion 投递）。 */
        const val SEED_VERSION = 2
        private const val KEY_SEED_VERSION = "life_seed_version"
        private const val KEY_CUSTOMIZED = "customized_template_ids"
    }

    /** 显式「已被用户编辑」的内置模板 id 集合（§7.3：与 lastSynced 隐式判定双保险）。 */
    fun customizedTemplateIds(): Set<Long> =
        syncPrefs?.getStringSet(KEY_CUSTOMIZED, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    fun markCustomized(templateId: Long) {
        val prefs = syncPrefs ?: return
        val ids = prefs.getStringSet(KEY_CUSTOMIZED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (ids.add(templateId.toString())) prefs.edit().putStringSet(KEY_CUSTOMIZED, ids).apply()
    }

    /**
     * 「恢复出厂」（§4.1）：唯一把 id 从 customized 集合移除的动作；
     * 模板回到种子值并重新纳入投递。不减少任何 LifeItem。
     */
    suspend fun restoreToSeed(templateId: Long) {
        val stored = templateRepo.getTemplateById(templateId) ?: return
        if (!stored.isBuiltin) return
        val seed = lifeTemplateSeeds.firstOrNull { it.icon == stored.icon } ?: return
        templateRepo.updateTemplate(
            stored.copy(
                name = seed.name, category = seed.category, description = seed.description,
                color = seed.color, fieldsConfig = seed.fieldsConfig,
                statusFlowConfig = seed.statusFlowConfig, linkConfig = seed.linkConfig,
                availableLayouts = seed.availableLayouts, isSpecial = seed.isSpecial,
                updatedAt = System.currentTimeMillis()
            )
        )
        recordSynced(stored.icon, seed.fieldsConfig)
        val prefs = syncPrefs ?: return
        val ids = prefs.getStringSet(KEY_CUSTOMIZED, emptySet())?.toMutableSet() ?: return
        if (ids.remove(templateId.toString())) prefs.edit().putStringSet(KEY_CUSTOMIZED, ids).apply()
    }

    suspend fun seedIfEmpty() {
        val existingTemplates = templateRepo.getAllTemplates().first()
        if (existingTemplates.isEmpty()) {
            appDatabase.withTransaction {
                seedTemplates()
            }
            recordSeedVersion()
        } else {
            // 版本升级新增的内置模板：存量安装按 icon 补插（如「专注」/「身体记录」/「物品维护」）
            ensureBuiltinTemplatesExist()
            refreshBuiltinFieldsConfigs()
            syncBuiltinStaticProps()
            recordSeedVersion()
        }
    }

    private fun recordedSeedVersion(): Int = syncPrefs?.getInt(KEY_SEED_VERSION, 0) ?: 0

    private fun recordSeedVersion() {
        if (recordedSeedVersion() < SEED_VERSION) {
            syncPrefs?.edit()?.putInt(KEY_SEED_VERSION, SEED_VERSION)?.apply()
        }
    }

    /** 存量安装补插缺失的内置模板（按 icon 识别身份）。 */
    private suspend fun ensureBuiltinTemplatesExist() {
        val existingIcons = templateRepo.getAllTemplates().first().map { it.icon }.toSet()
        val missing = lifeTemplateSeeds.filter { it.icon !in existingIcons }
        if (missing.isEmpty()) return
        appDatabase.withTransaction {
            missing.forEach { templateRepo.insertTemplate(it) }
        }
    }

    /**
     * 存量安装：把内置模板的 fieldsConfig 同步为种子值，但**不动用户自定义过的模板**。
     * 双重判定（§7.3）：customizedTemplateIds 显式声明 / current == lastSynced 隐式判定，两者都过才写。
     * 无同步记录（本机制上线后的首次启动）不更新模板，但会把当前值记为基线，
     * 保证未来的种子变更能正常识别「用户没改过」并修复。
     */
    private suspend fun refreshBuiltinFieldsConfigs() {
        val existing = templateRepo.getAllTemplates().first()
        val seedByIdentity = lifeTemplateSeeds.associateBy { it.icon }
        val customized = customizedTemplateIds()
        existing.filter { it.isBuiltin && !it.isSpecial }.forEach { stored ->
            if (stored.id in customized) return@forEach  // 显式声明：用户改过，绝不覆盖
            val seed = seedByIdentity[stored.icon] ?: return@forEach
            if (stored.fieldsConfig == seed.fieldsConfig) {
                recordSynced(stored.icon, seed.fieldsConfig)
                return@forEach
            }
            val lastSynced = syncPrefs?.getString(syncKey(stored.icon), null)
            if (lastSynced != null && stored.fieldsConfig == lastSynced) {
                templateRepo.updateTemplate(
                    stored.copy(fieldsConfig = seed.fieldsConfig, updatedAt = System.currentTimeMillis())
                )
                recordSynced(stored.icon, seed.fieldsConfig)
            } else if (lastSynced == null) {
                // 首次建立基线：把当前值记为「上次写入值」。本次不更新（无法区分旧种子与用户自定义）。
                recordSynced(stored.icon, stored.fieldsConfig)
            }
        }
    }

    /**
     * 存量安装：把内置模板的身份色与 isSpecial 同步为种子值（A6 / A7，定案 26 / §5.0）。
     * P1 起颜色有编辑入口：用户在编辑器改过的模板（customized 集合内）跳过同步。
     */
    private suspend fun syncBuiltinStaticProps() {
        val existing = templateRepo.getAllTemplates().first()
        val seedByIdentity = lifeTemplateSeeds.associateBy { it.icon }
        val customized = customizedTemplateIds()
        existing.filter { it.isBuiltin && it.id !in customized }.forEach { stored ->
            val seed = seedByIdentity[stored.icon] ?: return@forEach
            if (stored.color != seed.color || stored.isSpecial != seed.isSpecial) {
                templateRepo.updateTemplate(
                    stored.copy(color = seed.color, isSpecial = seed.isSpecial, updatedAt = System.currentTimeMillis())
                )
            }
        }
    }

    private fun recordSynced(icon: String, config: String) {
        val prefs = syncPrefs ?: return
        if (prefs.getString(syncKey(icon), null) != config) {
            prefs.edit().putString(syncKey(icon), config).apply()
        }
    }

    private fun syncKey(icon: String) = "fields_config:$icon"

    // ============================================================
    // 种子定义（§5.0 表 / §14.11）
    // ============================================================

    private fun flowJson(vararg statuses: Pair<String, String>, detailMode: String = "ITEM") =
        """{"detailMode":"$detailMode","defaultStatus":"ACTIVE","statuses":[{"key":"ACTIVE","label":"进行中"}, ${statuses.joinToString(", ") { """{"key":"${it.first}","label":"${it.second}"}""" } ?: ""}]}"""

    internal val lifeTemplateSeeds: List<LifeTemplate> = listOf(
        // ── 1 存钱计划（§5.0 #1）：currentAmount 开进度 + progressTargetKey + REMAINING 派生 + 凭证 IMAGE ──
        buildTemplate("存钱计划", "计划", "savings", "设定存款目标，记录储蓄进度", 1,
            """[{"key":"targetAmount","label":"目标金额","type":"NUMBER","required":false,"unit":"元","showInCard":true,"sortOrder":1},{"key":"currentAmount","label":"已存金额","type":"NUMBER","required":false,"unit":"元","showInCard":true,"showAsProgress":true,"progressTargetKey":"targetAmount","step":500,"sortOrder":2},{"key":"deadline","label":"目标日期","type":"DATE","required":false,"showInCard":true,"sortOrder":3},{"key":"remain","label":"还差","type":"REMAINING","options":["targetAmount","currentAmount"],"showInCard":true,"sortOrder":4},{"key":"voucher","label":"凭证","type":"IMAGE","showInCard":false,"sortOrder":5}]""",
            """["card","list","timeline"]""", flowJson("COMPLETED" to "已完成", "ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["TODO","NOTE"]}"""),
        // ── 2 购物计划（§14.3）：spent 进度 + items_detail 明细表（定案 20：旧 items 保留只读）──
        buildTemplate("购物计划", "计划", "shopping_cart", "管理购物清单和预算", 2,
            """[{"key":"budget","label":"预算金额","type":"NUMBER","required":false,"unit":"元","showInCard":true,"sortOrder":1},{"key":"spent","label":"已花费","type":"NUMBER","required":false,"unit":"元","showInCard":true,"showAsProgress":true,"progressTargetKey":"budget","sortOrder":2},{"key":"store","label":"店铺","type":"TEXT","required":false,"showInCard":false,"sortOrder":3},{"key":"items","label":"购物项目","type":"TEXT","required":false,"showInCard":false,"sortOrder":4},{"key":"category","label":"分类","type":"MULTI_SELECT","options":["日用","服饰","数码","食品","其他"],"showInCard":false,"sortOrder":5},{"key":"items_detail","label":"购物明细","type":"TABLE","options":["name:品名:TEXT","price:单价:CURRENCY","qty:数量:NUMBER","bought:已买:BOOLEAN"],"showInCard":true,"sortOrder":6},{"key":"photo","label":"商品图","type":"IMAGE","showInCard":false,"sortOrder":7}]""",
            """["card","list"]""", flowJson("COMPLETED" to "已完成", "ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["TODO","NOTE"]}"""),
        // ── 3 待办（§5.0 #3，原无字段）：deadline chips + subtasks 清单；detailMode=PLAN ──
        buildTemplate("待办", "计划", "checklist", "管理日常待办事项", 3,
            """[{"key":"deadline","label":"截止日期","type":"DATE","required":false,"showInCard":true,"sortOrder":1},{"key":"subtasks","label":"子任务","type":"CHECKLIST","showInCard":true,"sortOrder":2}]""",
            """["list","card"]""", flowJson("COMPLETED" to "已完成", "ARCHIVED" to "已归档", detailMode = "PLAN"),
            """{"allowCrossLink":true,"targetTypes":["NOTE","MOMENT"]}"""),
        // ── 4 旅行计划（§14.4）：companions PERSON + itinerary TABLE + photos IMAGE + route MAP ──
        buildTemplate("旅行计划", "计划", "flight", "规划旅行，记录美好回忆", 4,
            """[{"key":"destination","label":"目的地","type":"SHORT_TEXT","required":true,"showInCard":true,"sortOrder":1},{"key":"startDate","label":"出发日期","type":"DATE","required":true,"showInCard":true,"sortOrder":2},{"key":"endDate","label":"返程日期","type":"DATE","required":false,"showInCard":false,"sortOrder":3},{"key":"budget","label":"预算","type":"NUMBER","required":false,"unit":"元","showInCard":true,"sortOrder":4},{"key":"companions","label":"同行人","type":"PERSON","showInCard":false,"sortOrder":5},{"key":"itinerary","label":"行程明细","type":"TABLE","options":["day:天:TEXT","place:地点:TEXT","traffic:交通:TEXT","cost:费用:CURRENCY"],"showInCard":true,"sortOrder":6},{"key":"photos","label":"照片","type":"IMAGE","showInCard":false,"sortOrder":7},{"key":"route","label":"路线","type":"MAP","showInCard":false,"sortOrder":8}]""",
            """["card","list","timeline"]""", flowJson("COMPLETED" to "已完成", "ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["TODO","NOTE"]}"""),
        // ── 5 阅读：currentPage 进度 + rating + cover ──
        buildTemplate("阅读", "计划", "menu_book", "记录阅读进度", 6,
            """[{"key":"totalPages","label":"总页数","type":"NUMBER","required":false,"unit":"页","showInCard":true,"sortOrder":1},{"key":"currentPage","label":"当前页数","type":"NUMBER","required":false,"unit":"页","showInCard":true,"showAsProgress":true,"progressTargetKey":"totalPages","sortOrder":2},{"key":"author","label":"作者","type":"TEXT","required":false,"showInCard":false,"sortOrder":3},{"key":"rating","label":"评分","type":"RATING","showInCard":true,"sortOrder":4},{"key":"cover","label":"封面","type":"IMAGE","showInCard":false,"sortOrder":5},{"key":"excerpt","label":"书摘","type":"RICH_TEXT","showInCard":false,"sortOrder":6}]""",
            """["card","list","timeline"]""", flowJson("COMPLETED" to "已完成", "ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["NOTE","TODO"]}"""),
        // ── 6 学习计划：completedLessons 进度 + duration + lessons 清单；detailMode=PLAN ──
        buildTemplate("学习计划", "计划", "school", "添加课程，追踪学习进度", 5,
            """[{"key":"courseName","label":"课程名称","type":"TEXT","required":true,"showInCard":true,"sortOrder":1},{"key":"totalLessons","label":"总节数","type":"NUMBER","required":false,"unit":"节","showInCard":true,"sortOrder":2},{"key":"completedLessons","label":"完成节数","type":"NUMBER","required":false,"unit":"节","showInCard":true,"showAsProgress":true,"progressTargetKey":"totalLessons","sortOrder":3},{"key":"duration","label":"单次时长","type":"DURATION","required":false,"unit":"分钟","showInCard":false,"sortOrder":4},{"key":"lessons","label":"课时清单","type":"CHECKLIST","showInCard":false,"sortOrder":5},{"key":"notes","label":"笔记","type":"RICH_TEXT","showInCard":false,"sortOrder":6}]""",
            """["card","list","timeline"]""", flowJson("COMPLETED" to "已完成", "ARCHIVED" to "已归档", detailMode = "PLAN"),
            """{"allowCrossLink":true,"targetTypes":["TODO","NOTE"]}"""),
        // ── 7 倒计时：remainDays ELAPSED 派生 + 配图 ──
        buildTemplate("倒计时", "时间", "timer_off", "记录重要事件倒计时", 14,
            """[{"key":"targetDate","label":"目标日期","type":"DATE","required":true,"showInCard":true,"sortOrder":1},{"key":"reminder","label":"提醒","type":"BOOLEAN","required":false,"defaultValue":"true","showInCard":true,"sortOrder":2},{"key":"remainDays","label":"剩余天数","type":"ELAPSED","options":["targetDate"],"showInCard":true,"sortOrder":3},{"key":"photo","label":"配图","type":"IMAGE","showInCard":false,"sortOrder":4}]""",
            """["card","list"]""", flowJson("COMPLETED" to "已完成", "ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["NOTE","TODO"]}"""),
        // ── 8 正数日：elapsedDays ELAPSED 派生 + 配图 ──
        buildTemplate("正数日", "时间", "trending_up", "记录一个值得纪念的起点", 15,
            """[{"key":"start_date","label":"起始日期","type":"DATE","required":true,"showInCard":true,"sortOrder":1},{"key":"elapsedDays","label":"已经过","type":"ELAPSED","options":["start_date"],"showInCard":true,"sortOrder":2},{"key":"photo","label":"配图","type":"IMAGE","showInCard":false,"sortOrder":3}]""",
            """["card","list"]""", flowJson("COMPLETED" to "已完成", "ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["NOTE","TODO"]}"""),
        // ── 9 生日：person + avatar + lunar ──
        buildTemplate("生日", "时间", "cake", "记录重要日期", 13,
            """[{"key":"date","label":"日期","type":"DATE","required":true,"showInCard":true,"sortOrder":1},{"key":"reminder","label":"提醒","type":"BOOLEAN","required":false,"defaultValue":"true","showInCard":true,"sortOrder":2},{"key":"person","label":"人物","type":"PERSON","showInCard":false,"sortOrder":3},{"key":"avatar","label":"头像","type":"IMAGE","showInCard":false,"sortOrder":4},{"key":"lunar","label":"农历","type":"BOOLEAN","showInCard":false,"sortOrder":5}]""",
            """["card","list"]""", flowJson("ARCHIVED" to "已归档"),
            """{"allowCrossLink":false,"targetTypes":[]}"""),
        // ── 10 纪念日：person + 相册 ──
        buildTemplate("纪念日", "时间", "celebration", "记录那些值得铭记的日子", 16,
            """[{"key":"date","label":"日期","type":"DATE","required":true,"showInCard":true,"sortOrder":1},{"key":"note","label":"备注","type":"TEXT","required":false,"showInCard":false,"sortOrder":2},{"key":"person","label":"人物","type":"PERSON","showInCard":false,"sortOrder":3},{"key":"photos","label":"相册","type":"IMAGE","showInCard":false,"sortOrder":4}]""",
            """["card","list"]""", flowJson("ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["NOTE","TODO"]}"""),
        // ── 11 打卡：currentStreak 进度（STREAK 派生待 P4 聚合层）──
        buildTemplate("打卡", "记录", "calendar_month", "记录每日打卡习惯", 7,
            """[{"key":"targetDays","label":"目标天数","type":"NUMBER","required":false,"unit":"天","showInCard":false,"sortOrder":1},{"key":"currentStreak","label":"连续天数","type":"NUMBER","required":false,"unit":"天","showInCard":true,"showAsProgress":true,"progressTargetKey":"targetDays","sortOrder":2}]""",
            """["card","list"]""", flowJson("PAUSED" to "已暂停", "COMPLETED" to "已完成", "ARCHIVED" to "已归档"),
            """{"allowCrossLink":false,"targetTypes":[]}"""),
        // ── 12 心情（原无字段）：mood chips + energy 拖动 + factors + note ──
        buildTemplate("心情", "记录", "mood", "记录每日心情", 8,
            """[{"key":"mood","label":"心情","type":"SELECT","options":["开心","平静","疲惫","难过","焦虑"],"showInCard":true,"sortOrder":1},{"key":"energy","label":"精力","type":"SLIDER","min":0.0,"max":100.0,"step":5.0,"showInCard":true,"sortOrder":2},{"key":"factors","label":"影响因素","type":"MULTI_SELECT","options":["工作","学习","健康","家庭","社交","天气"],"showInCard":false,"sortOrder":3},{"key":"note","label":"备注","type":"RICH_TEXT","showInCard":false,"sortOrder":4}]""",
            """["card","list"]""", flowJson("ARCHIVED" to "已归档"),
            """{"allowCrossLink":false,"targetTypes":[]}"""),
        // ── 13 日记（原无字段）：weather + mood + photos + content + location ──
        buildTemplate("日记", "记录", "book", "记录每日心情和想法", 9,
            """[{"key":"weather","label":"天气","type":"SELECT","options":["晴","阴","雨","雪"],"showInCard":true,"sortOrder":1},{"key":"mood","label":"心情","type":"SELECT","options":["开心","平静","疲惫","难过","焦虑"],"showInCard":true,"sortOrder":2},{"key":"content","label":"正文","type":"RICH_TEXT","showInCard":false,"sortOrder":3},{"key":"photos","label":"配图","type":"IMAGE","showInCard":false,"sortOrder":4},{"key":"location","label":"地点","type":"LOCATION","showInCard":false,"sortOrder":5}]""",
            """["card","list"]""", flowJson("ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["MOMENT","TODO","NOTE"]}"""),
        // ── 14 订阅记录：周期 chips（修 monthly 直出）+ daysToBilling ELAPSED + URL ──
        buildTemplate("订阅记录", "记录", "subscriptions", "管理你的订阅服务", 10,
            """[{"key":"price","label":"扣费金额","type":"NUMBER","required":false,"unit":"元","showInCard":true,"sortOrder":1},{"key":"billingCycle","label":"扣费周期","type":"SELECT","required":true,"options":["monthly","quarterly","yearly"],"showInCard":true,"sortOrder":2},{"key":"billingDay","label":"扣费日","type":"NUMBER","required":false,"unit":"号","showInCard":false,"sortOrder":3},{"key":"nextBilling","label":"下次扣费","type":"DATE","required":false,"showInCard":true,"sortOrder":4},{"key":"daysToBilling","label":"距扣费","type":"ELAPSED","options":["nextBilling"],"showInCard":true,"sortOrder":5},{"key":"url","label":"管理链接","type":"URL","showInCard":false,"sortOrder":6}]""",
            """["card","list"]""", flowJson("PAUSED" to "已暂停", "ARCHIVED" to "已归档"),
            """{"allowCrossLink":true,"targetTypes":["BILL"]}"""),
        // ── 15 周报月报（系统型，保持无字段）──
        buildTemplate("周报月报", "记录", "BarChart", "自动聚合生活数据生成报告", 12, "[]", """["STATS"]""", "{}", "{}", isSpecial = true),
        // ── 16 专注（系统型，保持无字段）──
        buildTemplate("专注", "记录", "timer", "番茄钟专注计时", 11, "[]", """["card","list"]""", "{}", "{}", isSpecial = true),
        // ── 17 身体记录（新增 A，§5.3；定案 25）：体重步进 + 睡眠 + BMI 派生 ──
        buildTemplate("身体记录", "记录", "fitness_center", "记录体重、睡眠与运动，健康看得见", 17,
            """[{"key":"weight","label":"体重","type":"NUMBER","required":false,"unit":"kg","min":20.0,"max":300.0,"step":0.1,"showInCard":true,"sortOrder":1},{"key":"sleep","label":"睡眠","type":"DURATION","required":false,"unit":"小时","showInCard":true,"sortOrder":2},{"key":"exercise","label":"运动","type":"CHECKLIST","showInCard":false,"sortOrder":3},{"key":"height","label":"身高","type":"NUMBER","required":false,"unit":"cm","min":50.0,"max":250.0,"showInCard":false,"sortOrder":4},{"key":"bmi","label":"BMI","type":"FORMULA","defaultValue":"weight / (height * height / 10000)","showInCard":true,"sortOrder":5},{"key":"nextCheckup","label":"下次体检","type":"DATE","showInCard":false,"sortOrder":6}]""",
            """["card","list"]""", flowJson("ARCHIVED" to "已归档"),
            """{"allowCrossLink":false,"targetTypes":[]}"""),
        // ── 18 物品维护（新增 B，§5.3；定案 25）：周期提醒，与「物品」资产台账互补 ──
        buildTemplate("物品维护", "计划", "build", "滤芯、牙刷等消耗品的更换周期提醒（区别于「物品」的资产台账）", 18,
            """[{"key":"item","label":"物品","type":"TEXT","required":true,"showInCard":true,"sortOrder":1},{"key":"boughtAt","label":"更换日期","type":"DATE","required":false,"showInCard":true,"sortOrder":2},{"key":"cycle","label":"更换周期","type":"DURATION","required":false,"unit":"天","showInCard":false,"sortOrder":3},{"key":"photo","label":"照片","type":"IMAGE","showInCard":false,"sortOrder":4}]""",
            """["card","list"]""", flowJson("ARCHIVED" to "已归档"),
            """{"allowCrossLink":false,"targetTypes":[]}"""),
    )

    private suspend fun seedTemplates() {
        lifeTemplateSeeds.forEach { templateRepo.insertTemplate(it) }
    }

    private fun buildTemplate(name: String, category: String, icon: String, description: String, sortOrder: Int, fieldsConfig: String, availableLayouts: String, statusFlowConfig: String, linkConfig: String, isHidden: Boolean = false, isSpecial: Boolean = false) = LifeTemplate(
        name = name, category = category, icon = icon, color = lifeTemplateIdentityHex(icon) ?: "#8A8580", description = description,
        fieldsConfig = fieldsConfig, layoutType = "card", availableLayouts = availableLayouts,
        statusFlowConfig = statusFlowConfig, linkConfig = linkConfig, isBuiltin = true,
        isHidden = isHidden, isSpecial = isSpecial, sortOrder = sortOrder
    )
}
