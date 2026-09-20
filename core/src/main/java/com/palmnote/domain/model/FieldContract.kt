package com.palmnote.domain.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 字段六组（§3.5）：数值 / 点选 / 媒体与空间 / 文本 / 复合 / 派生。 */
enum class FieldGroup { NUMERIC, CHOICE, MEDIA_SPACE, TEXTUAL, COMPOUND, DERIVED }

/** 输入控件形态（§3.2 七种「手」；NONE = 零输入，派生字段）。 */
enum class InputKind { TEXT_LINE, TEXT_MULTILINE, STEPPER, DRAG, TOGGLE, OPTION_CHIPS, QUICK_PICK, MEDIA_PICK, NONE }

/** 只读展示形态——不是文本（§3.5）。 */
enum class DisplayKind {
    TEXT, MONEY, NUMBER_UNIT, CHIP_LIST, STARS, SWATCH,
    PROGRESS_BAR, RING,
    CHECKLIST_VIEW, TABLE_VIEW, RANGE_VIEW, PERSON_STACK, MEDIA_GRID, MAP_VIEW, DERIVED_VALUE
}

/**
 * 进度形态（§3.5.1 两族 11 种；§十 定案 12：P1 落地 ③ 厚胶囊 / ⑥ 厚环 / ⑩ 分段齿环，
 * 列表卡一律横向薄档；⑦⑧⑨⑪ 及 ①②④⑤ 等数据依赖就绪后再开）。
 * AUTO 不作为存储值出现——读取时已按语义解析为具体形态。
 */
enum class ProgressForm { THICK_CAPSULE, THIN_TRACK, SEGMENTED_BAR, THICK_RING, SEGMENTED_RING }

/** 可聚合函数（§3.5）；空集 = 不可聚合。 */
enum class AggFn { SUM, AVG, MIN, MAX, COUNT, DISTRIBUTION }

/** 智能默认（§3.6 硬指标 2：「什么都不填也能存」）。 */
enum class DefaultPolicy { TODAY, CURRENT_TIME, LAST_VALUE, FIRST_OPTION, NONE }

/**
 * 字段契约——四态渲染的唯一真源（§3.5）。
 * 四个分派点（FieldInput / FieldDisplay / 卡片渲染 / 统计）全部查表，未声明 = 编译期不存在。
 */
data class FieldContract(
    val type: FieldType,
    val group: FieldGroup,
    val input: InputKind,
    val display: DisplayKind,
    val agg: Set<AggFn> = emptySet(),
    val defaultPolicy: DefaultPolicy = DefaultPolicy.NONE,
    /** 仅模板编排器可设，记录时不可改（派生字段 = true）。 */
    val editorOnly: Boolean = false,
    /** 可作为进度字段（showAsProgress 有意义）。 */
    val progressCapable: Boolean = false,
    /** 可数语义，支持分段齿环（⑩）。 */
    val countable: Boolean = false,
    /** 可数语义，支持分段条（M 分段档 8 格）。 */
    val segmented: Boolean = false
) {
    companion object {
        /** PERCENTAGE 并入 PERCENT；未知名一律回落 TEXT 契约（防御旧数据）。 */
        fun normalize(name: String): FieldType =
            FieldType.entries.firstOrNull { it.name == name }
                ?: if (name == "PERCENTAGE") FieldType.PERCENT else FieldType.TEXT

        /** 形态覆盖值解析：认不出的值回落 AUTO（§九 形态一致）。 */
        fun parseProgressStyle(raw: String?): ProgressStyleSetting =
            when (raw?.uppercase()) {
                null, "" -> ProgressStyleSetting.AUTO
                "THICK_CAPSULE" -> ProgressStyleSetting.FIXED(ProgressForm.THICK_CAPSULE)
                "THIN_TRACK" -> ProgressStyleSetting.FIXED(ProgressForm.THIN_TRACK)
                "SEGMENTED_BAR" -> ProgressStyleSetting.FIXED(ProgressForm.SEGMENTED_BAR)
                "THICK_RING" -> ProgressStyleSetting.FIXED(ProgressForm.THICK_RING)
                "SEGMENTED_RING" -> ProgressStyleSetting.FIXED(ProgressForm.SEGMENTED_RING)
                else -> ProgressStyleSetting.AUTO
            }
    }
}

