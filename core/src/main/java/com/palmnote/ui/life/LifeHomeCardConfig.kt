package com.palmnote.ui.life

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class LifeHomeCardType {
    CATEGORY,
    TODAY_BOARD,
    TODO,
    /** 模板卡（总纲 §4.6「模板即格子」）：卡锚在模板上，身份由 templateId / builtinKey 承载。 */
    TEMPLATE
}

/** 格子五档（§4.3）：XS 1×1 / S 1×1 / M 2×1 / L 2×2 / XL 3×2（以两列网格为基准，XL 独占整行）。 */
enum class LifeCardSpan(val cols: Int, val tall: Boolean) {
    XS(1, false),
    S(1, false),
    M(2, false),
    L(2, true),
    XL(2, true);
}

@Stable
@Serializable
data class LifeHomeCardConfig(
    val type: LifeHomeCardType,
    val visible: Boolean = true,
    /** 档位覆盖值（§4.3 五档）。存 String 保持 JSON 向后兼容。 */
    val span: String = "S",
    /** 排序（§4.4）；0 = 按 type 默认顺序。 */
    val order: Int = 0,
    /** 一个基色派生全部的卡片自定义色（§4.5）；null = 用模板身份色。 */
    val customColor: String? = null,
    /** 模板卡（§4.6）：运行期模板 id；null = 用 builtinKey 按图标身份解析。 */
    val templateId: Long? = null,
    /** 模板卡（§4.6）：内置模板身份键 = 种子 icon 名；用户自建模板卡此值为 null。 */
    val builtinKey: String? = null
) {
    fun spanOf(): LifeCardSpan = LifeCardSpan.entries.firstOrNull { it.name == span } ?: LifeCardSpan.S

    /** 槽位身份：同 type 且模板卡同模板（§4.6 卡锚模板）。布局编辑器按此定位卡。 */
    fun sameSlot(other: LifeHomeCardConfig): Boolean =
        type == other.type && templateId == other.templateId && builtinKey == other.builtinKey

    companion object {
        /** 功能卡默认（不含模板卡——模板卡由 builtinDefault 按身份键补）。 */
        val defaults: List<LifeHomeCardConfig> = listOf(
            LifeHomeCardConfig(LifeHomeCardType.CATEGORY),
            LifeHomeCardConfig(LifeHomeCardType.TODO),
            LifeHomeCardConfig(LifeHomeCardType.TODAY_BOARD)
        )

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        fun toJson(configs: List<LifeHomeCardConfig>): String = json.encodeToString(configs)

        fun fromJson(jsonStr: String): List<LifeHomeCardConfig> {
            return try {
                json.decodeFromString<List<LifeHomeCardConfig>>(jsonStr)
            } catch (_: Exception) {
                defaults
            }
        }
    }
}

/**
 * 布局预设（§4.4）：默认预设只读不可删；自定义最多 3 个，一键切换。
 * 切布局不动任何 LifeItem——只改首页卡的顺序 / 档位 / 颜色。
 */
