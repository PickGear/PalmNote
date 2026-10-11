package com.palmnote.ui.life

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 相册字段（IMAGE）的载荷（总纲 §3.7(2) 相册升级）：每张照片一个路径 + 可选标题。
 *
 * **载荷三种形态都认，零迁移**（沿用原类型名，只换 fieldsData 里的 JSON 内容）：
 * - 旧单图：裸路径字符串（`/data/.../a.jpg` 或 `content://...`）；
 * - 旧数组：字符串数组（历史多图尝试的遗留）；
 * - 新载荷：对象数组 `[{"p":路径,"t":标题}]`。
 *
 * 序列化统一写成对象数组；标题为空时省略 `t`，让新载荷尽量短。
 */
data class LifePhoto(val path: String, val title: String = "")

private val photosJson = Json { ignoreUnknownKeys = true }

internal fun parsePhotos(raw: String?): List<LifePhoto> {
    if (raw.isNullOrBlank()) return emptyList()
    val parsed = runCatching {
        when (val el = photosJson.parseToJsonElement(raw)) {
            is JsonArray -> el.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }?.let { LifePhoto(it) }
                    is JsonObject -> (item["p"] as? JsonPrimitive)?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { p -> LifePhoto(p, (item["t"] as? JsonPrimitive)?.contentOrNull.orEmpty()) }
                    else -> null
                }
            }
            // 裸路径（旧单图）不是合法 JSON 数组时走到这里，原样当一张
            else -> listOf(LifePhoto(raw))
        }
    }.getOrNull()
    // JSON 解析失败 = 裸路径，按单张兜底；绝不静默丢图
    return parsed ?: listOf(LifePhoto(raw))
}

internal fun serializePhotos(photos: List<LifePhoto>): String = buildJsonArray {
    photos.forEach { photo ->
        add(buildJsonObject {
            put("p", photo.path)
            if (photo.title.isNotBlank()) put("t", photo.title)
        })
    }
}.toString()
