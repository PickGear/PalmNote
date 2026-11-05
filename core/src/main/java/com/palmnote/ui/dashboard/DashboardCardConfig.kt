package com.palmnote.ui.dashboard

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 首页卡片类型。**枚举声明顺序 = 未自定义时的首页卡片排列顺序**（`defaults` 直接按 entries 展开），
 *  调整顺序前先对照卡片管理弹窗的既定排布（2026-11-05 定稿：物品分布排在预算预警前）。
 *  注意：配置按枚举**名字**序列化，改顺序不影响已保存的自定义排列。 */
enum class CardType {
    NET_WORTH,
    QUICK_ACTIONS,
    ASSET_DISTRIBUTION,
    BUDGET_ALERT,
    ANNIVERSARIES,
    TODAY,
    VAULT,
    HABIT_TODAY,
    SUBSCRIPTION
}

@Stable
@Serializable
data class DashboardCardConfig(
    val type: CardType,
    val visible: Boolean = true,
    val customColor: String? = null,
    /**
     * 预算卡要展示哪条预算（卡片管理里选）。四个字段同进同退：
     * [budgetPeriod] / [budgetScope] 为 null = 自动（最严重 → 使用率最高）；
     * [budgetCategory] 只在 scope = CATEGORY 时有意义，[budgetBookId] 只在 BOOK 时有意义。
     */
    val budgetPeriod: String? = null,
    val budgetScope: String? = null,
    val budgetCategory: String? = null,
    val budgetBookId: Long? = null
) {
    companion object {
        // 默认全部打开：显隐完全由用户在卡片管理里决定，应用不做自作主张的预关
        val defaults: List<DashboardCardConfig> = CardType.entries.map { DashboardCardConfig(it) }

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

        fun toJson(configs: List<DashboardCardConfig>): String = json.encodeToString(configs)

        fun fromJson(jsonStr: String): List<DashboardCardConfig> {
            return try {
                json.decodeFromString<List<DashboardCardConfig>>(jsonStr)
            } catch (_: Exception) {
                defaults
            }
        }
    }
}
