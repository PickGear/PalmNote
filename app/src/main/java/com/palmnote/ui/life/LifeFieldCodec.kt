package com.palmnote.ui.life

import com.palmnote.domain.model.ChecklistRow
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.encodeChecklist
import com.palmnote.domain.util.DateUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 字段值的**唯一编解码口径**。
 *
 * 表单保存（`LifeCreateRecordViewModel.buildFieldsData`）与详情页就地编辑
 * （`LifeDetailViewModel.setFieldValue`）都走这里；显示层要"原始表单值"时也走
 * [decodeFieldValue]。
 *
 * 为什么必须收拢：这些规则原先只存在于 `LifeCreateRecordViewModel` 的两个私有方法里，
 * 谁想再写一个"改单个字段"的入口，就只能抄一遍 —— 抄完就是审计里反复出现的 R2
 * （同一事实多套实现），而这类分叉的表现形式是**静默**的：日期写法不一致 → 执行列
 * 镜像不出来；数字写成字符串 → 统计读不到。
 */

/** 数值回填的显示形态：整数不带小数点，非整数**优先两位小数、仅在往返无损时采用**，否则给完整精度。 */
internal fun formatNumForEdit(v: Double): String {
    if (v == v.toLong().toDouble()) return v.toLong().toString()
    val two = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    return if (two.toDoubleOrNull() == v) two else v.toString()
}

/**
 * `fieldsData` 里的 JSON 值 → **表单字符串**（与 [encodeFieldValue] 互为逆变换）。
 *
 * CHECKLIST / MAP 这类复合载荷传原文；多选以**数组**形态存，这里转成逗号分隔的单串
 * （表单里的多选控件就是逗号分隔，直接 `toString` 会把 `["a","b"]` 塞进输入框）。
 */
internal fun decodeFieldValue(cfg: FieldConfig, el: JsonElement?): String? {
    val primitive = el as? JsonPrimitive ?: return when (el) {
        is JsonObject -> el.toString()
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(",")
        else -> null
    }
    val raw = primitive.contentOrNull ?: return null
    if (raw.isBlank()) return null
    return when (cfg.type) {
        FieldType.NUMBER, FieldType.CURRENCY, FieldType.SLIDER, FieldType.DURATION,
        FieldType.PERCENT, FieldType.PERCENTAGE, FieldType.RATING ->
            raw.toDoubleOrNull()?.let(::formatNumForEdit) ?: raw
        FieldType.DATE -> raw.toLongOrNull()?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        } ?: raw
        FieldType.DATETIME -> raw.toLongOrNull()?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } ?: raw
        else -> raw
    }
}

/**
 * **表单字符串 → `fieldsData` 里的 JSON 值**；返回 `null` = 这个键不写
 * （空值，以及本来就不进表单的派生字段）。
 *
 * 各类型的落库形态（与 `LifeItemRepositoryImpl` 的读取口径配套）：
 * 布尔→Boolean、数值→Double、日期→**毫秒**、时间→`HH:mm` 字符串、
 * 清单→载荷字符串、其余→字符串原文。
 */
internal fun encodeFieldValue(cfg: FieldConfig, raw: String): JsonElement? {
    if (raw.isBlank()) return null
    return when (cfg.type) {
        FieldType.BOOLEAN -> JsonPrimitive(raw.toBooleanStrictOrNull() ?: false)
        FieldType.NUMBER, FieldType.CURRENCY, FieldType.SLIDER, FieldType.DURATION,
        FieldType.PERCENT, FieldType.PERCENTAGE, FieldType.RATING ->
            JsonPrimitive(raw.toDoubleOrNull() ?: 0.0)
        FieldType.DATE, FieldType.DATETIME ->
            JsonPrimitive(DateUtils.parseDateValueOrNull(raw) ?: System.currentTimeMillis())
        FieldType.CHECKLIST -> JsonPrimitive(
            // 表单层产出的是清单载荷原样透传；纯文本行是历史遗留格式，按未勾选行转换。
            if (raw.trimStart().startsWith("{")) {
                raw
            } else {
                encodeChecklist(raw.lines().filter { it.isNotBlank() }.map { ChecklistRow(it, false) })
            }
        )
        else -> JsonPrimitive(raw)
    }
}

/**
 * 把 [fieldsData] 里 [key] 的值就地改成 [raw]（表单字符串形态），返回新的 `fieldsData`。
 * 空串 = **删除该键**（与表单保存时"空值不写"一致，而不是写一个空字符串）。
 */
internal fun applyFieldValue(fieldsData: String, cfg: FieldConfig, raw: String): String {
    val obj = runCatching {
        kotlinx.serialization.json.Json.decodeFromString<JsonObject>(fieldsData)
    }.getOrDefault(JsonObject(emptyMap()))
    val next = obj.toMutableMap()
    val encoded = encodeFieldValue(cfg, raw)
    if (encoded == null) next.remove(cfg.key) else next[cfg.key] = encoded
    return JsonObject(next).toString()
}