/** progressStyle 的解析结果：AUTO = 按语义推导；FIXED = 用户覆盖值。 */
sealed interface ProgressStyleSetting {
    data object AUTO : ProgressStyleSetting
    data class FIXED(val form: ProgressForm) : ProgressStyleSetting
}

/**
 * 进度形态解析（§3.5.1 新规则 1）：同一字段在任何界面只有一种渲染。
 * 决定权在字段（progressStyle 覆盖 + 语义默认），不在卡片。
 * 语义默认表（§4.2 分配表的代码化，只覆盖高频；留空 = AUTO = 语义推荐值）：
 * - 单一目标（存钱 / 阅读 / 学习）→ ⑥ 厚环（重点卡）/ ③ 厚胶囊（列表投影）
 * - 可数（打卡 / 清单 x/y）→ ⑩ 分段齿环（重点卡）/ 分段条（列表投影）
 * - 区间（倒计时 / 纪念日）→ 天数型不走比例进度， heroes 直接巨字
 */
fun resolveProgressForm(config: FieldConfig): ProgressForm {
    when (val s = FieldContract.parseProgressStyle(config.progressStyle)) {
        is ProgressStyleSetting.FIXED -> return s.form
        ProgressStyleSetting.AUTO -> {}
    }
    val c = FieldContracts.of(config.type)
    return when {
        c.segmented || c.countable -> ProgressForm.SEGMENTED_RING
        else -> ProgressForm.THICK_RING
    }
}

/** 列表卡空间投影（§3.5.1 §五）：环 ≥130dp 放不进列表卡，圆环按同族语义投影为横向薄档。 */
fun projectToListCard(form: ProgressForm): ProgressForm = when (form) {
    ProgressForm.THICK_RING -> ProgressForm.THICK_CAPSULE
    ProgressForm.SEGMENTED_RING -> ProgressForm.SEGMENTED_BAR
    else -> form
}

/** 进度求值结果：携带原值（端标「还差 ¥6,800」需要 cur/tot，§3.5.1 #6）。 */
data class ProgressValue(val current: Double?, val total: Double?, val fraction: Float) {
    companion object {
        val EMPTY = ProgressValue(null, null, 0f)
    }
}

/**
 * 34 种字段契约注册表（§3.1 总表的代码化）。
 * 新增 FieldType 而不在这里登记 → 编译期 `of` 非穷举（entries.toMap 由调用方断言），运行期 getValue 抛错。
 */
object FieldContracts {
    private fun c(
        type: FieldType, group: FieldGroup, input: InputKind, display: DisplayKind,
        agg: Set<AggFn> = emptySet(), defaultPolicy: DefaultPolicy = DefaultPolicy.NONE,
        editorOnly: Boolean = false, progressCapable: Boolean = false,
        countable: Boolean = false, segmented: Boolean = false
    ) = FieldContract(type, group, input, display, agg, defaultPolicy, editorOnly, progressCapable, countable, segmented)

