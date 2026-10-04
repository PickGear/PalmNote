package com.palmnote.domain.model

/**
 * 派生字段的**参考字段**配置（纯函数层）。
 *
 * ## 存储契约（这是本文件存在的理由）
 *
 * `FieldConfig` 没有「参考字段」这种键，它只有 `options` 与 `defaultValue` 两个通用槽位，
 * 派生类型就借用它们：
 *
 * | 类型 | 参考信息放在哪 | 语义 |
 * |---|---|---|
 * | `REMAINING` | `options[0]` = **被减**字段 key；`options[1]` = **减数**字段 key（可空 = 0） | `A − B` |
 * | `ELAPSED` | `options[0]` = **日期**字段 key | 与今天的天数差 |
 * | `FORMULA` | `defaultValue` = **表达式**（字段 key 与 `+ - * / ( )`） | 四则运算 |
 * | `STREAK` | —— | 跨记录自动统计，**无需**参考 |
 *
 * 这套借位此前**没有任何 UI 露出**：字段面板只对「点选类」显示 `options` 输入框、
 * 对派生类连输入框都没有，于是用户想配「还差 = 目标 − 当前」只能**手打字段 key**
 * （`targetAmount` 这种），打错就静默算不出、字段在卡片与详情页里**直接消失**。
 * 本文件把槽位语义收成可测的纯函数，UI 侧只需调它们，不再各写一份拼串逻辑。
 *
 * ⚠️ `options` 是**位置型**的：不允许中间出现空洞（否则 `options[1]` 会串位）。
 * UI 因此要求**先选被减字段**（`REMAINING` 的减数槽在未选被减时不可点）——
 * 见 [withDerivedRef] 的 `dropLastWhile` 收尾，它只裁掉**尾部**空槽。
 */

/** 需要「参考字段」才能算出来的派生类型（`STREAK` 自动统计，不需要）。 */
fun needsDerivedRef(type: FieldType): Boolean = type == FieldType.REMAINING || type == FieldType.ELAPSED

/** 参考槽位数：`REMAINING` 两个（被减 / 减），`ELAPSED` 一个（日期）。 */
fun derivedRefSlots(type: FieldType): Int = if (type == FieldType.REMAINING) 2 else 1

/** 能当参考的字段类型（自我约束，不依赖别处的类型集合，避免集合漂移）。 */
private val NUMERIC_REF_TYPES = setOf(
    FieldType.NUMBER, FieldType.CURRENCY, FieldType.PERCENT, FieldType.PERCENTAGE,
    FieldType.SLIDER, FieldType.DURATION, FieldType.RATING,
    FieldType.FORMULA, FieldType.REMAINING
)
private val DATE_REF_TYPES = setOf(FieldType.DATE, FieldType.DATETIME)

/**
 * 某槽位的候选字段：`ELAPSED` 只能引日期类、`REMAINING` 只能引数值类；
 * 一律排除自己与已停用字段（引用一个停用字段等于引用一个空值）。
 */
fun derivedRefCandidates(
    type: FieldType,
    fields: List<FieldConfig>,
    selfKey: String
): List<FieldConfig> {
    val allowed = if (type == FieldType.ELAPSED) DATE_REF_TYPES else NUMERIC_REF_TYPES
    return fields.filter { !it.disabled && it.key != selfKey && it.type in allowed }
}

/**
 * 把第 [slot] 个参考 key 写进 `options`（纯函数，自动补齐长度）。
 *
 * 两条不变量（`options` 是**位置型**的，破坏任何一条都会让减法读错字段）：
 * 1. **前导槽必须先有值**：槽 1 有值而槽 0 为空时，本次写入被忽略（返回原值）；
 * 2. **清空某槽要连带清掉它后面的槽**：`["a","b"]` 清掉槽 0 应得到 `[]`，而不是 `["","b"]`。
 */
fun withDerivedRef(options: List<String>, slot: Int, key: String): List<String> {
    if (slot < 0) return options
    val next = options.toMutableList()
    while (next.size <= slot) next.add("")
    if (slot > 0 && next.subList(0, slot).any { it.isBlank() }) return options
    next[slot] = key.trim()
    if (next[slot].isBlank()) {
        for (i in slot until next.size) next[i] = ""
    }
    return next.map { it.trim() }.dropLastWhile { it.isBlank() }
}

/** 某槽位当前选中的 key（空 = 未选）。 */
fun derivedRefKey(options: List<String>, slot: Int): String =
    options.getOrNull(slot).orEmpty()

/**
 * 表达式里引用的字段 key（与 `DerivedEvaluator.formula` 的 `[A-Za-z_][A-Za-z0-9_]*` 同一口径）。
 *
 * 口径必须一致：这里多认或少认一个 key，都会让"配不全"的判断与实际求值对不上。
 */
fun formulaRefKeys(expression: String): List<String> =
    Regex("[A-Za-z_][A-Za-z0-9_]*").findAll(expression).map { it.value }.distinct().toList()

/** 这些 key 里有没有**指不到活字段**的（不存在 / 已停用）——空串按"没填"跳过，由调用方各自判必填。 */
private fun anyDangling(keys: List<String>, fields: List<FieldConfig>): Boolean =
    keys.any { key -> key.isNotBlank() && fields.none { it.key == key && !it.disabled } }

/**
 * 派生字段**是否配置不全**（面板与字段行都据此提示）。
 *
 * 「配不全」的定义就是「它一定算不出」，三种情形：
 * - `FORMULA`：没有表达式，**或表达式引用了解析不到的字段 key**；
 * - `REMAINING`：没有被减字段，或任一被引用的字段不存在 / 已停用；
 * - `ELAPSED`：没有日期字段，或引用的日期字段不存在 / 已停用。
 *
 * ⚠️ 后两条（**引用了不存在的字段**）是曾经的漏网之鱼：只查"槽位是否为空"时，
 * 用户把 `targetAmount` 打成 `targetAmout` 依然算"已配置"，
 * 而 `DerivedFields.eval` 会返回 null、上层不渲染 —— **字段直接消失，且没有任何提示**。
 * 表达式的情况同理（`formula` 少一个引用就返回 null）。
 *
 * `STREAK` 永远返回 false（靠跨记录统计，无需配置）；已停用字段也不提示
 * （停用是用户明确的选择，不是配置错误）。
 */
fun derivedUnconfigured(cfg: FieldConfig, fields: List<FieldConfig>): Boolean = when {
    cfg.disabled -> false
    cfg.type == FieldType.FORMULA -> {
        val expr = cfg.defaultValue
        expr.isBlank() || anyDangling(formulaRefKeys(expr), fields)
    }
    cfg.type == FieldType.REMAINING -> {
        val minuend = cfg.options.getOrNull(0).orEmpty()
        // 槽 1（减数）可选（缺省按 0），但**一旦填了就必须指得到字段**
        minuend.isBlank() || anyDangling(listOf(minuend, cfg.options.getOrNull(1).orEmpty()), fields)
    }
    cfg.type == FieldType.ELAPSED -> {
        val dateKey = cfg.options.getOrNull(0).orEmpty()
        dateKey.isBlank() || anyDangling(listOf(dateKey), fields)
    }
    else -> false
}
