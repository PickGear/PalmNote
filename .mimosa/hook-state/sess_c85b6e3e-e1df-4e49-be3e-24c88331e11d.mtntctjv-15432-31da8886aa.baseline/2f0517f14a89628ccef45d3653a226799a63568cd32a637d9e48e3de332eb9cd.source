package com.palmnote.data

import androidx.room.withTransaction
import com.palmnote.data.db.AppDatabase
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.repository.LifeTemplateRepository
import kotlinx.coroutines.flow.first
class LifeDataSeeder(
    private val templateRepo: LifeTemplateRepository,
    private val appDatabase: AppDatabase
) {
    suspend fun seedIfEmpty() {
        val existingTemplates = templateRepo.getAllTemplates().first()
        if (existingTemplates.isEmpty()) {
            appDatabase.withTransaction {
                seedTemplates()
            }
        } else {
            refreshBuiltinFieldsConfigs()
        }
    }

    /** 存量安装:把内置模板的 fieldsConfig 同步为种子值(仅修复内置模板,不触碰用户自定义模板)。 */
    private suspend fun refreshBuiltinFieldsConfigs() {
        val existing = templateRepo.getAllTemplates().first()
        val seedByIdentity = lifeTemplateSeeds.associateBy { it.icon }
        existing.filter { it.isBuiltin && !it.isSpecial }.forEach { stored ->
            val seed = seedByIdentity[stored.icon] ?: return@forEach
            if (stored.fieldsConfig != seed.fieldsConfig) {
                templateRepo.updateTemplate(
                    stored.copy(fieldsConfig = seed.fieldsConfig, updatedAt = System.currentTimeMillis())
                )
            }
        }
    }

    internal val lifeTemplateSeeds: List<LifeTemplate> = listOf(
        buildTemplate("\u5B58\u94B1\u8BA1\u5212", "\u8BA1\u5212", "savings", "#4CAF50", "\u8BBE\u5B9A\u5B58\u6B3E\u76EE\u6807\uFF0C\u8BB0\u5F55\u50A8\u84C4\u8FDB\u5EA6", 1, """[{"key":"targetAmount","label":"\u76EE\u6807\u91D1\u989D","type":"NUMBER","required":true,"unit":"\u5143","showInCard":true,"sortOrder":1},{"key":"currentAmount","label":"\u5DF2\u5B58\u91D1\u989D","type":"NUMBER","required":true,"unit":"\u5143","showInCard":true,"sortOrder":2},{"key":"deadline","label":"\u76EE\u6807\u65E5\u671F","type":"DATE","required":false,"showInCard":true,"sortOrder":3}]""", "[\"card\",\"list\",\"timeline\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["TODO","NOTE"]}"""),
        buildTemplate("\u8D2D\u7269\u8BA1\u5212", "\u8BA1\u5212", "shopping_cart", "#FF9800", "\u7BA1\u7406\u8D2D\u7269\u6E05\u5355\u548C\u9884\u7B97", 2, """[{"key":"budget","label":"\u9884\u7B97\u91D1\u989D","type":"NUMBER","required":false,"unit":"\u5143","showInCard":true,"sortOrder":1},{"key":"spent","label":"\u5DF2\u82B1\u8D39","type":"NUMBER","required":false,"unit":"\u5143","showInCard":true,"sortOrder":2},{"key":"store","label":"\u5E97\u94FA","type":"TEXT","required":false,"showInCard":false,"sortOrder":3},{"key":"items","label":"\u8D2D\u7269\u9879\u76EE","type":"TEXT","required":false,"showInCard":false,"sortOrder":4}]""", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["TODO","NOTE"]}"""),
        buildTemplate("\u5F85\u529E", "\u8BA1\u5212", "checklist", "#5C6BC0", "\u7BA1\u7406\u65E5\u5E38\u5F85\u529E\u4E8B\u9879", 3, "[]", "[\"list\",\"card\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["NOTE","MOMENT"]}"""),
        buildTemplate("\u65C5\u884C\u8BA1\u5212", "\u8BA1\u5212", "flight", "#FF7043", "\u89C4\u5212\u65C5\u884C\uFF0C\u8BB0\u5F55\u7F8E\u597D\u56DE\u5FC6", 4, """[{"key":"destination","label":"\u76EE\u7684\u5730","type":"TEXT","required":true,"showInCard":true,"sortOrder":1},{"key":"startDate","label":"\u51FA\u53D1\u65E5\u671F","type":"DATE","required":true,"showInCard":true,"sortOrder":2},{"key":"endDate","label":"\u8FD4\u7A0B\u65E5\u671F","type":"DATE","required":false,"showInCard":false,"sortOrder":3},{"key":"budget","label":"\u9884\u7B97","type":"NUMBER","required":false,"unit":"\u5143","showInCard":false,"sortOrder":4}]""", "[\"card\",\"list\",\"timeline\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["TODO","NOTE"]}"""),
        buildTemplate("\u9605\u8BFB", "\u8BA1\u5212", "menu_book", "#26A69A", "\u8BB0\u5F55\u9605\u8BFB\u8FDB\u5EA6", 7, """[{"key":"totalPages","label":"\u603B\u9875\u6570","type":"NUMBER","required":true,"unit":"\u9875","showInCard":true,"sortOrder":1},{"key":"currentPage","label":"\u5F53\u524D\u9875\u6570","type":"NUMBER","required":true,"unit":"\u9875","showInCard":true,"sortOrder":2},{"key":"author","label":"\u4F5C\u8005","type":"TEXT","required":false,"showInCard":false,"sortOrder":3}]""", "[\"card\",\"list\",\"timeline\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["NOTE","TODO"]}"""),
        buildTemplate("\u5B66\u4E60\u8BA1\u5212", "\u8BA1\u5212", "school", "#AB47BC", "\u6DFB\u52A0\u8BFE\u7A0B\uFF0C\u8FFD\u8E2A\u5B66\u4E60\u8FDB\u5EA6", 5, """[{"key":"courseName","label":"\u8BFE\u7A0B\u540D\u79F0","type":"TEXT","required":true,"showInCard":true,"sortOrder":1},{"key":"totalLessons","label":"\u603B\u8282\u6570","type":"NUMBER","required":true,"unit":"\u8282","showInCard":true,"sortOrder":2},{"key":"completedLessons","label":"\u5B8C\u6210\u8282\u6570","type":"NUMBER","required":true,"unit":"\u8282","showInCard":true,"sortOrder":3}]""", "[\"card\",\"list\",\"timeline\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["TODO","NOTE"]}"""),
        buildTemplate("\u5012\u8BA1\u65F6", "\u65F6\u95F4", "timer_off", "#3F51B5", "\u8BB0\u5F55\u91CD\u8981\u4E8B\u4EF6\u5012\u8BA1\u65F6", 11, """[{"key":"targetDate","label":"\u76EE\u6807\u65E5\u671F","type":"DATE","required":true,"showInCard":true,"sortOrder":1},{"key":"reminder","label":"\u63D0\u9192","type":"BOOLEAN","required":false,"defaultValue":"true","showInCard":true,"sortOrder":2}]""", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["NOTE","TODO"]}"""),
        buildTemplate("\u6B63\u6570\u65E5", "\u65F6\u95F4", "trending_up", "#50C890", "\u8BB0\u5F55\u4E00\u4E2A\u503C\u5F97\u7EAA\u5FF5\u7684\u8D77\u70B9", 12, """[{"key":"start_date","label":"\u8D77\u59CB\u65E5\u671F","type":"DATE","required":true,"showInCard":true,"sortOrder":1}]""", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["NOTE","TODO"]}"""),
        buildTemplate("\u751F\u65E5", "\u65F6\u95F4", "cake", "#FF5722", "\u8BB0\u5F55\u91CD\u8981\u65E5\u671F", 10, """[{"key":"date","label":"\u65E5\u671F","type":"DATE","required":true,"showInCard":true,"sortOrder":1},{"key":"reminder","label":"\u63D0\u9192","type":"BOOLEAN","required":false,"defaultValue":"true","showInCard":true,"sortOrder":2}]""", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","ARCHIVED"]}""", """{"allowCrossLink":false,"targetTypes":[]}"""),
        buildTemplate("\u7EAA\u5FF5\u65E5", "\u65F6\u95F4", "celebration", "#F07070", "\u8BB0\u5F55\u90A3\u4E9B\u503C\u5F97\u94ED\u8BB0\u7684\u65E5\u5B50", 13, """[{"key":"date","label":"\u65E5\u671F","type":"DATE","required":true,"showInCard":true,"sortOrder":1},{"key":"note","label":"\u5907\u6CE8","type":"TEXT","required":false,"showInCard":false,"sortOrder":2}]""", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["NOTE","TODO"]}"""),
        buildTemplate("\u6253\u5361", "\u8BB0\u5F55", "calendar_month", "#9C27B0", "\u8BB0\u5F55\u6BCF\u65E5\u6253\u5361\u4E60\u60EF", 4, """[{"key":"targetDays","label":"\u76EE\u6807\u5929\u6570","type":"NUMBER","required":false,"unit":"\u5929","showInCard":false,"sortOrder":1},{"key":"currentStreak","label":"\u8FDE\u7EED\u5929\u6570","type":"NUMBER","required":false,"unit":"\u5929","showInCard":true,"sortOrder":2}]""", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","PAUSED","COMPLETED","ARCHIVED"]}""", """{"allowCrossLink":false,"targetTypes":[]}"""),
        buildTemplate("\u5FC3\u60C5", "\u8BB0\u5F55", "mood", "#FFCA28", "\u8BB0\u5F55\u6BCF\u65E5\u5FC3\u60C5", 5, "[]", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","ARCHIVED"]}""", """{"allowCrossLink":false,"targetTypes":[]}"""),
        buildTemplate("\u65E5\u8BB0", "\u8BB0\u5F55", "book", "#AB47BC", "\u8BB0\u5F55\u6BCF\u65E5\u5FC3\u60C5\u548C\u60F3\u6CD5", 6, "[]", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["MOMENT","TODO","NOTE"]}"""),
        buildTemplate("\u8BA2\u9605\u8BB0\u5F55", "\u8BB0\u5F55", "subscriptions", "#66BB6A", "\u7BA1\u7406\u4F60\u7684\u8BA2\u9605\u670D\u52A1", 8, """[{"key":"price","label":"\u6263\u8D39\u91D1\u989D","type":"NUMBER","required":true,"unit":"\u5143","showInCard":true,"sortOrder":1},{"key":"billingCycle","label":"\u6263\u8D39\u5468\u671F","type":"SELECT","required":true,"options":["monthly","quarterly","yearly"],"showInCard":true,"sortOrder":2},{"key":"billingDay","label":"\u6263\u8D39\u65E5","type":"NUMBER","required":true,"unit":"\u53F7","showInCard":false,"sortOrder":3},{"key":"nextBilling","label":"\u4E0B\u6B21\u6263\u8D39","type":"DATE","required":false,"showInCard":true,"sortOrder":4}]""", "[\"card\",\"list\"]", """{"defaultStatus":"ACTIVE","statuses":["ACTIVE","PAUSED","ARCHIVED"]}""", """{"allowCrossLink":true,"targetTypes":["BILL"]}"""),
        buildTemplate("\u5468\u62A5\u6708\u62A5", "\u8BB0\u5F55", "BarChart", "#42A5F5", "\u81EA\u52A8\u805A\u5408\u751F\u6D3B\u6570\u636E\u751F\u6210\u62A5\u544A", 15, "[]", "[\"STATS\"]", "{}", "{}"),
    )

    private suspend fun seedTemplates() {
        lifeTemplateSeeds.forEach { templateRepo.insertTemplate(it) }
    }

    private fun buildTemplate(name: String, category: String, icon: String, color: String, description: String, sortOrder: Int, fieldsConfig: String, availableLayouts: String, statusFlowConfig: String, linkConfig: String, isHidden: Boolean = false) = LifeTemplate(
        name = name, category = category, icon = icon, color = color, description = description,
        fieldsConfig = fieldsConfig, layoutType = "card", availableLayouts = availableLayouts,
        statusFlowConfig = statusFlowConfig, linkConfig = linkConfig, isBuiltin = true,
        isHidden = isHidden, isSpecial = false, sortOrder = sortOrder
    )
}