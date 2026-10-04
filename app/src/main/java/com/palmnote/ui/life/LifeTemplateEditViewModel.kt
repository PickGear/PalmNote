package com.palmnote.ui.life

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.app.R
import com.palmnote.data.LifeDataSeeder
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldContracts
import com.palmnote.domain.model.FieldGroup
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.derivedUnconfigured
import com.palmnote.domain.model.ProgressForm
import com.palmnote.domain.model.ReminderSpec
import com.palmnote.domain.model.resolveProgressForm
import com.palmnote.domain.repository.LifeTemplateRepository
import com.palmnote.domain.util.BuiltinTemplates
import com.palmnote.domain.util.kindFromIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    @ApplicationContext private val appContext: Context,
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
        /** 历史记录里出现过的字段 key（决定内置字段能否删除）。 */
        val fieldKeysWithData: Set<String> = emptySet(),
        /** 用户动过草稿（供返回拦截：动过才弹「放弃更改」）。 */
        val dirty: Boolean = false,
        val saved: Boolean = false,
        val loadError: String? = null,
        /**
         * 字段库全屏页选中的待添加字段类型（跨页回传槽位）。
         * 字段库页与编辑页共用同一个 ViewModel 实例（按 NavBackStackEntry 作用域绑定），
         * 选中写这里、编辑页消费后立即清空，避免重复添加。
         */
        val pendingAddType: FieldType? = null,
        /** 模板级提醒配置（null = 未开启）。保存时写入 LifeTemplate.reminderConfig。 */
        val reminder: ReminderSpec? = null,
        /**
         * 「每年重复」（生日 / 纪念日这类）：开启后该模板记录的**距离天数**按下一次周年算。
         * 落点在读数而不是记录 —— 见 `LifeTemplate.repeatYearly` 的 KDoc。
         */
        val repeatYearly: Boolean = false
    )

    /** 保存前校验结果（ed_9）：结构化结论，文案由 UI 资源化。 */
    data class Validation(
        val blocks: List<Block> = emptyList(),
        val advisories: List<Advisory> = emptyList()
    ) {
        val canSave: Boolean get() = blocks.isEmpty()

        /** 硬阻断（保存置灰，ed_9）。 */
        sealed interface Block {
            /** 文本字段超出卡片输入预算。 */
            data class TextBudget(val count: Int) : Block
            /** 必填且无默认值。 */
            data class RequiredNoDefault(val label: String) : Block
            /**
             * 派生字段配不全 —— 它**一定算不出**，等于一张永远空着的行。
             *
             * 与 [RequiredNoDefault] 同类：都是"能存但存了没用"的配置错误。
             * 此前只有字段行上的一行红字，用户仍可先存下一个永远为空的行。
             */
            data class DerivedUnconfigured(val label: String) : Block
        }

        /** 软提示（只提示不阻断）。 */
        sealed interface Advisory {
            /** 圆环族进度落到卡片流自动换横向。 */
            data object RingToCard : Advisory
        /** 卡片色对比度可能不足。 */
        data object LowContrast : Advisory
        /** 提醒已开启，但所选（或模板没有）日期字段解析不到——照此配置不会发出提醒。 */
        data object ReminderNoDateKey : Advisory
    }
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
                _state.update { it.copy(loadError = appContext.getString(R.string.life_record_error_template_not_found)) }
                return@launch
            }
            val configs = runCatching { JSON.decodeFromString<List<FieldConfig>>(tpl.fieldsConfig) }
                .getOrNull().orEmpty()
            val fieldKeysWithData = itemDao.getFieldsDataByTemplate(id).first()
                .flatMapTo(mutableSetOf()) { raw ->
                    runCatching { JSON.decodeFromString<JsonObject>(raw).keys }
                        .getOrDefault(emptySet())
                }
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
                    fieldKeysWithData = fieldKeysWithData,
                    reminder = ReminderSpec.fromJson(tpl.reminderConfig),
                    repeatYearly = tpl.repeatYearly
                )
            }
        }
    }

    // ---- 元信息编辑 ----
    fun setName(v: String) = _state.update { it.copy(name = v, dirty = true) }
    fun setDescription(v: String) = _state.update { it.copy(description = v, dirty = true) }
    fun setCategory(v: String) = _state.update { it.copy(category = v, dirty = true) }
    fun setIcon(v: String) = _state.update { it.copy(icon = v, dirty = true) }
    fun setColor(v: String) = _state.update { it.copy(color = v, dirty = true) }

    /** 开关「每年重复」：只影响读数口径（ELAPSED 按下一次周年滚动），不动任何记录。 */
    fun setRepeatYearly(v: Boolean) = _state.update { it.copy(repeatYearly = v, dirty = true) }

    // ---- 提醒配置（总纲 §提醒显式化） ----

    /** 开 / 关模板级提醒。开启时默认「倒数日」类型，日期字段取模板第一个可用日期字段。 */
    fun setReminderEnabled(on: Boolean) = _state.update { s ->
        if (on == (s.reminder != null)) return@update s
        s.copy(
            reminder = if (on) {
                ReminderSpec(
                    ReminderSpec.Kind.COUNTDOWN,
                    dateKey = s.fields.firstOrNull { !it.disabled && it.type in DATE_TYPES }?.key
                )
            } else {
                null
            },
            dirty = true
        )
    }

    /** 改提醒类型 / 日期字段（开关走 [setReminderEnabled]）。未开启时忽略。 */
    fun updateReminder(spec: ReminderSpec) = _state.update { s ->
        if (s.reminder == null) return@update s
        s.copy(reminder = spec, dirty = true)
    }

    /**
     * 字段库全屏页选中一种字段：只放进待添加槽位，**由编辑页消费时才真正入列表**。
     * 分两步是为了让编辑页在返回后统一处理（例如连续的快捷添加）。
     */
    fun stageAddField(type: FieldType) = _state.update { it.copy(pendingAddType = type) }

    /** 编辑页消费待添加字段（消费即清空，保证同一种不会连加两次）。 */
    fun consumePendingAddField() {
        val type = _state.value.pendingAddType ?: return
        _state.update { it.copy(pendingAddType = null) }
        addField(type)
    }

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
            s.copy(fields = s.fields + cfg, dirty = true)
        }
    }

    fun updateField(index: Int, cfg: FieldConfig) {
        _state.update { s ->
            if (index !in s.fields.indices) {
                s
            } else {
                s.copy(fields = s.fields.toMutableList().also { it[index] = cfg }, dirty = true)
            }
        }
    }

    /** 停用内置字段（ed_3）：保留历史 key，并关闭全部展示出口。 */
    fun disableField(index: Int) {
        _state.update { s ->
            if (index !in s.fields.indices) return@update s
            val cfg = s.fields[index]
            val next = cfg.copy(disabled = true, showInCard = false, showInList = false, showAsProgress = false)
            s.copy(fields = s.fields.toMutableList().also { it[index] = next }, dirty = true)
        }
    }

    /** 删除字段（ed_3）：自定义字段可直接删；内置字段只有从未有数据时才能删。 */
    fun deleteField(index: Int) {
        _state.update { s ->
            if (index !in s.fields.indices) return@update s
            val cfg = s.fields[index]
            if (s.isBuiltin && cfg.key in s.fieldKeysWithData) return@update s
            s.copy(fields = s.fields.toMutableList().also { it.removeAt(index) }, dirty = true)
        }
    }

    /** 字段上移 / 下移（键盘可达的备用路径；主路径是长按拖拽）。 */
    fun moveField(index: Int, delta: Int) = moveFieldTo(index, index + delta)

    /**
     * 把 [from] 处的字段拖到 [to] 处（长按拖拽排序的主路径）。
     * 越界与原地不动都直接返回，不产生 dirty。
     */
    fun moveFieldTo(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.fields.indices || to !in s.fields.indices || from == to) {
                s
            } else {
                val list = s.fields.toMutableList()
                list.add(to, list.removeAt(from))
                s.copy(fields = list.mapIndexed { i, c -> c.copy(sortOrder = i) }, dirty = true)
            }
        }
    }

    /** 批量更新某个字段的配置（字段设置面板所有写操作的统一入口）。 */
    fun patchField(index: Int, transform: (FieldConfig) -> FieldConfig) {
        _state.update { s ->
            if (index !in s.fields.indices) {
                s
            } else {
                s.copy(
                    fields = s.fields.toMutableList().also { it[index] = transform(it[index]) },
                    dirty = true
                )
            }
        }
    }

    // ---- 字段设置面板的辅助查询（面板按能力露出对应配置项，§3.5 字段契约） ----

    /**
     * 能当进度分母的字段：数值型、启用中、且不是它自己。
     * `progressTargetKey` 只能指向这些 key（§7.2 进度字段的分母）。
     */
    fun progressTargetCandidates(excludeKey: String): List<FieldConfig> =
        _state.value.fields.filter {
            !it.disabled && it.key != excludeKey && it.type in NUMERIC_TARGET_TYPES
        }

    /** 面板是否该露出「选项」配置（只有点选类字段带选项，§3.2 七种「手」）。 */
    fun hasOptions(type: FieldType): Boolean = type in OPTIONS_TYPES

    /** 面板是否该露出「默认值」配置：派生字段零输入，没有默认值可设（§3.4 F 组）。 */
    fun supportsDefaultValue(type: FieldType): Boolean =
        FieldContracts.of(type).group != FieldGroup.DERIVED

    // ---- 保存前校验（ed_9） ----
    fun validate(): Validation {
        val s = _state.value
        val active = s.fields.filter { !it.disabled }
        val blocks = hardBlocks(active, s.fields)
        val advisories = mutableListOf<Validation.Advisory>()
        // 提示：圆环族进度落到卡片流（§4.2 降级表）
        if (active.any { it.showAsProgress && ringFamily(it) }) {
            advisories += Validation.Advisory.RingToCard
        }
        // 提示：卡片色对比度（仅做粗略判断：过浅的色在浅底上对比不足）
        if (s.color.isNotBlank() && !contrastOk(s.color)) {
            advisories += Validation.Advisory.LowContrast
        }
        // 提示：提醒开启但日期字段不可用（§提醒显式化：配置得能落地，别静默失效）
        val reminder = s.reminder
        if (reminder != null && reminder.enabled && reminder.kind != ReminderSpec.Kind.SUBSCRIPTION) {
            val dateKeys = s.fields.filter { !it.disabled && it.type in DATE_TYPES }.mapTo(mutableSetOf()) { it.key }
            if (reminder.dateKey == null || reminder.dateKey !in dateKeys) {
                advisories += Validation.Advisory.ReminderNoDateKey
            }
        }
        return Validation(blocks, advisories)
    }

    /**
     * 硬阻断（保存置灰）：三条都是「**能存，但存了没用**」的配置错误。
     *
     * 抽出来是因为 `validate()` 加第三条后复杂度就顶到 detekt 阈值了；
     * 分开之后「阻断」与「提示」两类结论各自成段，读起来也更清楚。
     */
    private fun hardBlocks(active: List<FieldConfig>, fields: List<FieldConfig>): List<Validation.Block> {
        val blocks = mutableListOf<Validation.Block>()
        // 1 文本字段预算（上限 2）
        val textCount = active.count { it.type in TEXT_TYPES }
        if (textCount > TEXT_BUDGET) {
            blocks += Validation.Block.TextBudget(textCount)
        }
        // 2 必填且无默认值
        active.firstOrNull { it.required && it.defaultValue.isBlank() }?.let {
            blocks += Validation.Block.RequiredNoDefault(it.label.ifBlank { it.key })
        }
        // 3 派生字段配不全（缺表达式 / 缺参考 / 参考指向不存在或已停用的字段）
        active.firstOrNull { derivedUnconfigured(it, fields) }?.let {
            blocks += Validation.Block.DerivedUnconfigured(it.label.ifBlank { it.key })
        }
        return blocks
    }

    /**
     * 分类归一：只认「计划 / 时间 / 记录」三个值。
     *
     * 编辑器里分类已是三选一，但**历史数据**里可能存着用户当年自由输入的自造分类
     * （比如「理财」）——那种模板在生活页三张分类卡里都没有入口。这里把它们收敛到「计划」，
     * 用户下次编辑该模板时会在界面上看到提示（`life_template_category_unsupported`）。
     */
    internal fun normalizedCategory(value: String, fallback: String?): String = when {
        value in BuiltinTemplates.ALL_CATEGORIES -> value
        !fallback.isNullOrBlank() && fallback in BuiltinTemplates.ALL_CATEGORIES -> fallback
        else -> BuiltinTemplates.PLAN_CATEGORY
    }

    // ---- 保存 ----
    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        // 加载失败时 existingId 仍是 null：放行就会被当成「新建模板」，用户以为在编辑、
        // 实际插入一条新的未命名模板，而原模板的字段配置全丢。这里硬挡住。
        if (s.loadError != null) return
        val v = validate()
        if (!v.canSave) return
        viewModelScope.launch {
            val fieldsJson = Json { ignoreUnknownKeys = true }.encodeToString(s.fields)
            val id = if (s.existingId != null) {
                val existing = repo.getTemplateById(s.existingId) ?: return@launch
                repo.updateTemplate(
                    existing.copy(
                        name = s.name.ifBlank { existing.name },
                        description = s.description,
                        category = normalizedCategory(s.category, existing.category),
                        icon = s.icon,
                        color = s.color,
                        fieldsConfig = fieldsJson,
                        reminderConfig = s.reminder?.toJson(),
                        repeatYearly = s.repeatYearly,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                // 内置模板被改过 → 标记已自定义（§4.1）
                if (existing.isBuiltin) seeder.markCustomized(existing.id)
                existing.id
            } else {
                val maxSort = repo.getAllTemplates().first().maxOfOrNull { it.sortOrder } ?: 0
                repo.insertTemplate(
                    LifeTemplate(
                        name = s.name.ifBlank { appContext.getString(R.string.life_template_untitled) },
                        category = normalizedCategory(s.category, null),
                        icon = s.icon,
                        color = s.color,
                        description = s.description,
                        fieldsConfig = fieldsJson,
                        layoutType = "card",
                        availableLayouts = "[\"card\",\"list\"]",
                        statusFlowConfig = "",
                        linkConfig = "",
                        reminderConfig = s.reminder?.toJson(),
                        isBuiltin = false,
                        // 语义身份在这里**固化一次**（按用户当下选的图标推导）：
                        // 此后用户再改图标只是换显示，连击/hero/统计口径都不会跟着漂移。
                        // 注意 GENERIC 也要显式存（不是 null）：null 的语义是「v12 之前的存量行，
                        // 按 icon 兜底」，若新模板也存 null，用户随后把图标改成 cake 就又会漂移。
                        kind = kindFromIcon(s.icon).name,
                        sortOrder = maxSort + 1,
                        repeatYearly = s.repeatYearly
                    )
                )
            }
            _state.update { it.copy(saved = true) }
            onSaved(id)
        }
    }

    /**
     * 另存为新模板（ed_2 底注）：内置模板想改结构 → 复制成一个自定义模板。
     * 只复制配置，不动原模板；新模板不是内置，字段 key/type 随即可编辑。
     */
    fun saveAsNew(newName: String, onSaved: (Long) -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            val fieldsJson = Json { ignoreUnknownKeys = true }.encodeToString(s.fields)
            val maxSort = repo.getAllTemplates().first().maxOfOrNull { it.sortOrder } ?: 0
            val id = repo.insertTemplate(
                LifeTemplate(
                    name = newName,
                    // 另存为新模板同样要归一：源模板可能是历史自造分类
                    category = normalizedCategory(s.category, null),
                    icon = s.icon,
                    color = s.color,
                    description = s.description,
                        fieldsConfig = fieldsJson,
                        layoutType = "card",
                        availableLayouts = "[\"card\",\"list\"]",
                        statusFlowConfig = "",
                        linkConfig = "",
                        reminderConfig = s.reminder?.toJson(),
                        isBuiltin = false,
                        // 同 save()：另存为新模板时把语义身份一起固化，否则复制出来的
                        // 打卡模板会因为 icon 是唯一判据而丢失连击等行为
                        kind = kindFromIcon(s.icon).name,
                        sortOrder = maxSort + 1,
                        repeatYearly = s.repeatYearly
                    )
                )
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

    private fun defaultLabel(type: FieldType): String = appContext.getString(fieldTypeLabelRes(type))



    private fun ringFamily(config: FieldConfig): Boolean = when (resolveProgressForm(config)) {
        ProgressForm.THICK_RING,
        ProgressForm.THIN_RING,
        ProgressForm.SEGMENTED_RING,
        ProgressForm.BEADED_RING -> true
        ProgressForm.THICK_CAPSULE,
        ProgressForm.THIN_TRACK,
        ProgressForm.SEGMENTED_BAR -> false
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

        /** 能当进度分母的类型（§7.2：分母必须是可累加的数值）。 */
        val NUMERIC_TARGET_TYPES = setOf(
            FieldType.NUMBER, FieldType.CURRENCY, FieldType.DURATION, FieldType.RATING,
            FieldType.PERCENT, FieldType.PERCENTAGE, FieldType.SLIDER
        )

        /** 带选项的点选类字段（§3.2 OPTION_CHIPS / QUICK_PICK 两种「手」）。 */
        val OPTIONS_TYPES = setOf(
            FieldType.SELECT, FieldType.MULTI_SELECT, FieldType.TAG, FieldType.CHECKLIST
        )

        /** 能当提醒日期来源的类型（§提醒显式化）。 */
        val DATE_TYPES = setOf(FieldType.DATE, FieldType.DATETIME)
    }
}