    // 注册表：显式 34 条（PERCENTAGE 复用 PERCENT 槽）
    private val registry: Map<FieldType, FieldContract> = buildMap {
        // 文本（最后手段）
        put(FieldType.TEXT, c(FieldType.TEXT, FieldGroup.TEXTUAL, InputKind.TEXT_MULTILINE, DisplayKind.TEXT))
        put(FieldType.SHORT_TEXT, c(FieldType.SHORT_TEXT, FieldGroup.TEXTUAL, InputKind.TEXT_LINE, DisplayKind.TEXT))
        put(FieldType.RICH_TEXT, c(FieldType.RICH_TEXT, FieldGroup.TEXTUAL, InputKind.TEXT_MULTILINE, DisplayKind.TEXT))
        put(FieldType.URL, c(FieldType.URL, FieldGroup.TEXTUAL, InputKind.TEXT_LINE, DisplayKind.TEXT))
        put(FieldType.EMAIL, c(FieldType.EMAIL, FieldGroup.TEXTUAL, InputKind.TEXT_LINE, DisplayKind.TEXT))
        put(FieldType.PHONE, c(FieldType.PHONE, FieldGroup.TEXTUAL, InputKind.TEXT_LINE, DisplayKind.TEXT))
        put(FieldType.FILE, c(FieldType.FILE, FieldGroup.MEDIA_SPACE, InputKind.MEDIA_PICK, DisplayKind.TEXT))
        // 数值
        put(FieldType.NUMBER, c(FieldType.NUMBER, FieldGroup.NUMERIC, InputKind.STEPPER, DisplayKind.NUMBER_UNIT,
            agg = setOf(AggFn.SUM, AggFn.AVG, AggFn.MIN, AggFn.MAX), defaultPolicy = DefaultPolicy.LAST_VALUE, progressCapable = true))
        put(FieldType.CURRENCY, c(FieldType.CURRENCY, FieldGroup.NUMERIC, InputKind.STEPPER, DisplayKind.MONEY,
            agg = setOf(AggFn.SUM, AggFn.AVG, AggFn.MIN, AggFn.MAX), defaultPolicy = DefaultPolicy.LAST_VALUE, progressCapable = true))
        put(FieldType.PERCENT, c(FieldType.PERCENT, FieldGroup.NUMERIC, InputKind.DRAG, DisplayKind.PROGRESS_BAR,
            agg = setOf(AggFn.AVG), progressCapable = true))
        put(FieldType.PERCENTAGE, c(FieldType.PERCENTAGE, FieldGroup.NUMERIC, InputKind.DRAG, DisplayKind.PROGRESS_BAR,
            agg = setOf(AggFn.AVG), progressCapable = true))
        put(FieldType.SLIDER, c(FieldType.SLIDER, FieldGroup.NUMERIC, InputKind.DRAG, DisplayKind.NUMBER_UNIT,
            agg = setOf(AggFn.AVG, AggFn.MIN, AggFn.MAX), defaultPolicy = DefaultPolicy.LAST_VALUE))
        put(FieldType.DURATION, c(FieldType.DURATION, FieldGroup.NUMERIC, InputKind.STEPPER, DisplayKind.NUMBER_UNIT,
            agg = setOf(AggFn.SUM, AggFn.AVG), defaultPolicy = DefaultPolicy.LAST_VALUE))
        put(FieldType.RATING, c(FieldType.RATING, FieldGroup.CHOICE, InputKind.TOGGLE, DisplayKind.STARS,
            agg = setOf(AggFn.AVG)))
        put(FieldType.BOOLEAN, c(FieldType.BOOLEAN, FieldGroup.CHOICE, InputKind.TOGGLE, DisplayKind.SWATCH,
            agg = setOf(AggFn.COUNT), defaultPolicy = DefaultPolicy.LAST_VALUE))
        put(FieldType.COLOR, c(FieldType.COLOR, FieldGroup.CHOICE, InputKind.TOGGLE, DisplayKind.SWATCH))
        // 点选（选项）
        put(FieldType.SELECT, c(FieldType.SELECT, FieldGroup.CHOICE, InputKind.OPTION_CHIPS, DisplayKind.CHIP_LIST,
            agg = setOf(AggFn.COUNT, AggFn.DISTRIBUTION), defaultPolicy = DefaultPolicy.LAST_VALUE))
        put(FieldType.MULTI_SELECT, c(FieldType.MULTI_SELECT, FieldGroup.CHOICE, InputKind.OPTION_CHIPS, DisplayKind.CHIP_LIST,
            agg = setOf(AggFn.COUNT, AggFn.DISTRIBUTION), defaultPolicy = DefaultPolicy.LAST_VALUE))
        put(FieldType.TAG, c(FieldType.TAG, FieldGroup.CHOICE, InputKind.OPTION_CHIPS, DisplayKind.CHIP_LIST,
            agg = setOf(AggFn.COUNT)))
        // 点选（快捷）
        put(FieldType.DATE, c(FieldType.DATE, FieldGroup.NUMERIC, InputKind.QUICK_PICK, DisplayKind.TEXT,
            defaultPolicy = DefaultPolicy.TODAY))
        put(FieldType.TIME, c(FieldType.TIME, FieldGroup.NUMERIC, InputKind.QUICK_PICK, DisplayKind.TEXT,
            defaultPolicy = DefaultPolicy.CURRENT_TIME))
        put(FieldType.DATETIME, c(FieldType.DATETIME, FieldGroup.NUMERIC, InputKind.QUICK_PICK, DisplayKind.TEXT,
            defaultPolicy = DefaultPolicy.TODAY))
        // 媒体与空间
        put(FieldType.IMAGE, c(FieldType.IMAGE, FieldGroup.MEDIA_SPACE, InputKind.MEDIA_PICK, DisplayKind.MEDIA_GRID))
        put(FieldType.VIDEO, c(FieldType.VIDEO, FieldGroup.MEDIA_SPACE, InputKind.MEDIA_PICK, DisplayKind.MEDIA_GRID))
        put(FieldType.AUDIO, c(FieldType.AUDIO, FieldGroup.MEDIA_SPACE, InputKind.MEDIA_PICK, DisplayKind.TEXT))
        put(FieldType.LOCATION, c(FieldType.LOCATION, FieldGroup.MEDIA_SPACE, InputKind.TEXT_LINE, DisplayKind.TEXT))
        put(FieldType.MAP, c(FieldType.MAP, FieldGroup.MEDIA_SPACE, InputKind.MEDIA_PICK, DisplayKind.MAP_VIEW))
        // 复合 E 组
        put(FieldType.CHECKLIST, c(FieldType.CHECKLIST, FieldGroup.COMPOUND, InputKind.OPTION_CHIPS, DisplayKind.CHECKLIST_VIEW,
            countable = true, segmented = true))
        put(FieldType.TABLE, c(FieldType.TABLE, FieldGroup.COMPOUND, InputKind.OPTION_CHIPS, DisplayKind.TABLE_VIEW))
        put(FieldType.RANGE, c(FieldType.RANGE, FieldGroup.COMPOUND, InputKind.QUICK_PICK, DisplayKind.RANGE_VIEW))
        put(FieldType.PERSON, c(FieldType.PERSON, FieldGroup.COMPOUND, InputKind.OPTION_CHIPS, DisplayKind.PERSON_STACK))
        // 派生 F 组（零输入；FORMULA/REMAINING/ELAPSED 同条记录可算 → P1；STREAK 需聚合 → P4）
        put(FieldType.FORMULA, c(FieldType.FORMULA, FieldGroup.DERIVED, InputKind.NONE, DisplayKind.DERIVED_VALUE, editorOnly = true, progressCapable = true))
        put(FieldType.REMAINING, c(FieldType.REMAINING, FieldGroup.DERIVED, InputKind.NONE, DisplayKind.DERIVED_VALUE, editorOnly = true))
        put(FieldType.ELAPSED, c(FieldType.ELAPSED, FieldGroup.DERIVED, InputKind.NONE, DisplayKind.DERIVED_VALUE, editorOnly = true))
        put(FieldType.STREAK, c(FieldType.STREAK, FieldGroup.DERIVED, InputKind.NONE, DisplayKind.DERIVED_VALUE, editorOnly = true, countable = true, segmented = true))
    }

