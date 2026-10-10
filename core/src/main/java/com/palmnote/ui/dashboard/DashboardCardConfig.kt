package com.palmnote.ui.dashboard

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 首页卡片类型。**枚举声明顺序 = 未自定义时的首页卡片排列顺序**（`defaults` 直接按 entries 展开），
 *  调整顺序前先对照卡片管理弹窗的既定排布（2026-10-11 定稿：物品分布紧随预算预警、纪念日其后）。 */
enum class CardType {
    NET_WORTH,
    QUICK_ACTIONS,
    BUDGET_ALERT,
    ASSET_DISTRIBUTION,
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
    val customColor: String? = null
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
