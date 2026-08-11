package com.palmnote.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class FieldType {
    TEXT, NUMBER, DATE, BOOLEAN, SELECT, MULTI_SELECT, IMAGE, LOCATION, TIME, PERCENT, RATING
}

@Serializable
data class FieldConfig(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    val defaultValue: String = "",
    val options: List<String> = emptyList(),
    val placeholder: String = "",
    val validation: String = "",
    val unit: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val showInCard: Boolean = false,
    val showInList: Boolean = false,
    val sortOrder: Int = 0
)