    /** 查表入口（§3.5：`FieldContracts.of(field.type)`）。 */
    fun of(type: FieldType): FieldContract = registry.getValue(type)

    /** 字符串名（FieldDef 遗留 / JSON 直读）→ 契约。 */
    fun byName(name: String): FieldContract = of(FieldContract.normalize(name))

    /** 断言：34 种类型 × 契约全穷举（§九「无 else 承载未实现类型」）。 */
    fun assertExhaustive(): Boolean = FieldType.entries.all { registry.containsKey(it) }
}

// ============================================================
// 派生字段求值（§3.5：从 LifeCards 上收进契约层，四态共用）
// ============================================================

object DerivedEvaluator {

    private fun num(obj: JsonObject, key: String): Double? =
        (obj[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun dateMs(obj: JsonObject, key: String): Long? =
        (obj[key] as? JsonPrimitive)?.content?.toLongOrNull()

    /**
     * FORMULA：同条记录内其他字段 + 四则运算。
     * 表达式 = 字段 key 与 + - * / ( ) 数字；例：`currentAmount / goalAmount`。
     * 除零 / 解析失败 / 引用缺失 → null（上层不渲染，而不是显示 0）。
     */
    fun formula(obj: JsonObject, expression: String): Double? {
        val refRegex = Regex("[A-Za-z_][A-Za-z0-9_]*")
        // 引用缺失 → null（上层不渲染而不是显示 0）；先收集校验，替换阶段不 return
        val values = refRegex.findAll(expression)
            .map { it.value }.distinct()
            .associateWith { num(obj, it) ?: return null }
        val filled = refRegex.replace(expression) { m -> values[m.value].toString() }
        return if (filled.isBlank()) null else evalArithmetic(filled)
    }

    /** REMAINING：`A − B`，config.options[0] = 被减字段 key，options.getOrNull(1) = 减数字段 key（缺省 0）。 */
    fun remaining(obj: JsonObject, a: String, b: String?): Double? {
        val av = num(obj, a) ?: return null
        val bv = b?.let { num(obj, it) } ?: 0.0
        return av - bv
    }

    /** ELAPSED：日期字段与今天的天数差；options[0] = 日期字段 key。正 = 还有，负 = 已过。 */
    fun elapsedDays(obj: JsonObject, dateKey: String, today: java.time.LocalDate): Long? {
        val ms = dateMs(obj, dateKey) ?: return null
        val target = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(today, target)
    }

    /** 极简四则求值（Shunting-yard，无函数无变量；算不上表达式时返回 null）。 */
    private fun evalArithmetic(expr: String): Double? {
        return try {
        val tokens = Regex("\\d+\\.?\\d*|[+\\-*/()]").findAll(expr.replace(" ", "")).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        val output = ArrayDeque<String>()
        val ops = ArrayDeque<String>()
        val prec = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2)
        var prev: String? = null
        for (t in tokens) {
            when {
                t.toDoubleOrNull() != null -> output.addLast(t)
                t == "(" -> ops.addLast(t)
                t == ")" -> {
                    while (ops.isNotEmpty() && ops.last() != "(") output.addLast(ops.removeLast())
                    if (ops.isEmpty()) return null
                    ops.removeLast()
                }
                else -> {
                    // 一元负号：前一个 token 是运算符或开头
                    if (t == "-" && (prev == null || prev in prec || prev == "(")) { output.addLast("0") }
                    while (ops.isNotEmpty() && ops.last() != "(" && prec[ops.last()]!! >= prec[t]!!) {
                        output.addLast(ops.removeLast())
                    }
                    ops.addLast(t)
                }
            }
            prev = t
        }
        while (ops.isNotEmpty()) {
            val op = ops.removeLast()
            if (op == "(") return null
            output.addLast(op)
        }
        val stack = ArrayDeque<Double>()
        for (t in output) {
            when {
                t.toDoubleOrNull() != null -> stack.addLast(t.toDouble())
                else -> {
                    if (stack.size < 2) return null
                    val b = stack.removeLast(); val a = stack.removeLast()
                    stack.addLast(when (t) {
                        "+" -> a + b; "-" -> a - b; "*" -> a * b
                        "/" -> if (b == 0.0) return null else a / b
                        else -> return null
                    })
                }
            }
        }
        if (stack.size == 1) stack.removeLast() else null
        } catch (_: Exception) { null }
    }
}

/** 复合字段载荷版本化（§十 风险 2）：`{"v":1,...}`。 */
object CompoundPayload {
    const val VERSION = 1

