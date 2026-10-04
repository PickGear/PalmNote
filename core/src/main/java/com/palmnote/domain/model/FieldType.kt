package com.palmnote.domain.model

import kotlinx.serialization.Serializable

/**
 * 字段类型（§3.1 字段库 v2：34 种 = 26 原子 + 4 复合 + 4 派生）。
 * v1.26 新增 12 枚举：VIDEO/AUDIO/TAG/MAP（原子）、CHECKLIST/TABLE/RANGE/PERSON（复合 E 组）、
 * FORMULA/REMAINING/STREAK/ELAPSED（派生 F 组）。
 * ⚠️ PERCENTAGE 已并入 PERCENT（§3.2）；枚举值保留只为读取旧数据，契约层两者同槽。
 * 红线：序列化名即存储名，只增不改不删。
 */
@Serializable
enum class FieldType {
    TEXT, NUMBER, DATE, BOOLEAN, SELECT, MULTI_SELECT, IMAGE, LOCATION, TIME, PERCENT, RATING,
    SHORT_TEXT, SLIDER, PERCENTAGE, URL, EMAIL, PHONE, COLOR, DURATION,
    CURRENCY, DATETIME, RICH_TEXT, FILE,
    VIDEO, AUDIO, TAG, MAP,
    CHECKLIST, TABLE, RANGE, PERSON,
    FORMULA, REMAINING, STREAK, ELAPSED
}

/**
 * 字段配置（fieldsConfig JSON 的存储模型）。
 * v1.26 新增三键（全部带默认值，JSON 向后兼容、零迁移）：
 * - progressStyle：进度形态覆盖（§4.2；null = AUTO 按语义推导；认不出的值回落 AUTO）
 * - progressTargetKey：进度分母字段的 key（§7.2；空 = 用 min/max）
 * - step：步进器步长（CURRENCY 默认步长由模板给，如存钱 500）
 */
@Serializable
data class FieldConfig(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    val defaultValue: String = "",
    val options: List<String> = emptyList(),
    val placeholder: String = "",
    /**
     * ⚠️ **目前无消费方**（2026-09-30 全仓审计：零引用，字段面板也不露出）。
     * 保留键位是为了不改存储格式；真要启用得先定校验语法（正则？长度？）。
     */
    val validation: String = "",
    val unit: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val showInCard: Boolean = false,
    /**
     * ⚠️ **目前无消费方**（只在"停用字段"时被置 false；字段面板无开关、渲染层不读）。
     * 语义本应是「在完整清单/列表行里显示这个字段的值」，但那一行是固定 44dp 单行胶囊，
     * 要接上得先重设计列表行 —— 别以为打开它就有效果。
     */
    val showInList: Boolean = false,
    val showAsProgress: Boolean = false,
    /** 停用字段（§4.1：内置模板有数据的字段不能删，只能停用；历史数据保留，不再渲染）。 */
    val disabled: Boolean = false,
    val progressStyle: String? = null,
    val progressTargetKey: String = "",
    val sortOrder: Int = 0
)
