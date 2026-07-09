package com.palmnote.ui.dashboard

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class CardType {
    NET_WORTH,
    QUICK_ACTIONS,
    BUDGET_ALERT,
    GOALS,
    ANNIVERSARIES,
    ASSET_DISTRIBUTION,
    TODAY
}

@Stable
@Serializable
data class DashboardCardConfig(
    val type: CardType,
    val visible: Boolean = true
) {
    companion object {
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
