package com.palmnote.ui.life

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class LifeHomeCardType {
    CATEGORY,
    TODAY_BOARD,
    TODO
}

@Stable
@Serializable
data class LifeHomeCardConfig(
    val type: LifeHomeCardType,
    val visible: Boolean = true
) {
    companion object {
        val defaults: List<LifeHomeCardConfig> = LifeHomeCardType.entries.map { LifeHomeCardConfig(it) }

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
