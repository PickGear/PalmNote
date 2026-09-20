package com.palmnote.ui.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.repository.LifeTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 模板管理页的数据源（设计稿 `ed_10` / `ed_11`，总纲 §4.8）。
 *
 * 三条硬规矩（都来自 §4.8，且都是「不做就会出错」的那类）：
 * 1. **必须读全量列表** `getAllTemplates()`（无 `isHidden` 过滤）——只读可见列表会让
 *    已关闭的模板从页面消失，而**全应用再无入口能重新开启它**（单向阀，§4.8(2)）；
 * 2. **开关不改写任何 `LifeItem`**，也不改布局里的 `visible`（软开关）——否则重开时
 *    无法还原用户做过的决定（§4.8(5)）；
 * 3. **恢复出厂不动开关**：只重写模板内容，「已自定义 + 已关闭」的叠加态要能显示出来（§4.8(6)）。
 */
@HiltViewModel
class LifeTemplateManageViewModel @Inject constructor(
    private val templateRepo: LifeTemplateRepository,
    private val itemDao: LifeItemDao,
    private val seeder: LifeDataSeeder
) : ViewModel() {

    /** 管理页一行：模板本身 + 三个派生量。 */
    data class TemplateRow(
        val id: Long,
        val name: String,
        val icon: String,
        val colorHex: String,
        val isBuiltin: Boolean,
        val isHidden: Boolean,
        /** 内置模板且被用户改过（`customizedTemplateIds`）——决定「已自定义」徽标。 */
        val customized: Boolean,
        val fieldCount: Int,
        val cardFieldCount: Int,
        /** **用户自己的**记录数（示例行不算）：决定关闭时要不要二次确认。 */
        val itemCount: Int
    )

    data class TemplateGroup(val category: String, val rows: List<TemplateRow>) {
        val closedCount: Int get() = rows.count { it.isHidden }
    }

    val groups: StateFlow<List<TemplateGroup>> =
        combine(
            templateRepo.getAllTemplates(),
            itemDao.getItemCountsByTemplate(LIFE_DEMO_META)
        ) { templates, counts ->
            val customized = seeder.customizedTemplateIds()
            val countByTemplate = counts.associate { it.templateId to it.cnt }
            templates
                .groupBy { it.category }
                .map { (category, list) ->
                    TemplateGroup(category, list.map { it.toRow(customized, countByTemplate[it.id] ?: 0) })
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 关闭 / 开启（硬开关）。零数据风险：不删记录、不动布局配置。 */
    fun setHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch { templateRepo.setTemplateHidden(id, hidden) }
    }

    /** 恢复出厂：只重写模板内容（`fieldsConfig` 等），**不碰记录、也不改开关状态**。 */
    fun restoreFactory(id: Long) {
        viewModelScope.launch { seeder.restoreToSeed(id) }
    }

    /** 删除自定义模板（ed_8）：关联删除其记录（仅自定义模板调用）。 */
    fun deleteTemplate(id: Long) {
        viewModelScope.launch { templateRepo.deleteTemplateCascade(id) }
    }

    private fun LifeTemplate.toRow(customized: Set<Long>, itemCount: Int): TemplateRow {
        val configs = runCatching { JSON.decodeFromString<List<FieldConfig>>(fieldsConfig) }
            .getOrNull().orEmpty()
        return TemplateRow(
            id = id,
            name = name,
            icon = icon,
            colorHex = color,
            isBuiltin = isBuiltin,
            isHidden = isHidden,
            customized = isBuiltin && id in customized,
            fieldCount = configs.size,
            cardFieldCount = configs.count { it.showInCard },
            itemCount = itemCount
        )
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