    fun wrap(payload: JsonObject): JsonObject = JsonObject(payload + ("v" to JsonPrimitive(VERSION)))

    fun unwrap(raw: String?): JsonObject? {
        if (raw == null) return null
        return try {
            val obj = kotlinx.serialization.json.Json.decodeFromString<JsonObject>(raw)
            obj
        } catch (_: Exception) { null }
    }
}

/** CHECKLIST 载荷：`{"v":1,"items":[{"text":"...","done":false}]}`。 */
data class ChecklistRow(val text: String, val done: Boolean)

fun parseChecklist(raw: String?): List<ChecklistRow> {
    val obj = CompoundPayload.unwrap(raw) ?: return emptyList()
    val arr = obj["items"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val text = (o["text"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        ChecklistRow(text, (o["done"] as? JsonPrimitive)?.content == "true")
    }
}

fun encodeChecklist(rows: List<ChecklistRow>): String = CompoundPayload.wrap(kotlinx.serialization.json.buildJsonObject {
    put("items", kotlinx.serialization.json.buildJsonArray {
        rows.forEach { add(kotlinx.serialization.json.buildJsonObject {
            put("text", JsonPrimitive(it.text))
            put("done", JsonPrimitive(it.done))
        }) }
    })
}).toString()

/** TABLE 载荷：`{"v":1,"columns":[{"key","label","type"}],"rows":[[cell...]]}`；cell 为 JsonPrimitive 或 {"image":path}。 */
data class TableColumn(val key: String, val label: String, val type: FieldType)
data class TableModel(val columns: List<TableColumn>, val rows: List<List<String>>)

fun parseTable(raw: String?): TableModel {
    val obj = CompoundPayload.unwrap(raw) ?: return TableModel(emptyList(), emptyList())
    val cols = (obj["columns"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val k = (o["key"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val l = (o["label"] as? JsonPrimitive)?.contentOrNull ?: k
        val t = (o["type"] as? JsonPrimitive)?.contentOrNull ?: "TEXT"
        TableColumn(k, l, FieldContract.normalize(t))
    } ?: emptyList()
    val rows = (obj["rows"] as? kotlinx.serialization.json.JsonArray)?.map { el ->
        (el as? kotlinx.serialization.json.JsonArray)?.map { cell ->
            (cell as? JsonPrimitive)?.contentOrNull ?: ""
        } ?: emptyList()
    } ?: emptyList()
    return TableModel(cols, rows)
}

fun encodeTable(model: TableModel): String = CompoundPayload.wrap(kotlinx.serialization.json.buildJsonObject {
    put("columns", kotlinx.serialization.json.buildJsonArray {
        model.columns.forEach { add(kotlinx.serialization.json.buildJsonObject {
            put("key", JsonPrimitive(it.key))
            put("label", JsonPrimitive(it.label))
            put("type", JsonPrimitive(it.type.name))
        }) }
    })
    put("rows", kotlinx.serialization.json.buildJsonArray {
        model.rows.forEach { row -> add(kotlinx.serialization.json.buildJsonArray {
            row.forEach { cell -> add(JsonPrimitive(cell)) }
        }) }
    })
}).toString()

/** RANGE 载荷：`{"v":1,"start":ms,"end":ms}`（或数值 start/end）。 */
data class RangeModel(val start: String, val end: String)

fun parseRange(raw: String?): RangeModel? {
    val obj = CompoundPayload.unwrap(raw) ?: return null
    val s = (obj["start"] as? JsonPrimitive)?.contentOrNull ?: return null
    val e = (obj["end"] as? JsonPrimitive)?.contentOrNull ?: return null
    return RangeModel(s, e)
}

fun encodeRange(r: RangeModel): String = CompoundPayload.wrap(kotlinx.serialization.json.buildJsonObject {
    put("start", JsonPrimitive(r.start))
    put("end", JsonPrimitive(r.end))
}).toString()

/** MAP 载荷（§3.7）：route 路线点 + 可选 track 轨迹 + 可选 underlay 底图 + connect 连线开关。 */
data class MapPoint(val name: String, val lat: Double? = null, val lng: Double? = null, val note: String = "")
data class MapTrackPoint(val lat: Double, val lng: Double, val ele: Double? = null, val t: Long? = null)
data class MapTrack(val pts: List<MapTrackPoint>, val distM: Double, val ascentM: Double, val descentM: Double, val durSec: Long)
data class MapModel(
    val route: List<MapPoint> = emptyList(),
    val connect: Boolean = true,
    val track: MapTrack? = null,
    val underlay: String? = null
)

fun parseMap(raw: String?): MapModel {
    val obj = CompoundPayload.unwrap(raw) ?: return MapModel()
    val route = (obj["route"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        MapPoint(
            name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: "",
            lat = (o["lat"] as? JsonPrimitive)?.content?.toDoubleOrNull(),
            lng = (o["lng"] as? JsonPrimitive)?.content?.toDoubleOrNull(),
            note = (o["note"] as? JsonPrimitive)?.contentOrNull ?: ""
        )
    } ?: emptyList()
    val trackObj = obj["track"] as? JsonObject
    val track = trackObj?.let {
        val pts = (it["pts"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { p ->
            val arr = p as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
            val lat = arr.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = arr.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            MapTrackPoint(lat, lng,
                arr.getOrNull(2)?.jsonPrimitive?.content?.toDoubleOrNull(),
                arr.getOrNull(3)?.jsonPrimitive?.content?.toLongOrNull())
        } ?: emptyList()
        val stat = it["stat"] as? JsonObject
        MapTrack(
            pts = pts,
            distM = (stat?.get("dist") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
            ascentM = (stat?.get("ascent") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
            descentM = (stat?.get("descent") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
            durSec = (stat?.get("dur") as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
        )
    }
    return MapModel(
        route = route,
        connect = (obj["connect"] as? JsonPrimitive)?.content != "false",
        track = track,
        underlay = (obj["underlay"] as? JsonPrimitive)?.contentOrNull
    )
}

/** MAP 全量载荷编码（route + connect + track + underlay）。 */
fun encodeMapModel(model: MapModel): String = CompoundPayload.wrap(buildJsonObject {
    put("route", buildJsonArray {
        model.route.forEach { p -> add(buildJsonObject {
            put("name", JsonPrimitive(p.name))
            if (p.lat != null) put("lat", JsonPrimitive(p.lat))
            if (p.lng != null) put("lng", JsonPrimitive(p.lng))
            if (p.note.isNotBlank()) put("note", JsonPrimitive(p.note))
        }) }
    })
    put("connect", JsonPrimitive(model.connect))
    if (model.track != null) {
        val t = model.track
        put("track", buildJsonObject {
            put("pts", buildJsonArray {
                t.pts.take(300).forEach { p -> add(buildJsonArray {
                    add(JsonPrimitive(p.lat)); add(JsonPrimitive(p.lng))
                    if (p.ele != null) add(JsonPrimitive(p.ele))
                    if (p.t != null) add(JsonPrimitive(p.t))
                }) }
            })
            put("stat", buildJsonObject {
                put("dist", JsonPrimitive(t.distM))
                put("ascent", JsonPrimitive(t.ascentM))
                put("descent", JsonPrimitive(t.descentM))
                put("dur", JsonPrimitive(t.durSec))
                put("n", JsonPrimitive(t.pts.size))
            })
        })
    }
    if (model.underlay != null) put("underlay", JsonPrimitive(model.underlay))
}).toString()
