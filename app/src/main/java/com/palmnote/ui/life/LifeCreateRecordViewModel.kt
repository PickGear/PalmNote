package com.palmnote.ui.life

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.palmnote.app.R
import com.palmnote.ui.widget.WidgetUpdateHelper
import androidx.lifecycle.viewModelScope
import com.palmnote.data.datastore.PreferencesManager
import com.palmnote.data.db.dao.LIFE_DEMO_META
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.BuiltinFieldText
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.getDisplayName
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.TrackImportFailure
import com.palmnote.domain.model.TrackImportResult
import com.palmnote.domain.model.encodeMapModel
import com.palmnote.domain.model.importTrack
import com.palmnote.domain.model.parseMap
import com.palmnote.domain.repository.LifeItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/** 记录填写页 UI 状态（新建与编辑共用同一表单）。 */
data class CreateRecordUiState(
    val templateName: String = "",
    val templateIcon: String = "",
    val templateColor: String = "",
    val fields: List<FieldConfig> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    /**
     * 已停用字段数：它们**不进表单**（`showInForm` 过滤），但表单里得说一句 ——
     * 否则用户只会觉得"表单缺了东西"，而停用是模板层的事、这里看不到原因。
     */
    val disabledFieldCount: Int = 0,
    /** 模板的「每年重复」：填写页的预览也要按同一次序滚动，否则预览与真实读数不一致。 */
    val templateRepeatYearly: Boolean = false,
    /** 编辑已有记录（itemId > 0）。 */
    val isEdit: Boolean = false,
    /** 保存时缺值的必填字段 key：表单内联标红，错在哪一格一眼可见。 */
    val missingRequiredKey: String? = null,
    /** 用户动过表单（供返回拦截）。 */
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    /** 每次导入尝试的序号：0 表示尚未导入，供 UI 显示/清除导入结果。 */
    val trackImportTick: Int = 0,
    val trackImportFieldKey: String? = null,
    val trackImportMessage: String? = null,
    val importingTrack: Boolean = false
) {
    /**
     * 能不能保存。**"没有可填字段"也算不能**：那意味着模板加载失败或模板本身没有字段，
     * 而 `save()` 内部同样会 return —— 按钮置灰比"点了没反应"诚实。
     *
     * 判断放在状态层而不是 UI：这是业务规则，且可以直接单测（UI 里的 `&&` 还会占用
     * `LifeCreateRecordScreen` 的圈复杂度额度）。
     */
    val canSave: Boolean get() = !saving && fields.isNotEmpty()
}

