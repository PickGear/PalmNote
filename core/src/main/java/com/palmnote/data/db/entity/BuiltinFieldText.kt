package com.palmnote.data.db.entity

import android.content.Context
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType

/**
 * 内置模板的**字段文案**（label / unit / 表头 / 占位提示）是**建库时的中文**，
 * 存在 `life_templates.fieldsConfig` 里 —— 和模板名一样，切到英文后直接读会漏中文。
 *
 * 与模板名走 `getDisplayName` 同样的思路：**在展示层翻译，不改库**。
 * 为什么不做「按语言重播种子」：模板不是演示数据，重播会覆盖用户对内置模板的字段改动；
 * 而且语言相关文案一旦落库，切语言就得再洗一遍库，越洗越乱。
 *
 * 表只覆盖**内置模板用到的那批词**（不是通用词典）：认不出的原文原样返回，
 * 所以用户自建的字段名、自填的选项值都不会被误翻。
 */
object BuiltinFieldText {

    private fun isChinese(context: Context): Boolean =
        context.resources.configuration.locales[0].language.startsWith("zh")

    /** 存库的中文文案 → 当前语言的展示文案（认不出 / 已是中文环境 → 原样返回）。 */
    fun localize(context: Context, raw: String): String =
        if (isChinese(context)) raw else EN[raw] ?: UNIT_EN[raw] ?: raw

    /** 单位单独一张表：「天」当表头是 Days、当单位是 d，同一个词两种角色不能共用一条。 */
    private fun localizeUnit(context: Context, raw: String): String =
        if (isChinese(context)) raw else UNIT_EN[raw] ?: EN[raw] ?: raw

    /**
     * 字段配置的展示层翻译：`label`、`unit`、占位提示、以及 TABLE 列头
     * （`options` 的 `key:表头:类型` 中间那一段 —— key 与类型是结构，不能动）。
     *
     * **MULTI_SELECT 的 options 不动**：那是**值域**，翻译它会让「选中的值」与
     * 「选项」对不上（存进去的就是选项原文）。选项在渲染处用 [localize] 单独翻译显示。
     */
    fun localizeConfigs(context: Context, configs: List<FieldConfig>): List<FieldConfig> {
        if (isChinese(context)) return configs
        return configs.map { cfg ->
            cfg.copy(
                label = localize(context, cfg.label),
                unit = localizeUnit(context, cfg.unit),
                placeholder = localize(context, cfg.placeholder),
                options = if (cfg.type == FieldType.TABLE) {
                    cfg.options.map { localizeTableOption(context, it) }
                } else {
                    cfg.options
                }
            )
        }
    }

    private fun localizeTableOption(context: Context, option: String): String {
        val parts = option.split(":")
        if (parts.size != 3) return option
        return parts[0] + ":" + localize(context, parts[1]) + ":" + parts[2]
    }

    /** 字段名 / 表头 / 占位提示。 */
    private val EN: Map<String, String> = mapOf(
        "备注" to "Note",
        "提醒" to "Reminder",
        "配图" to "Image",
        "照片" to "Photos",
        "评分" to "Rating",
        "日期" to "Date",
        "人物" to "People",
        "心情" to "Mood",
        "目标日期" to "Target date",
        "目标金额" to "Target amount",
        "已存金额" to "Saved amount",
        "还差" to "Remaining",
        "凭证" to "Receipt",
        "明细" to "Details",
        "预算金额" to "Budget",
        "已花费" to "Spent",
        "店铺" to "Store",
        "分类" to "Category",
        "购物明细" to "Shopping list",
        "截止日期" to "Due date",
        "子任务" to "Subtasks",
        "目的地" to "Destination",
        "出发日期" to "Departure date",
        "返程日期" to "Return date",
        "预算" to "Budget",
        "已订" to "Booked",
        "同行人" to "Companions",
        "行程明细" to "Itinerary",
        "路线" to "Route",
        "总页数" to "Total pages",
        "当前页数" to "Current page",
        "作者" to "Author",
        "标签" to "Tags",
        "封面" to "Cover",
        "书摘" to "Excerpts",
        "课程名称" to "Course name",
        "总节数" to "Total lessons",
        "完成节数" to "Lessons done",
        "单次时长" to "Session length",
        "课时清单" to "Lesson list",
        "笔记" to "Notes",
        "剩余天数" to "Days left",
        "起始日期" to "Start date",
        "已经过" to "Elapsed",
        "里程碑" to "Milestones",
        "头像" to "Avatar",
        "农历" to "Lunar date",
        "相册" to "Album",
        "目标天数" to "Target days",
        "连续天数" to "Streak",
        "精力" to "Energy",
        "影响因素" to "Factors",
        "天气" to "Weather",
        "正文" to "Content",
        "地点" to "Location",
        "扣费金额" to "Amount",
        "扣费周期" to "Billing cycle",
        "扣费日" to "Billing day",
        "下次扣费" to "Next charge",
        "距扣费" to "Days to charge",
        "扣费历史" to "Billing history",
        "管理链接" to "Manage link",
        "体重" to "Weight",
        "睡眠" to "Sleep",
        "运动" to "Exercise",
        "身高" to "Height",
        "下次体检" to "Next checkup",
        "物品" to "Item",
        "更换日期" to "Replaced on",
        "更换周期" to "Replace every",
        // ── 表头（TABLE 列的中间段）──
        "名目" to "Item",
        "金额" to "Amount",
        "品名" to "Name",
        "单价" to "Unit price",
        "数量" to "Qty",
        "已买" to "Bought",
        "天" to "Days",
        "交通" to "Transport",
        "费用" to "Cost",
        // ── 占位提示 ──
        "今天学到了什么？" to "What did you learn today?",
        "今天最值得记住的一件小事？" to "One small thing worth remembering today?",
        "摘一句打动你的话" to "A line that stayed with you",
        "此刻的感觉，因为什么？" to "How do you feel right now, and why?"
    )

    /** 单位（跟在数字后面）。 */
    private val UNIT_EN: Map<String, String> = mapOf(
        "元" to " CNY",
        "分钟" to " min",
        "小时" to " h",
        "号" to "",
        "天" to " d",
        "节" to "",
        "页" to " p"
    )
}
