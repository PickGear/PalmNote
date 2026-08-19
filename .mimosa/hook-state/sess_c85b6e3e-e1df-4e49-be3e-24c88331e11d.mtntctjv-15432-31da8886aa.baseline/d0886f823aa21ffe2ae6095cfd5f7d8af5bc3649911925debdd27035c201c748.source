package com.palmnote.ui.theme

import androidx.compose.ui.graphics.Color

object ColorResolver {

    private val overrides = mutableMapOf<String, Color>()

    fun resolve(category: String, fallback: Color = Gray400): Color {
        return overrides[category] ?: fallback
    }

    fun loadOverrides(overridesMap: Map<String, String>) {
        overrides.clear()
        overridesMap.forEach { (key, hex) ->
            try {
                overrides[key] = Color(android.graphics.Color.parseColor(hex))
            } catch (_: Exception) {}
        }
    }

    fun loadPresetColorOverrides(presetOverrides: Map<String, String>) {
        presetOverrides.forEach { (key, json) ->
            try {
                val obj = org.json.JSONObject(json)
                if (obj.has("color")) {
                    val hex = obj.getString("color")
                    val categoryName = when {
                        key.startsWith("preset_EXPENSE_") -> key.removePrefix("preset_EXPENSE_")
                        key.startsWith("preset_INCOME_") -> key.removePrefix("preset_INCOME_")
                        key.startsWith("preset_") -> key.removePrefix("preset_")
                        else -> return@forEach
                    }
                    overrides[categoryName] = Color(android.graphics.Color.parseColor(hex))
                }
            } catch (_: Exception) {}
        }
    }
}