@HiltViewModel
class LifeCreateRecordViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val templateDao: LifeTemplateDao,
    private val repository: LifeItemRepository,
    private val preferences: PreferencesManager
) : ViewModel(), FieldFormHost {

    private val templateId: Long = savedStateHandle.get<Long>(ARG_TEMPLATE_ID) ?: 0L
    private val itemId: Long = savedStateHandle.get<Long>(ARG_ITEM_ID) ?: 0L
    private var cachedTemplateId: Long = 0L
    private var cachedTitle: String = ""

    private val _state = MutableStateFlow(CreateRecordUiState())
    val state: StateFlow<CreateRecordUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (itemId > 0L) {
                loadForEdit(itemId)
            } else {
                loadForCreate()
            }
        }
    }

    private suspend fun loadForCreate() {
        // 按 **id** 认模板：图标只是长相，用户可以从内置图标里随便挑，
        // 撞图标时按 icon 查会打开另一个模板（见 LifeRoute.LifeCreateRecord 的说明）。
        val template = templateDao.getTemplateById(templateId)
        if (template == null) {
            _state.update { it.copy(error = appContext.getString(R.string.life_record_error_template_not_found)) }
            return
        }
        cachedTemplateId = template.id
        val tf = decodeTemplateFields(template.fieldsConfig)
        // 日期类字段**不再预填**（2026-09-29 定案，方案①：全部留空）。
        //
        // 原实现把 DATE→今天 / TIME→现在 / DATETIME→今天+现在 直接写成**真实值**，
        // 而界面上的灰胶囊看起来只是个占位提示 —— 用户点开表单、什么都不改、直接保存，
        // 就把「今天」当成了生日 / 纪念日 / 倒计时的日期。它对打卡这类「当天发生」的记录
        // 是合理便利，但对「指向别的时间点」的字段本身就是错的，而这两类语义无法用同一个
        // 默认值表达。现在一律留空、占位符显示「今天 / 现在」（且渲染成弱化色，见
        // `LifeCreateFieldKit.PillValue` 的 `muted`），由必填校验拦住空值，
        // 把「什么时候该默认什么」交回用户。
        _state.update {
            it.copy(
                templateName = template.getDisplayName(appContext),
                templateIcon = template.icon,
                templateColor = template.color,
                fields = tf.fillable,
                values = emptyMap(),
                disabledFieldCount = tf.disabledCount,
                templateRepeatYearly = template.repeatYearly
            )
        }
    }

    /** 编辑已有记录：模板由记录反查，fieldsData 逐字段反解回表单值。 */
    private suspend fun loadForEdit(id: Long) {
        val item = repository.getItemById(id)
        if (item == null) {
            _state.update { it.copy(error = appContext.getString(R.string.life_record_error_template_not_found)) }
            return
        }
        val template = templateDao.getTemplateById(item.templateId)
        if (template == null) {
            _state.update { it.copy(error = appContext.getString(R.string.life_record_error_template_not_found)) }
            return
        }
        cachedTemplateId = template.id
        cachedTitle = item.title
        val tf = decodeTemplateFields(template.fieldsConfig)
        val configs = tf.fillable
        val payload = runCatching {
            Json.decodeFromString<JsonObject>(item.fieldsData)
        }.getOrDefault(JsonObject(emptyMap()))
        val values = mutableMapOf<String, String>()
        for (cfg in configs) {
            decodeValue(cfg, payload[cfg.key])?.let { values[cfg.key] = it }
        }
        _state.update {
            it.copy(
                templateName = template.getDisplayName(appContext),
                templateIcon = template.icon,
                templateColor = template.color,
                fields = configs,
                values = values,
                isEdit = true,
                disabledFieldCount = tf.disabledCount,
                templateRepeatYearly = template.repeatYearly
            )
        }
    }

    /** 模板字段的解码结果：进表单的可填字段 + 已停用字段数（停用的不进表单，但要能解释）。 */
    private data class TemplateFields(val fillable: List<FieldConfig>, val disabledCount: Int)

    private fun decodeTemplateFields(raw: String): TemplateFields {
        val all = BuiltinFieldText.localizeConfigs(
            appContext,
            runCatching { Json.decodeFromString<List<FieldConfig>>(raw) }.getOrDefault(emptyList())
        )
        return TemplateFields(all.filter { it.showInForm() }, all.count { it.disabled })
    }

    /** fieldsData 载荷 → 表单字符串。**口径见 `LifeFieldCodec.decodeFieldValue`（唯一实现）**。 */
    private fun decodeValue(cfg: FieldConfig, el: kotlinx.serialization.json.JsonElement?): String? =
        decodeFieldValue(cfg, el)

    /** 展示过的错误就地清空：同一错误再次发生时 snackbar 能重新触发。 */
    fun consumeError() = _state.update { it.copy(error = null) }

    override fun updateValue(key: String, value: String) {
        _state.update {
            it.copy(
                values = it.values + (key to value),
                dirty = true,
                // 缺值的字段一有输入就撤掉内联错误
                missingRequiredKey = if (it.missingRequiredKey == key && value.isNotBlank()) null else it.missingRequiredKey
            )
        }
    }

    /**
     * 读入 GPX/KML（SAF 返回的 Uri），解析成功后把结构化轨迹写入 MAP 字段载荷。
     * 只落 [MapTrack] 的抽稀点与统计，不保存原始 XML。
     */
    override fun importTrack(context: Context, uri: Uri, fieldKey: String) {
        if (_state.value.importingTrack) return
        _state.update { it.copy(importingTrack = true, error = null, trackImportMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: return@runCatching null
                }.getOrNull()?.let(::importTrack)
            }
            val failureMessage: String? = when (result) {
                null -> appContext.getString(R.string.life_record_error_read_file)
                is TrackImportResult.Failure -> when (result.reason) {
                    TrackImportFailure.EMPTY -> appContext.getString(R.string.life_track_error_empty)
                    TrackImportFailure.UNSUPPORTED_KML_OVERLAY -> appContext.getString(R.string.life_track_error_unsupported_kml)
                    TrackImportFailure.PARSE_ERROR -> appContext.getString(R.string.life_track_error_parse)
                    TrackImportFailure.INVALID_POINTS -> appContext.getString(R.string.life_track_error_invalid_points)
                }
                is TrackImportResult.Success -> null
            }
            if (failureMessage != null) {
                _state.update {
                    it.copy(
                        importingTrack = false,
                        trackImportTick = it.trackImportTick + 1,
                        trackImportFieldKey = fieldKey,
                        trackImportMessage = failureMessage,
                        error = failureMessage
                    )
                }
                return@launch
            }
            val success = result as TrackImportResult.Success
            _state.update { s ->
                val existing = s.values[fieldKey].orEmpty()
                val model = parseMap(existing)
                s.copy(
                    values = s.values + (fieldKey to encodeMapModel(model.copy(track = success.track.stats))),
                    importingTrack = false,
                    trackImportTick = s.trackImportTick + 1,
                    trackImportFieldKey = fieldKey,
                    trackImportMessage = appContext.getString(R.string.life_record_track_imported, success.track.points.size)
                )
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        // 没有可填字段就没有可存的东西。这条**同时**守住了"模板加载失败"那条路径：
        // 失败时 fields 为空而 cachedTemplateId 仍是 0，若放行就会插入一条挂到模板 0 的坏记录。
        if (s.fields.isEmpty()) return

        // 必填校验
        val missing = s.fields.filter { it.required }
            .firstOrNull { s.values[it.key].isNullOrBlank() }
        if (missing != null) {
            _state.update {
                it.copy(
                    error = appContext.getString(R.string.life_record_error_required, missing.label),
                    missingRequiredKey = missing.key
                )
            }
            return
        }

        _state.update { it.copy(saving = true, error = null, missingRequiredKey = null) }
        viewModelScope.launch {
            try {
                val fieldsData = buildFieldsData(s.fields, s.values)
                if (s.isEdit) {
                    val existing = repository.getItemById(itemId) ?: throw IllegalStateException("record missing")
                    // 派生/表格等未进表单的键：从原 fieldsData 原样保留，表单键覆盖其上
                    val preserved = runCatching { Json.decodeFromString<JsonObject>(existing.fieldsData) }
                        .getOrDefault(JsonObject(emptyMap()))
                    val formMap = runCatching { Json.decodeFromString<JsonObject>(fieldsData) }
                        .getOrDefault(JsonObject(emptyMap()))
                    val mergedMap = (preserved + formMap).toMutableMap()
                    // 用户在表单里清空的键必须从结果里移除，否则"原值保留"会让清除失效
                    s.fields.forEach { cfg ->
                        if (s.values[cfg.key].isNullOrBlank()) mergedMap.remove(cfg.key)
                    }
                    val merged = JsonObject(mergedMap)
                    repository.updateItem(
                        // dueDate/dueTime 置空：让仓储按编辑后的 fieldsData 重算执行列镜像，
                        // 否则旧 dueDate 非空会盖掉用户改过的日期字段。
                        existing.copy(
                            fieldsData = merged.toString(),
                            title = resolveTitle(s, existing.title),
                            dueDate = null,
                            dueTime = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    val item = LifeItem(
                        templateId = cachedTemplateId,
                        title = buildTitle(s.fields, s.values),
                        fieldsData = fieldsData,
                        // 可见性口径要和另外两处创建入口一致（快捷添加 / 详情页打卡）：
                        // 演示模式下不带这个标记的记录，在首页、日历、看板、统计里全都看不到，
                        // 用户刚填完表单就发现记录「消失」了。注意它只是可见性标记，
                        // isSeedSample 保持 false ⇒ 关掉演示模式时不会被删（见 LifeItem 注释）。
                        meta = if (preferences.lifeDemoMode.first()) LIFE_DEMO_META else null
                    )
                    repository.insertItem(item)
                }
                WidgetUpdateHelper.refreshTodoWidgets()
                WidgetUpdateHelper.refreshCounterWidgets()
                WidgetUpdateHelper.refreshHabitWidgets()
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: appContext.getString(R.string.life_record_error_save_failed)) }
            }
        }
    }

    /** 编辑态标题：正文文本字段有值 → 用字段值；否则保留原标题（不退回模板名）。 */
    private fun resolveTitle(s: CreateRecordUiState, fallback: String): String {
        val hasText = s.fields.any { it.type in TEXT_TYPES && !s.values[it.key].isNullOrBlank() }
        return if (hasText) buildTitle(s.fields, s.values) else fallback.ifBlank { buildTitle(s.fields, s.values) }
    }

    private fun buildFieldsData(configs: List<FieldConfig>, values: Map<String, String>): String {
        val map = mutableMapOf<String, JsonPrimitive>()
        for (cfg in configs) {
            val raw = values[cfg.key] ?: continue
            // 各类型的落库形态收在 LifeFieldCodec.encodeFieldValue 一处（详情页就地编辑共用同一口径）。
            val encoded = encodeFieldValue(cfg, raw)
            if (encoded != null) map[cfg.key] = encoded as JsonPrimitive
        }
        return JsonObject(map).toString()
    }

    private fun buildTitle(configs: List<FieldConfig>, values: Map<String, String>): String {
        for (cfg in configs) {
            val v = values[cfg.key]
            if (!v.isNullOrBlank() && cfg.type in TEXT_TYPES) return v
        }
        return _state.value.templateName.ifBlank { appContext.getString(R.string.life_record_new_title) }
    }

    companion object {
        const val ARG_TEMPLATE_ID = "templateId"
        const val ARG_ITEM_ID = "itemId"

        /**
         * 不该出现在填写页的字段：只剩派生字段（F 组）。
         * 派生值由其他字段算出（BMI / 还差 / 已经过 / 连续），用户没有可填的东西。
         * 编辑保存时这些键从原 `fieldsData` 原样保留，不丢数据。
         *
         * **TABLE 已移出本表**（2026-09-29）：它有行编辑器了（`LifeCreateRecordKit.TableSection`）。
         * 此前它被排除，而 `encodeTable` 又没有调用方 —— 结果是存钱「明细」/ 购物「购物明细」/
         * 旅行「行程明细」/ 订阅「扣费历史」四个内置模板的表格**一条都填不进去**。
         */
        val EXCLUDED_FROM_FORM = setOf(
            FieldType.FORMULA, FieldType.REMAINING, FieldType.ELAPSED, FieldType.STREAK
        )
        private val TEXT_TYPES = setOf(
            FieldType.TEXT,
            FieldType.SHORT_TEXT,
            FieldType.RICH_TEXT,
            FieldType.URL,
            FieldType.EMAIL,
            FieldType.PHONE
        )
    }
}

/** 表单页只渲染用户可填写的字段（派生与表格排除，见 [LifeCreateRecordViewModel.EXCLUDED_FROM_FORM]）。 */
private fun com.palmnote.domain.model.FieldConfig.showInForm(): Boolean =
    !disabled && type !in LifeCreateRecordViewModel.EXCLUDED_FROM_FORM
