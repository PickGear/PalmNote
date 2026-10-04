package com.palmnote.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 模板级提醒配置（总纲 §提醒）：显式落在模板行上，Worker 按字段配置分发，**不靠图标猜**。
 *
 * 存储为 `life_templates.reminderConfig` 列（JSON；null = 未配置，Worker 按内置图标兜底推断，
 * 兼容迁移前的存量数据）。老安装由 Migration9To10 按图标回填，此后改图标不再影响提醒。
 *
 * 两层开关：
 * - 模板级 [enabled]：整个模板不提醒；
 * - 记录级：条目 fieldsData 里的 `reminder` 布尔字段（缺省开，显式 false 才跳过）。
 */
@Serializable
data class ReminderSpec(
    val kind: Kind,
    /**
     * 日期字段的 key（DATE/DATETIME）。SUBSCRIPTION 不看日期字段（用扣费日数字键），可空；
     * 其他 kind 留空时 Worker 回退到该 kind 的历史键（start_date / targetDate / date）。
     */
    val dateKey: String? = null,
    val enabled: Boolean = true
) {
    enum class Kind {
        /** 正数日里程碑（100/200/365…天）。 */
        MILESTONE,
        /** 倒数日：到期当天 + 临近提前提醒。 */
        COUNTDOWN,
        /** 生日（按年循环）。 */
        BIRTHDAY,
        /** 周年纪念日（按年循环）。 */
        ANNIVERSARY,
        /** 订阅扣费日提醒（monthly/quarterly/yearly 周期）。 */
        SUBSCRIPTION
    }

    fun toJson(): String = JSON.encodeToString(this)

    companion object {
        /** 解析失败按未配置处理（脏数据不炸 Worker）。 */
        fun fromJson(raw: String?): ReminderSpec? = raw?.let {
            runCatching { JSON.decodeFromString<ReminderSpec>(it) }.getOrNull()
        }

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
