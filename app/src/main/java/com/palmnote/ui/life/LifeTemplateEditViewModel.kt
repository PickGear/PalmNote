package com.palmnote.ui.life

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.repository.LifeTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 模板编辑器（设计稿 ed_2～ed_6 / ed_9，总纲 §四）。
 *
 * 单栏编排器：字段列表 + 「添加字段」+ 模板元信息（名称 / 图标与颜色 / 分类）。
 * 字段增删改全部在内存草稿里完成，保存时才落库（insert / update）。
 *
 * 三条硬规矩（来自 §4.1 / ed_9）：
 * 1. **内置模板的字段 key 与 type 只读**——想改结构就「另存为新模板」；
 * 2. **内置且有数据的字段不能删，只能停用**（`disabled`，历史数据保留、不再渲染）；
 * 3. **保存前校验**：文本字段 > 2 个、或必填字段无默认值 → 阻断；卡片色对比度不足 /
 *    圆环族进度落到卡片流 → 只提示不阻断。
 */
@HiltViewModel
class LifeTemplateEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: LifeTemplateRepository,
    private val itemDao: LifeItemDao,
    private val seeder: LifeDataSeeder
) : ViewModel() {

    private val templateId: Long = (savedStateHandle.get<Any>("templateId") as? Number)?.toLong() ?: 0L

    /** 编辑器草稿（全部内存态，保存才落库）。 */
    data class EditorState(
        val name: String = "",
        val description: String = "",
        val category: String = "",
        val icon: String = "savings",
        val color: String = "#EC407A",
        val fields: List<FieldConfig> = emptyList(),
        val isBuiltin: Boolean = false,
        val existingId: Long? = null,
        /** 当前模板下的用户记录数（决定内置字段能否删除）。 */
        val itemCount: Int = 0,
        val saved: Boolean = false,
        val loadError: String? = null
    )

    /** 保存前校验结果（ed_9）。 */
    data class Validation(
        val blocks: List<String> = emptyList(),
        val advisories: List<String> = emptyList()
    ) {
        val canSave: Boolean get() = blocks.isEmpty()
    }

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        if (templateId != 0L) load(templateId)
    }

    private fun load(id: Long) {
        viewModelScope.launch {
            val tpl = repo.getTemplateById(id)
            if (tpl == null) {
                _state.update { it.copy(loadError = "模板未找到") }
                return@launch
            }
            val configs = runCatching { JSON.decodeFromString<List<FieldConfig>>(tpl.fieldsConfig) }
                .getOrNull().orEmpty()
            val count = itemDao.getItemCountByTemplate(id).first()
            _state.update {
                it.copy(
                    name = tpl.name,
                    description = tpl.description,
                    category = tpl.category,
                    icon = tpl.icon,
                    color = tpl.color,
                    fields = configs,
                    isBuiltin = tpl.isBuiltin,
                    existingId = tpl.id,
                    itemCount = count
                )
            }
        }
    }

    // ---- 元信息编辑 ----
    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setCategory(v: String) = _state.update { it.copy(category = v) }
    fun setIcon(v: String) = _state.update { it.copy(icon = v) }
    fun setColor(v: String) = _state.update { it.copy(color = v) }

    // ---- 字段编辑 ----
    /** 从字段库添加一种类型（ed_5）：生成默认 key，追加到末尾。 */
    fun addField(type: FieldType) {
        _state.update { s ->
            val idx = s.fields.count { !it.disabled } + 1
            val key = "f$idx${type.name.lowercase().firstOrNull()?.let { "_$it" } ?: ""}"
            val cfg = FieldConfig(
                key = uniqueKey(s.fields, key),
                label = defaultLabel(type),
                type = type,
                sortOrder = s.fields.size
            )
            s.copy(fields = s.fields + cfg)
        }
    }

    fun updateField(index: Int, cfg: FieldConfig) {
        _state.update { s ->
            if (index !in s.fields.indices) s
            else s.copy(fields = s.fields.toMutableList().also { it[index] = cfg })
        }
    }

    /** 删除字段（ed_3）：自定义/无数据 → 真删；内置且有数据 → 只停用。 */
    fun removeOrDisableField(index: Int) {
        _state.update { s ->
            if (index !in s.fields.indices) s
            val cfg = s.fields[index]
            if (s.isBuiltin && s.itemCount > 0) {
                // 有数据的内置字段：只能停用
                s.copy(fields = s.fields.toMutableList().also { it[index] = cfg.copy(disabled = true) })
            } else {
                s.copy(fields = s.fields.toMutableList().also { it.removeAt(index) })
            }
        }
    }

    /** 字段上移 / 下移（ed_2 拖拽手柄的简化：用按钮调整顺序）。 */
    fun moveField(index: Int, delta: Int) {
        _state.update { s ->
            val target = index + delta
            if (index !in s.fields.indices || target !in s.fields.indices) s
            else {
                val list = s.fields.toMutableList()
                val a = list[index]; list[index] = list[target]; list[target] = a
                s.copy(fields = list.mapIndexed { i, c -> c.copy(sortOrder = i) })
            }
        }
    }

    // ---- 保存前校验（ed_9） ----
    fun validate(): Validation {
        val s = _state.value
        val blocks = mutableListOf<String>()
        val advisories = mutableListOf<String>()
        val active = s.fields.filter { !it.disabled }
        // 硬阻断 1：文本字段预算（上限 2）
        val textCount = active.count { it.type in TEXT_TYPES }
        if (textCount > TEXT_BUDGET) {
            blocks += "文本字段超过 $TEXT_BUDGET 个（当前 $textCount 个）——卡片放不下"
        }
        // 硬阻断 2：必填且无默认值
        active.firstOrNull { it.required && it.defaultValue.isBlank() }?.let {
            blocks += "必填字段「${it.label}」没有默认值——用户无法留空"
        }
        // 提示：圆环族进度落到卡片流（§4.2 降级表）
        if (active.any { it.showAsProgress && ringFamily(it.progressStyle) }) {
            advisories += "圆环族进度会在卡片流自动换成横向（不报错、不阻断）"
        }
        // 提示：卡片色对比度（仅做粗略判断：过浅的色在浅底上对比不足）
        if (s.color.isNotBlank() && !contrastOk(s.color)) {
            advisories += "卡片颜色对比度可能不足（建议更深一点）"
        }
        return Validation(blocks, advisories)
    }

    // ---- 保存 ----
    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        val v = validate()
        if (!v.canSave) return
        viewModelScope.launch {
            val fieldsJson = Json { ignoreUnknownKeys = true }.encodeToString(s.fields)
            val id = if (s.existingId != null) {
                val existing = repo.getTemplateById(s.existingId) ?: return@launch
                repo.updateTemplate(existing.copy(
                    name = s.name.ifBlank { existing.name },
                    description = s.description,
                    category = s.category.ifBlank { existing.category },
                    icon = s.icon,
                    color = s.color,
                    fieldsConfig = fieldsJson,
                    updatedAt = System.currentTimeMillis()
                ))
                // 内置模板被改过 → 标记已自定义（§4.1）
                if (existing.isBuiltin) seeder.markCustomized(existing.id)
                existing.id
            } else {
                val maxSort = repo.getAllTemplates().first().maxOfOrNull { it.sortOrder } ?: 0
                repo.insertTemplate(LifeTemplate(
                    name = s.name.ifBlank { "未命名模板" },
                    category = s.category.ifBlank { "自定义" },
                    icon = s.icon,
                    color = s.color,
                    description = s.description,
                    fieldsConfig = fieldsJson,
                    layoutType = "card",
                    availableLayouts = "[\"card\",\"list\"]",
                    statusFlowConfig = "",
                    linkConfig = "",
                    isBuiltin = false,
                    sortOrder = maxSort + 1
                ))
            }
            _state.update { it.copy(saved = true) }
            onSaved(id)
        }
    }

    /** 删除自定义模板（ed_8）：关联删除其记录。内置模板不可删。 */
    fun deleteTemplate(onDeleted: () -> Unit) {
        val s = _state.value
        val id = s.existingId ?: return
        if (s.isBuiltin) return
        viewModelScope.launch {
            repo.deleteTemplateCascade(id)
            onDeleted()
        }
    }

    private fun uniqueKey(fields: List<FieldConfig>, base: String): String {
        if (fields.none { it.key == base }) return base
        var i = 2
        while (fields.any { it.key == "$base$i" }) i++
        return "$base$i"
    }

    private fun defaultLabel(type: FieldType): String = when (type) {
        FieldType.TEXT -> "文本"
        FieldType.SHORT_TEXT -> "短文本"
        FieldType.RICH_TEXT -> "富文本"
        FieldType.NUMBER -> "数字"
        FieldType.CURRENCY -> "金额"
        FieldType.PERCENT -> "百分比"
        FieldType.DATE -> "日期"
        FieldType.TIME -> "时间"
        FieldType.DATETIME -> "日期时间"
        FieldType.BOOLEAN -> "开关"
        FieldType.SELECT -> "单选"
        FieldType.MULTI_SELECT -> "多选"
        FieldType.RATING -> "评分"
        FieldType.SLIDER -> "进度条"
        FieldType.EMAIL -> "邮箱"
        FieldType.PHONE -> "电话"
        FieldType.URL -> "网址"
        FieldType.COLOR -> "颜色"
        FieldType.DURATION -> "时长"
        FieldType.LOCATION -> "位置"
        FieldType.IMAGE -> "图片"
        FieldType.VIDEO -> "视频"
        FieldType.AUDIO -> "语音"
        FieldType.TAG -> "标签"
        FieldType.MAP -> "路线 / 地点"
        FieldType.CHECKLIST -> "清单"
        FieldType.TABLE -> "明细表"
        FieldType.RANGE -> "区间"
        FieldType.PERSON -> "人物"
        FieldType.FORMULA -> "公式"
        FieldType.REMAINING -> "差值"
        FieldType.STREAK -> "连续天数"
        FieldType.ELAPSED -> "已过 / 剩余天数"
        else -> type.name
    }

    private fun ringFamily(style: String?): Boolean {
        val upper = style?.uppercase() ?: "AUTO"
        if (upper == "AUTO") {
            // AUTO 时圆环族由语义默认推导；这里保守按「非厚胶囊/横向」判断
            return false
        }
        return upper.contains("RING")
    }

    /** 粗略对比度：解析 #RRGGBB，相对亮度 > 0.6 视为在浅底上对比不足。 */
    private fun contrastOk(hex: String): Boolean {
        val h = hex.removePrefix("#")
        if (h.length != 6) return true
        val r = h.substring(0, 2).toIntOrNull(16) ?: return true
        val g = h.substring(2, 4).toIntOrNull(16) ?: return true
        val b = h.substring(4, 6).toIntOrNull(16) ?: return true
        val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
        return lum <= 0.62
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        /** 算作「文本字段」计入预算的类型（ed_9 输入框预算）。 */
        val TEXT_TYPES = setOf(FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.RICH_TEXT)
        const val TEXT_BUDGET = 2
    }
}
