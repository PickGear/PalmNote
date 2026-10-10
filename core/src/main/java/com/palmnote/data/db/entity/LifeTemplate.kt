package com.palmnote.data.db.entity

import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable
import com.palmnote.R

@Entity(tableName = "life_templates", indices = [
    Index(value = ["category"], name = "idx_template_category"),
    Index(value = ["isHidden"], name = "idx_template_visible")
])
@Immutable
data class LifeTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String,
    val icon: String,
    val color: String,
    val description: String = "",
    val fieldsConfig: String,
    /**
     * ⚠️ **无消费方**（2026-09-30 审计）：保存时恒写 `"card"`，没有任何渲染/逻辑读它。
     * 它原本服务于「一份数据多个视图」的切换器，该特性未实现。
     */
    val layoutType: String,
    /** ⚠️ **无消费方**（同上）：保存时恒写 `["card","list"]`，没人读。 */
    val availableLayouts: String,
    /**
     * ⚠️ **无消费方**（同上）：种子会写 `flowJson("ARCHIVED" to "已归档")`，但没有任何代码读它
     * —— 状态流目前用的是固定的 ACTIVE/COMPLETED/ARCHIVED，不是这份配置。
     */
    val statusFlowConfig: String,
    /**
     * ⚠️ **无消费方**（同上）：种子里的 `allowCrossLink`（16 处）**没有任何读者**。
     * 关联功能本身是有的（`TriggerEngine` 自动建链 + 详情页只读展示计数），
     * 但建链由触发器规则决定，这个开关既不拦也不放 —— 别以为关掉它就不建链。
     *
     * 决定（2026-10-01）：上面四处死配置（本字段 + `layoutType` / `availableLayouts` /
     * `statusFlowConfig`）**也不做删列迁移** —— 它们无害，而"删列"本身要重建表；
     * 收益是零，风险不为零。注解保留，作用是不让下一个人误以为它们有效。
     */
    val linkConfig: String,
    /** 模板级提醒配置（[com.palmnote.domain.model.ReminderSpec] 的 JSON）；null = 未配置，Worker 按图标兜底推断。 */
    val reminderConfig: String? = null,
    val isBuiltin: Boolean = false,
    val isHidden: Boolean = false,
    val isSpecial: Boolean = false,
    val sortOrder: Int = 0,
    /**
     * **语义身份**（[com.palmnote.domain.util.LifeTemplateKind] 的枚举名），与 [icon] 分离。
     *
     * [icon] 是**显示**（用户随便改），[kind] 是**行为**（连击算不算、详情页用哪个 hero、
     * 统计进不进完成率）。此前两者共用 icon，改图标就会静默改语义。
     *
     * 可空：v12 之前的存量行由 `MIGRATION_11_12` 按 icon 回填；
     * 运行期取不到时由 `LifeTemplate.getKind()` 兜底（见该函数 KDoc）。
     */
    val kind: String? = null,
    /**
     * **每年重复**（生日 / 纪念日这类）：到期后自动滚到下一次，而不是停在那一天。
     *
     * 语义落点在「读数」而不是「记录」上：开启后，该模板记录的**距离天数**按
     * **下一次周年**算（含农历——生日模板的 `lunar` 字段为真时按农历反算今年公历日），
     * 于是「剩余天数」永远是「距下次还有几天」，而不是某个已经过去的日期。
     *
     * 为什么放在模板而不是字段：这与倒数日品类（Days Matter 等）的做法一致 ——
     * 重复与否是**整个事件**的属性（生日每年都过，高考倒计时只发生一次），
     * 同一条记录里的两个日期字段各滚各的会让人无法理解。
     */
    val repeatYearly: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 内置模板名的资源 id（按 [LifeTemplate.icon] 认身份）。
 *
 * 内置模板的 `name` 列存的是**建库时的中文**，切到英文后直接读 `name` 会漏出中文，
 * 所以凡展示内置模板名的地方都要走这里（或 [getDisplayName]），不要直接读 `name`。
 */
fun builtinTemplateNameRes(icon: String): Int? = BUILTIN_NAME_RES[icon]

private val BUILTIN_NAME_RES: Map<String, Int> = mapOf(
    "savings" to R.string.template_savings_name,
    "shopping_cart" to R.string.template_shopping_name,
    "checklist" to R.string.template_todo_name,
    "flight" to R.string.template_travel_name,
    "menu_book" to R.string.template_reading_name,
    "school" to R.string.template_study_name,
    "timer_off" to R.string.template_countdown_name,
    "trending_up" to R.string.template_countup_name,
    "cake" to R.string.template_birthday_name,
    "celebration" to R.string.template_anniversary_name,
    "calendar_month" to R.string.template_checkin_name,
    "mood" to R.string.template_mood_name,
    "book" to R.string.template_diary_name,
    "subscriptions" to R.string.template_subscription_name,
    "BarChart" to R.string.template_report_name,
    "timer" to R.string.template_focus_name,
    "fitness_center" to R.string.template_body_name,
    "build" to R.string.template_maintenance_name
)

fun LifeTemplate.getDisplayName(context: Context): String {
    if (!isBuiltin) return name
    return builtinTemplateNameRes(icon)?.let(context::getString) ?: name
}

/**
 * 只有「名字 + 图标 + 是否内置」三件套时的显示名。
 *
 * 给 DAO 投影行（如月度回顾的 `TemplateMonthCount`）用：这些行没有完整实体，
 * 但同样不能把内置模板的中文 `name` 直接摆到英文界面上。
 */
fun templateDisplayName(context: Context, name: String, icon: String, isBuiltin: Boolean): String {
    if (!isBuiltin) return name
    return builtinTemplateNameRes(icon)?.let(context::getString) ?: name
}

fun LifeTemplate.getDisplayDescription(context: Context): String {
    if (!isBuiltin) return description
    return when (icon) {
        "savings" -> context.getString(R.string.template_savings_desc)
        "shopping_cart" -> context.getString(R.string.template_shopping_desc)
        "checklist" -> context.getString(R.string.template_todo_desc)
        "flight" -> context.getString(R.string.template_travel_desc)
        "menu_book" -> context.getString(R.string.template_reading_desc)
        "school" -> context.getString(R.string.template_study_desc)
        "timer_off" -> context.getString(R.string.template_countdown_desc)
        "trending_up" -> context.getString(R.string.template_countup_desc)
        "cake" -> context.getString(R.string.template_birthday_desc)
        "celebration" -> context.getString(R.string.template_anniversary_desc)
        "calendar_month" -> context.getString(R.string.template_checkin_desc)
        "mood" -> context.getString(R.string.template_mood_desc)
        "book" -> context.getString(R.string.template_diary_desc)
        "subscriptions" -> context.getString(R.string.template_subscription_desc)
        "BarChart" -> context.getString(R.string.template_report_desc)
        "timer" -> context.getString(R.string.template_focus_desc)
        "fitness_center" -> context.getString(R.string.template_body_desc)
        "build" -> context.getString(R.string.template_maintenance_desc)
        else -> description
    }
}