@Stable
@Serializable
data class LifeHomeLayoutPreset(
    val id: String,
    val name: String,
    val readonly: Boolean = false,
    val configs: List<LifeHomeCardConfig> = LifeHomeCardConfig.defaults
) {
    companion object {
        /** 内置默认预设（§4.3 默认布局 + §4.6 模板卡：功能三卡 + 存钱/倒计时/打卡 XS·S 快照）。
         *  面积 1+2+6+1+1+1 = 12 单元、6 张、大格 2 —— 恰好顶满预算。 */
        val builtinDefault = LifeHomeLayoutPreset(
            id = "default",
            name = "",
            readonly = true,
            configs = listOf(
                LifeHomeCardConfig(LifeHomeCardType.CATEGORY, span = "S", order = 0),
                LifeHomeCardConfig(LifeHomeCardType.TODO, span = "M", order = 1),
                LifeHomeCardConfig(LifeHomeCardType.TODAY_BOARD, span = "XL", order = 2),
                LifeHomeCardConfig(LifeHomeCardType.TEMPLATE, span = "XS", order = 3, builtinKey = "savings"),
                LifeHomeCardConfig(LifeHomeCardType.TEMPLATE, span = "XS", order = 4, builtinKey = "timer_off"),
                LifeHomeCardConfig(LifeHomeCardType.TEMPLATE, span = "S", order = 5, builtinKey = "calendar_month")
            )
        )

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(presets: List<LifeHomeLayoutPreset>): String = json.encodeToString(presets)

        fun decode(raw: String?): List<LifeHomeLayoutPreset> {
            if (raw == null) return emptyList()
            return try {
                json.decodeFromString<List<LifeHomeLayoutPreset>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

/**
 * 能力矩阵（§4.3 硬规则：「每张卡只开放功能撑得起的档位」；不支持档位不出现而非灰掉）。
 * 「今天要做什么」只有 M·XL（无 XS/S/L）；分类三小卡只有 XS/S；待办全档开放。
 */
val LifeHomeCardType.allowedSpans: List<LifeCardSpan>
    get() = when (this) {
        LifeHomeCardType.CATEGORY -> listOf(LifeCardSpan.XS, LifeCardSpan.S)
        LifeHomeCardType.TODAY_BOARD -> listOf(LifeCardSpan.M, LifeCardSpan.XL)
        LifeHomeCardType.TODO -> LifeCardSpan.entries.toList()
        LifeHomeCardType.TEMPLATE -> templateAllowedSpans(null)
    }

/** 模板卡允许档位（§4.3 能力矩阵逐模板行；§4.6 模板卡按身份键取）。 */
fun templateAllowedSpans(builtinKey: String?): List<LifeCardSpan> = when (gridKindOf(builtinKey)) {
    LifeGridCardKind.CHECKIN -> listOf(LifeCardSpan.S, LifeCardSpan.M)
    LifeGridCardKind.SNAPSHOT -> listOf(LifeCardSpan.XS, LifeCardSpan.S)
    LifeGridCardKind.COUNTDOWN, LifeGridCardKind.PROGRESS -> listOf(LifeCardSpan.XS, LifeCardSpan.S, LifeCardSpan.L)
    LifeGridCardKind.FLOW -> listOf(LifeCardSpan.M, LifeCardSpan.XL)
}

/** 编辑器统一入口：功能卡走能力矩阵，模板卡走身份键矩阵。 */
fun LifeHomeCardConfig.allowedSpansFor(): List<LifeCardSpan> =
    if (type == LifeHomeCardType.TEMPLATE) templateAllowedSpans(builtinKey) else type.allowedSpans

/**
 * 模板卡的格子语义族（§4.6 (3) 基数三分类 × §4.3 能力矩阵；按种子 icon 身份判）：
 * - CHECKIN 打卡（可数、要行动）· SNAPSHOT 单值快照 · COUNTDOWN 日期型 · PROGRESS 单一目标 · FLOW 流水。
 * 自建模板（builtinKey = null）回落 PROGRESS（有进度字段的最常见形态）。
 */
enum class LifeGridCardKind { CHECKIN, SNAPSHOT, COUNTDOWN, PROGRESS, FLOW }

fun gridKindOf(builtinKey: String?): LifeGridCardKind = when (builtinKey) {
    "calendar_month" -> LifeGridCardKind.CHECKIN
    "mood", "book", "fitness_center", "BarChart", "timer" -> LifeGridCardKind.SNAPSHOT
    "timer_off", "trending_up", "cake", "celebration", "subscriptions" -> LifeGridCardKind.COUNTDOWN
    "checklist" -> LifeGridCardKind.FLOW
    else -> LifeGridCardKind.PROGRESS
}

/**
 * 面积预算制（§4.3）：张数上限 8；真正约束 Σ 单元 ≤ 12（一屏 15 格）。
 * 单元价：XS/S=1，M=2，L=4，XL=6；XL/M 大格上限 2 且第一张为主角。
 */
object LifeGridBudget {
    const val MAX_CARDS = 8
    const val MAX_UNITS = 12
    const val MAX_LARGE = 2

    fun unitCost(span: LifeCardSpan): Int = when (span) {
        LifeCardSpan.XS, LifeCardSpan.S -> 1
        LifeCardSpan.M -> 2
        LifeCardSpan.L -> 4
        LifeCardSpan.XL -> 6
    }

    fun isWithinBudget(configs: List<LifeHomeCardConfig>): Boolean {
        val units = configs.sumOf { unitCost(it.spanOf()) }
        val large = configs.count { it.spanOf() in listOf(LifeCardSpan.M, LifeCardSpan.L, LifeCardSpan.XL) }
        return configs.size <= MAX_CARDS && units <= MAX_UNITS && large <= MAX_LARGE
    }
}
