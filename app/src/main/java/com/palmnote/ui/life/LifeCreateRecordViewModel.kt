package com.palmnote.ui.life

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palmnote.data.db.dao.LifeTemplateDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.domain.model.ChecklistRow
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.encodeChecklist
import com.palmnote.domain.repository.LifeItemRepository
import com.palmnote.domain.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 记录填写页 UI 状态。 */
data class CreateRecordUiState(
    val templateName: String = "",
    val templateIcon: String = "",
    val templateColor: String = "",
    val fields: List<FieldConfig> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LifeCreateRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templateDao: LifeTemplateDao,
    private val repository: LifeItemRepository
) : ViewModel() {

    private val iconKey: String = savedStateHandle.get<String>(ARG_TEMPLATE_ICON).orEmpty()
    private var cachedTemplateId: Long = 0L

    private val _state = MutableStateFlow(CreateRecordUiState())
    val state: StateFlow<CreateRecordUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val template = templateDao.getTemplateByIcon(iconKey)
            if (template == null) {
                _state.update { it.copy(error = "模板未找到") }
                return@launch
            }
            cachedTemplateId = template.id
            val configs = runCatching {
                Json.decodeFromString<List<FieldConfig>>(template.fieldsConfig)
            }.getOrDefault(emptyList()).filter { !it.disabled }
            // 智能默认值（§3.6）：DATE→今天，TIME→当前时间，DATETIME→今天+现在
            val defaults = mutableMapOf<String, String>()
            for (cfg in configs) {
                when (cfg.type) {
                    FieldType.DATE -> defaults[cfg.key] = LocalDate.now().toString()
                    FieldType.TIME -> defaults[cfg.key] = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    FieldType.DATETIME -> defaults[cfg.key] = "${LocalDate.now()} ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}"
                    else -> {}
                }
            }
            _state.update {
                it.copy(
                    templateName = template.name,
                    templateIcon = template.icon,
                    templateColor = template.color,
                    fields = configs,
                    values = defaults
                )
            }
        }
    }

    fun updateValue(key: String, value: String) {
        _state.update { it.copy(values = it.values + (key to value)) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.fields.isEmpty()) return

        // 必填校验
        val missing = s.fields.filter { it.required }
            .firstOrNull { s.values[it.key].isNullOrBlank() }
        if (missing != null) {
            _state.update { it.copy(error = "请填写「${missing.label}」") }
            return
        }

        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val fieldsData = buildFieldsData(s.fields, s.values)
                val item = LifeItem(
                    templateId = cachedTemplateId,
                    title = buildTitle(s.fields, s.values),
                    fieldsData = fieldsData
                )
                repository.insertItem(item)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    private fun buildFieldsData(configs: List<FieldConfig>, values: Map<String, String>): String {
        val map = mutableMapOf<String, JsonPrimitive>()
        for (cfg in configs) {
            val raw = values[cfg.key] ?: continue
            if (raw.isBlank()) continue
            when (cfg.type) {
                FieldType.BOOLEAN -> map[cfg.key] = JsonPrimitive(raw.toBooleanStrictOrNull() ?: false)
                FieldType.NUMBER, FieldType.CURRENCY, FieldType.SLIDER, FieldType.DURATION,
                FieldType.PERCENT, FieldType.PERCENTAGE, FieldType.RATING ->
                    map[cfg.key] = JsonPrimitive(raw.toDoubleOrNull() ?: 0.0)
                FieldType.DATE -> map[cfg.key] = JsonPrimitive(
                    DateUtils.parseDateValueOrNull(raw) ?: System.currentTimeMillis()
                )
                FieldType.DATETIME -> map[cfg.key] = JsonPrimitive(
                    DateUtils.parseDateValueOrNull(raw) ?: System.currentTimeMillis()
                )
                FieldType.TIME -> map[cfg.key] = JsonPrimitive(raw)
                FieldType.CHECKLIST -> map[cfg.key] = JsonPrimitive(encodeChecklist(
                    raw.lines().filter { it.isNotBlank() }.map { ChecklistRow(it, false) }
                ))
                else -> map[cfg.key] = JsonPrimitive(raw)
            }
        }
        return JsonObject(map).toString()
    }

    private fun buildTitle(configs: List<FieldConfig>, values: Map<String, String>): String {
        for (cfg in configs) {
            val v = values[cfg.key]
            if (!v.isNullOrBlank() && cfg.type in TEXT_TYPES) return v
        }
        return _state.value.templateName.ifBlank { "新建记录" }
    }

    companion object {
        const val ARG_TEMPLATE_ICON = "templateIconKey"
        private val TEXT_TYPES = setOf(
            FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.RICH_TEXT,
            FieldType.URL, FieldType.EMAIL, FieldType.PHONE
        )
    }
}
