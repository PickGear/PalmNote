package com.palmnote.data.db

import com.palmnote.domain.model.FieldConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 提取结果行（与 Room 实体解耦，迁移回填与双写共用）。 */
data class FieldValueRow(
    val fieldKey: String,
    val type: String,
    val num: Double? = null,
    val text: String? = null,
    val dateMs: Long? = null,
    val json: String? = null,
    val idx: Int = 0
)

/**
 * `fieldsData` → 统计行提取器（总纲 §7.1）。迁移回填与写入双写共用同一实现，
 * 保证「回填后逐条与原值比对」的验收口径与日常写入一致。
 *
 * 分类规则（按字段类型名单，不依赖 FieldContract 以避免迁移期加载整个注册表）：
 * - 数值（NUMBER/CURRENCY/PERCENT/PERCENTAGE/SLIDER/DURATION/RATING）→ num
 * - 日期（DATE/DATETIME）→ dateMs；TIME（"HH:mm" 或分钟数）→ num(分钟)
 * - BOOLEAN → num(0/1)
 * - SELECT/TAG → text；MULTI_SELECT → 按值展开多行 text
 * - CHECKLIST → 按项展开多行 text + num(done?1:0)
 * - TABLE → 按行展开 json(rowJson)
 * - 其余 → json(原始字符串)
 */
object FieldValueExtractor {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val numericTypes = setOf(
        "NUMBER", "CURRENCY", "PERCENT", "PERCENTAGE", "SLIDER", "DURATION", "RATING"
    )
    private val dateTypes = setOf("DATE", "DATETIME")

    fun extract(fieldsConfigJson: String, fieldsData: String): List<FieldValueRow> {
        if (fieldsData.isBlank() || fieldsData == "{}") return emptyList()
        val configs: List<FieldConfig> = try {
            json.decodeFromString<List<FieldConfig>>(fieldsConfigJson)
        } catch (_: Exception) {
            emptyList()
        }
        val data: JsonObject = try {
            json.decodeFromString<JsonObject>(fieldsData)
        } catch (_: Exception) {
            return emptyList()
        }
        val rows = mutableListOf<FieldValueRow>()
        for (cfg in configs) {
            val el = data[cfg.key] ?: continue
            val primitive = el as? JsonPrimitive
            val raw = primitive?.content ?: (el as? kotlinx.serialization.json.JsonArray)?.let { arr ->
                arr.joinToString(",") { (it as? JsonPrimitive)?.content ?: "" }
            } ?: el.toString()
            if (raw.isBlank()) continue
            val type = cfg.type.name
            when {
                type in numericTypes -> primitive?.content?.toDoubleOrNull()?.let { rows.add(FieldValueRow(cfg.key, type, num = it)) }
                type in dateTypes -> primitive?.content?.toLongOrNull()?.let { rows.add(FieldValueRow(cfg.key, type, dateMs = it)) }
                type == "TIME" -> parseMinutes(primitive?.content)?.let { rows.add(FieldValueRow(cfg.key, type, num = it.toDouble())) }
                type == "BOOLEAN" -> rows.add(FieldValueRow(cfg.key, type, num = if (primitive?.content == "true") 1.0 else 0.0))
                type == "SELECT" || type == "TAG" -> rows.add(FieldValueRow(cfg.key, type, text = raw))
                type == "MULTI_SELECT" -> {
                    val values = primitive?.content?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: (el as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                        ?: emptyList()
                    values.forEachIndexed { i, v -> rows.add(FieldValueRow(cfg.key, type, text = v, idx = i)) }
                }
                type == "CHECKLIST" -> {
                    val obj = CompoundPayloads.unwrap(raw) ?: continue
                    val arr = obj["items"] as? kotlinx.serialization.json.JsonArray ?: continue
                    arr.forEachIndexed { i, item ->
                        val o = item as? JsonObject ?: return@forEachIndexed
                        val text = (o["text"] as? JsonPrimitive)?.content ?: return@forEachIndexed
                        val done = (o["done"] as? JsonPrimitive)?.content == "true"
                        rows.add(FieldValueRow(cfg.key, type, num = if (done) 1.0 else 0.0, text = text, idx = i))
                    }
                }
                type == "TABLE" -> {
                    val obj = CompoundPayloads.unwrap(raw) ?: continue
                    val arr = obj["rows"] as? kotlinx.serialization.json.JsonArray ?: continue
                    arr.forEachIndexed { i, rowEl ->
                        val rowArr = rowEl as? kotlinx.serialization.json.JsonArray ?: return@forEachIndexed
                        rows.add(FieldValueRow(cfg.key, type, json = rowArr.toString(), idx = i))
                    }
                }
                else -> rows.add(FieldValueRow(cfg.key, type, json = raw))
            }
        }
        return rows
    }

    private fun parseMinutes(raw: String?): Int? {
        val asMinutes = raw?.toIntOrNull()?.takeIf { it in 0..1439 }
        val fromHhMm = raw?.split(":")?.takeIf { it.size == 2 }?.let { p ->
            val h = p[0].toIntOrNull()
            val m = p[1].toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) h * 60 + m else null
        }
        return asMinutes ?: fromHhMm
    }
}

/** 迁移期的最小载荷解包（避免迁移 classpath 引入 FieldContract 的 Compose 依赖链）。 */
internal object CompoundPayloads {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    fun unwrap(raw: String?): JsonObject? {
        if (raw == null) return null
        return try {
            json.decodeFromString<JsonObject>(raw)
        } catch (_: Exception) {
            null
        }
    }
}
