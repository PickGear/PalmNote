package com.palmnote.ui.life.common

import com.palmnote.data.db.entity.LifeItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun getTodoPriority(item: LifeItem): String {
    return try {
        val obj = Json.decodeFromString<JsonObject>(item.fieldsData)
        (obj["priority"] as? JsonPrimitive)?.content ?: "NONE"
    } catch (_: Exception) { "NONE" }
}
