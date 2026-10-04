package com.palmnote.ui.life

import androidx.compose.ui.graphics.Color
import com.palmnote.app.R
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldContracts
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.ProgressForm
import com.palmnote.domain.model.ProgressValue
import com.palmnote.domain.model.compoundRawOf
import com.palmnote.domain.model.parseChecklist
import com.palmnote.domain.model.parseMap
import com.palmnote.domain.model.parseRange
import com.palmnote.domain.model.parseTable
import com.palmnote.domain.model.resolveProgressForm
import com.palmnote.domain.util.DateUtils
import com.palmnote.ui.theme.LifeMoodAngry
import com.palmnote.ui.theme.LifeMoodHappy
import com.palmnote.ui.theme.LifeMoodNormal
import com.palmnote.ui.theme.LifeMoodSad
import com.palmnote.ui.theme.LifeMoodUpset
import com.palmnote.ui.theme.ModuleLife
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.palmnote.ui.utils.LifeNumFormat
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import com.palmnote.domain.util.StreakEngine
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 英雄区形态 —— 设计稿 `gen_detail_svg.py` 的 9 种（总纲 §14.12(3)）。
 * 形态由**模板身份**决定（不是由「主字段」猜：§14.10(3) 已把「主字段」一词作废）；
 * 模板身份取 `icon`（与种子 / 身份色同一真源）。
 */
enum class HeroForm { MONEY3, DAYS, ROUTE, COVER, NOTE, TODO, MOOD, TIMER }

/**
 * 详情页数据上下文：把 `LifeItem` + `LifeTemplate` 解析**一次**，四段骨架共用。
 *
 * 三条硬规矩（§14.2）落在这里：
 * 1. 不再猜 key —— 一切经 [FieldConfig]（`fieldsConfig`）解析；
 * 2. 空值**照常占位**（返回 null，由行渲染器给 `—`），而不是整条消失；
 * 3. 日期一律经 [DateUtils.parseDateValueOrNull]（毫秒 / `yyyy-MM-dd` 两种写法都认）。
 */
class DetailCtx(
    val item: LifeItem,
    val template: LifeTemplate,
    val configs: List<FieldConfig>,
    val obj: JsonObject,
    val accent: Color,
    val aggregates: DetailAggregates = DetailAggregates()
) {
    val today: LocalDate = LocalDate.now()

    fun cfg(key: String): FieldConfig? = configs.firstOrNull { it.key == key }

    fun cfgByType(vararg types: FieldType): FieldConfig? = configs.firstOrNull { it.type in types }

    private fun prim(key: String): JsonPrimitive? = obj[key] as? JsonPrimitive

    fun str(key: String): String? = prim(key)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** 复合字段（清单/表格）：字符串原语与嵌套对象两种载荷都认（见 compoundRawOf）。 */
    fun raw(key: String): String? = compoundRawOf(obj[key])

    fun num(key: String): Double? = str(key)?.toDoubleOrNull()

    fun flag(key: String): Boolean? = str(key)?.lowercase()?.let { it == "true" || it == "1" }

    /** 多选值：既接受 `["a","b"]`，也接受 `"a,b"`（两种历史写法都认）。 */
    fun list(key: String): List<String> = when (val el = obj[key]) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }
        is JsonPrimitive -> el.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        else -> emptyList()
    }

    fun dateMs(key: String): Long? = DateUtils.parseDateValueOrNull(str(key))

    fun date(key: String): LocalDate? = dateMs(key)?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    fun dateText(key: String): String? = date(key)?.format(MD)

    /** 该字段的进度值；**取不到分母就不给进度**（绝不拿 0 冒充，§14.1 缺陷类）。 */
    fun progressOf(config: FieldConfig?): ProgressValue? {
        val c = config ?: return null
        val checklist = if (c.type == FieldType.CHECKLIST) parseChecklist(raw(c.key)) else emptyList()
        val cur = if (c.type == FieldType.CHECKLIST) {
            checklist.count { it.done }.toDouble()
        } else {
            num(c.key) ?: derived(c) ?: return null
        }
        val target = if (c.type == FieldType.CHECKLIST) {
            checklist.size.takeIf { it > 0 }?.toDouble() ?: return null
        } else {
            c.progressTargetKey.takeIf { it.isNotBlank() }?.let { num(it) }
                ?: c.max
                ?: return null
        }
        if (target <= 0.0) return null
        return ProgressValue(cur, target, (cur / target).toFloat().coerceIn(0f, 1f))
    }

    /**
     * 派生字段求值（REMAINING / ELAPSED / FORMULA）；算不出返回 null。
     *
     * **「每年重复」的模板**：`ELAPSED` 按**下一次周年**算（生日过去后显示「还有 364 天」而不是
     * 「已过 1 天」）。农历与否取自生日模板的 `lunar` 字段 —— 与提醒 Worker 同一判据。
     */
    fun derived(config: FieldConfig): Double? = DerivedFields.eval(obj, config, today, yearlyRepeat())

    /** 模板开了「每年重复」时的滚动上下文；未开返回 null（存量行为一字不变）。 */
    private fun yearlyRepeat(): YearlyRepeat? =
        if (!template.repeatYearly) null else YearlyRepeat(lunar = parseLunarFlag(str("lunar")))

    /** 英雄区形态：模板身份 → 形态（设计稿 16 张的逐模板标定）。 */
    val heroForm: HeroForm
        get() = when (template.icon) {
            "savings", "shopping_cart", "school", "calendar_month", "subscriptions",
            "fitness_center" -> HeroForm.MONEY3
            "timer_off", "trending_up", "cake", "celebration", "build" -> HeroForm.DAYS
            "flight" -> HeroForm.ROUTE
            "menu_book" -> HeroForm.COVER
            "book" -> HeroForm.NOTE
            "checklist" -> HeroForm.TODO
            "mood" -> HeroForm.MOOD
            "timer" -> HeroForm.TIMER
            // 未知模板：退化为「一个大数字」形态，不空屏（§14.10(3) 第 4 条同义）
            else -> HeroForm.MONEY3
        }

    /** ② 主指标行是否出现：**P2（天数巨字）不出现**（§14.12(4)）。 */
    val showsMetrics: Boolean
        get() = heroForm != HeroForm.DAYS

    fun format(config: FieldConfig, value: String?): String = when {
        value.isNullOrBlank() -> PLACEHOLDER
        config.type in NUMERIC_TYPES -> {
            val d = value.toDoubleOrNull()
            if (d == null) value else fmtNumber(d) + config.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        }
        else -> value
    }

    companion object {
        const val PLACEHOLDER = "—"

        // 日期短格式跟系统语言走（中文「9月24日」/ 英文「Sep 24」）；skeleton 由系统给出，
        // 避免把「MM月dd日」硬编码进英文界面
        private val MD by lazy {
            DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"),
                Locale.getDefault()
            )
        }
        private val NUMERIC_TYPES = setOf(
            FieldType.NUMBER,
            FieldType.CURRENCY,
            FieldType.PERCENT,
            FieldType.PERCENTAGE,
            FieldType.SLIDER,
            FieldType.DURATION,
            FieldType.RATING
        )
    }
}

/**
 * 模板身份色：唯一真源是种子里那串 hex（§5.2），解析不出时回落模块色 —— **不新造色**。
 */
fun identityColor(hex: String?): Color = runCatching { Color(android.graphics.Color.parseColor(hex.orEmpty())) }.getOrNull() ?: ModuleLife

/**
 * 情绪值 → 视觉（emoji + 五档情绪色）**单一真源**：英雄区表情与月热力格色共用，
 * 不许两处各写一张映射表。键**同时认中文与英文**写法（演示数据是中文，
 * 记录填写页落地后英文环境存英文也要能对上）；认不出：表情回默认「☺」，热力格**不猜色**。
 */
object MoodVisuals {
    private data class MoodSpec(val keys: Set<String>, val emoji: String, val color: Color)

    private val specs = listOf(
        MoodSpec(setOf("开心", "happy", "joy", "great"), "☺", LifeMoodHappy),
        MoodSpec(setOf("平静", "calm", "okay", "neutral"), "😐", LifeMoodNormal),
        MoodSpec(setOf("疲惫", "tired", "exhausted"), "😪", LifeMoodUpset),
        MoodSpec(setOf("难过", "sad", "down"), "😔", LifeMoodSad),
        MoodSpec(setOf("焦虑", "anxious", "stressed"), "😰", LifeMoodAngry)
    )

    private fun specOf(mood: String?): MoodSpec? = mood?.trim()?.lowercase()?.let { v -> specs.firstOrNull { v in it.keys } }

    /** 表情记号（emoji 是图形记号、非语言文案，不走字符串资源）。 */
    fun emojiOf(mood: String?): String = specOf(mood)?.emoji ?: "☺"

    /** 情绪色；认不出返回 null（调用方按空值处理，不编色）。 */
    fun colorOf(mood: String?): Color? = specOf(mood)?.color
}

/**
 * 数字格式化（生活模块显示层的**唯一入口**）：千分位分组、小数最多两位。
 *
 * 实现委托给 core 的 [com.palmnote.ui.utils.LifeNumFormat] —— 此前这里是第三套实现
 * （`%,d` / `%,.1f`），与 hero 用的 `#,##0.####` 精度不一致：同一个数在两处显示不同。
 */
fun fmtNumber(v: Double): String = LifeNumFormat.num(BigDecimal.valueOf(v))

/**
 * 金额：`¥1,234`；**负数写 `-¥1,234`**（不是 `¥-1,234`）。
 *
 * 委托给 [com.palmnote.ui.utils.LifeNumFormat.money]，那条「负号在币种之前」的规则
 * 本来就写在它的 KDoc 里，但显示层此前自己拼 `"¥" + 数字`，把规则绕过去了。
 */
fun fmtMoney(v: Double): String = LifeNumFormat.money(BigDecimal.valueOf(v))

/** 专注时长：稿面格式 `2h15m` / `45m`；不足一分钟也按分钟显示。 */
fun fmtDuration(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "${hours}h${rest}m"
        hours > 0 -> "${hours}h"
        else -> "${rest}m"
    }
}

/** 时间戳 → `2026-09-21 14:30`。 */
fun fmtDateTime(ms: Long?): String? = ms?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA))
}

/** 到期 / 起止日期 → `2026-09-24`。 */
fun fmtDate(ms: Long?): String? = ms?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)
}

fun fmtDate(d: LocalDate?): String? = d?.format(DateTimeFormatter.ISO_LOCAL_DATE)

/** P2 锚点短格式（dtl_07/09/10：同年 MM-dd；跨年保留年份避免歧义）。 */
fun fmtDateShort(d: LocalDate?, ref: LocalDate): String? = d?.format(
    if (d.year == ref.year) DateTimeFormatter.ofPattern("MM-dd") else DateTimeFormatter.ISO_LOCAL_DATE
)

/**
 * 打卡连击：从 distinct 打卡天（ISO 日期串）算「连续到今天」的最长段。
 *
 * 语义（Streaks 类 App 标准）：连击**只在「昨天也没打」时才断**——
 * - 今天打了 → 计入，并往下数昨天、前天…直到断；
 * - 今天没打但昨天打了 → 连击仍然有效（今天还没结束，不算断），从昨天数起；
 * - 今天和昨天都没打 → 连击清零。
 *
 * 空输入返回 0（不冒出假连击）。
 */
fun computeCheckInStreak(dayStrings: List<String>): Int =
    // 连击口径单一实现：全部委托 StreakEngine（此前两套并行，口径迟早分叉）
    StreakEngine.compute(dayStrings).current

/**
 * 打卡「最长连胜」：历史里最长的那一段，**不是当前连击**。
 *
 * 与 [computeCheckInStreak] 的差别在断卡之后：30 天连胜昨天中断时，当前连击回到 0/1，
 * 而最长连胜仍是 30。统计页「最长连胜」用本函数——此前它错用了 [computeCheckInStreak]，
 * 导致同一文案在统计页显示当前值、在详情页显示历史最长值（详情见
 * `LifeDetailViewModel` 的 `checkInLongest`，那里直接取 `StreakResult.longest`）。
 */
fun computeCheckInLongest(dayStrings: List<String>): Int =
    StreakEngine.compute(dayStrings).longest

/** 结构区一行（§14.12(5)）—— 由字段契约的 DisplayKind 决定渲染器。 */
sealed interface DetailRowModel {
    /** 键值行（文本 / 数值 / 金额 / 日期 / 派生值 / 区间 / 表格单行）。 */
    data class Kv(val label: String, val value: String?, val emoji: Boolean = false) : DetailRowModel

    /** 真实状态开关（BOOLEAN）。 */
    data class Toggle(val label: String, val checked: Boolean) : DetailRowModel

    /**
     * 色值（COLOR）：色块 + 十六进制原文。
     *
     * 此前 COLOR 与 [Toggle] 共用同一个渲染器，而 `ctx.flag(key)` 要从 `#RRGGBB`
     * 字符串里取布尔值 —— 永远得到 false ⇒ 用户选的颜色在详情页**恒显示为「关」**。
     */
    data class Swatch(val label: String, val hex: String) : DetailRowModel

    /** 链接（URL）。 */
    data class Link(val label: String, val value: String) : DetailRowModel

    /** 评分（RATING）。 */
    data class Stars(val label: String, val score: Int, val max: Int = 5) : DetailRowModel

    /**
     * 进度（PERCENT / showAsProgress）；[form] 决定横向条或圆环。
     *
     * [stepper] 非 null 时，这一行带**就地加减**按钮。此前进度型条目唯一能改数字的路径是
     * 「⋯ → 编辑记录 → 找到那个数字 → 保存」，四步才能推进一格（用户真机反馈）。
     */
    data class Bar(
        val label: String,
        val fraction: Float,
        val value: String,
        val form: ProgressForm = ProgressForm.THICK_CAPSULE,
        val segments: Int = 0,
        val stepper: BarStepper? = null
    ) : DetailRowModel

    /** 勾选清单（CHECKLIST）。 */
    data class CheckList(val label: String, val rows: List<Pair<String, Boolean>>) : DetailRowModel

    /** 详情表的每一条（TABLE → 拆成多行）。[columnTypes] 供布尔列画勾圈。 */
    data class TableRows(
        val label: String,
        val columns: List<String>,
        val rows: List<List<String>>,
        val columnTypes: List<FieldType> = emptyList()
    ) : DetailRowModel

    /**
     * 选项胶囊（SELECT / MULTI_SELECT / TAG）—— **只读**（详情页不给改，§14.12(8)）。
     * [valueRes] 与 [values] 按索引对应；`0` 表示该值直接使用原文。
     */
    data class Chips(
        val label: String,
        val values: List<String>,
        val valueRes: List<Int> = emptyList()
    ) : DetailRowModel

    /** 段落（RICH_TEXT / TEXT 长文）。 */
    data class Paragraph(val label: String, val text: String) : DetailRowModel

    /** 打卡统计行（calendar_month）：近 7 天一致性 + 本月 / 累计（标签在渲染层资源化）。 */
    data class CheckInStats(
        val hits: Int,
        val window: Int,
        val percent: Int,
        val monthCount: Int?,
        val total: Int?
    ) : DetailRowModel

    /** 人物堆叠（PERSON）。 */
    data class Persons(val label: String, val names: List<String>) : DetailRowModel

    /** 媒体缩略（IMAGE / VIDEO / AUDIO / FILE）。 */
    data class Media(val label: String, val paths: List<String>) : DetailRowModel

    /** 分类占比堆叠条（MULTI_SELECT 的只读统计形态）。 */
    data class StackBar(val label: String, val segments: List<StackSegment>) : DetailRowModel

    /** 地图（MAP，非英雄区时的小尺寸回落）。 */
    data class MapMini(val label: String, val pointCount: Int) : DetailRowModel

    /**
     * 月热力网格（§14.12(6)）——⚠️ **同一个网格承载两种完全不同的语义，不许混**：
     * [HeatMode.BINARY]（打卡）＝同色深浅，读作「**有没有**」（二元）；
     * [HeatMode.MOOD]（心情）＝每格取**当天情绪色本身**，读作「**是什么**」（分类）。
     * 若把后者按前者实现，心情会退化成「有 / 无」的深浅格子 —— 这正是本节存在的理由。
     */
    data class Heat(
        val label: String,
        val mode: HeatMode,
        val cells: List<HeatCell>,
        /** 首行留白格数（周一起始）：让 1 号落在正确的星期列（§14.12(6) 共用规格）。 */
        val leadingBlanks: Int
    ) : DetailRowModel

    /** 日记「时间线（按天，带图缩略）」。 */
    data class Timeline(val label: String, val entries: List<TimelineEntry>) : DetailRowModel

    /** 专注「今日会话」：一行一次会话。 */
    data class FocusSessions(val entries: List<FocusSessionEntry>) : DetailRowModel

    /** 专注「本周每日时长」：固定周一至周日七根柱子。 */
    data class WeekBars(val bars: List<FocusWeekBar>) : DetailRowModel
}

/** 堆叠占比条的一段：名称 + 计数；百分比由 UI 计算，避免模型层依赖本地化。 */
data class StackSegment(val name: String, val count: Int)

/** 热力的两种语义（§14.12(6)）。 */
enum class HeatMode { BINARY, MOOD }

/**
 * 热力一格。
 * - [BINARY]：`count > 0` 即有；
 * - [MOOD]：`moodKey` 为当天的情绪值（多色），取不到就不是心情日。
 */
data class HeatCell(val dayOfMonth: Int, val count: Int, val moodKey: String?)

/** 时间线一条（day = `MM-dd`，summary = 已本地化前的**数据片段**由 UI 拼）。 */
data class TimelineEntry(
    val day: String,
    val title: String,
    val weather: String?,
    val mood: String?,
    val photoCount: Int
)

data class FocusSessionEntry(val minutes: Int, val title: String, val time: String)

data class FocusWeekBar(val dayOfWeek: DayOfWeek, val durationMs: Long)

/**
 * ③ 结构区的一组。**一字段一组**（照设计稿）。
 *
 * 标题三选一，优先级：`title`（字段名，普通字段）> `titleRes`（模板专属块，如「本月打卡」）> 空。
 * 设计稿里的「· 类型」「（新增字段）」是标注、不进 UI（见 `LifeDetailViewModel.assemble` 注释）。
 */
data class DetailGroup(
    val rows: List<DetailRowModel>,
    /** 组标题（= 字段名，来自 fieldsConfig 的 label，已是用户语言）。 */
    val title: String = "",
    /** `R.string.*`；0 = 用 [title]。 */
    val titleRes: Int = 0,
    /** 组对应的字段 key（清单就地勾选要用；模板专属块/白名单组为 null）。 */
    val fieldKey: String? = null
)

/** ② 主指标行的一项（`key` 用于把已被指标消费的字段从结构区排除）。 */
/**
 * ② 指标行的一项。
 *
 * `labelRes != 0` 用资源文案（**派生指标**：日均 / 已坚持 / 逾期…这些不是字段，
 * 没有 fieldsConfig 的 label 可借用，且 ViewModel 里拿不到 `stringResource`）；
 * 否则用字段自己的 label（数据）。
 */
data class DetailMetric(
    val key: String,
    val label: String = "",
    val value: String,
    val labelRes: Int = 0,
    /** 值的格式化模板（如 `%1$s 篇`）；0 = 原样显示 [value]。 */
    val formatRes: Int = 0
)

/** 把一组字段装配成结构区的行（**契约驱动**：类型 → DisplayKind → 渲染器）。 */
fun buildRows(ctx: DetailCtx, config: FieldConfig): List<DetailRowModel> {
    val key = config.key
    progressRow(ctx, config)?.let { return listOf(it) }
    return when (config.type) {
        FieldType.BOOLEAN -> listOf(
            DetailRowModel.Toggle(config.label, ctx.flag(key) ?: false)
        )
        FieldType.COLOR -> ctx.str(key)?.let { listOf(DetailRowModel.Swatch(config.label, it)) }
            ?: listOf(DetailRowModel.Kv(config.label, DetailCtx.PLACEHOLDER))
        FieldType.URL -> ctx.str(key)?.let { listOf(DetailRowModel.Link(config.label, it)) }
            ?: listOf(DetailRowModel.Link(config.label, DetailCtx.PLACEHOLDER))
        FieldType.RATING -> listOf(
            DetailRowModel.Stars(config.label, ctx.num(key)?.toInt() ?: 0)
        )
        FieldType.PERCENT, FieldType.PERCENTAGE -> {
            val v = ctx.num(key)
            listOf(DetailRowModel.Bar(config.label, ((v ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f), v?.let { "${fmtNumber(it)}%" } ?: DetailCtx.PLACEHOLDER))
        }
        FieldType.SELECT, FieldType.TAG -> {
            val values = listOfNotNull(ctx.str(key))
            listOf(
                DetailRowModel.Chips(
                    config.label,
                    values,
                    values.map { if (key == "billingCycle") billingCycleRes(it) else 0 }
                )
            )
        }
        FieldType.MULTI_SELECT -> {
            // dtl_12：影响因素等 MULTI_SELECT 用**只读胶囊 chips** 列本条取值；
            // 堆叠占比条是统计页语言，不进详情结构区（§14.12：详情不承担聚合统计）。
            listOf(DetailRowModel.Chips(config.label, ctx.list(key)))
        }
        FieldType.RANGE -> {
            val r = parseRange(ctx.str(key))
            listOf(
                DetailRowModel.Kv(
                    config.label,
                    if (r == null) {
                        DetailCtx.PLACEHOLDER
                    } else {
                        // 同 DATE：区间端点也可能是 ISO 字符串，不能只按毫秒解析
                        listOfNotNull(
                            fmtDate(DateUtils.parseDateValueOrNull(r.start)),
                            fmtDate(DateUtils.parseDateValueOrNull(r.end))
                        ).joinToString(" – ")
                    }
                )
            )
        }
        FieldType.CHECKLIST -> {
            val rows = parseChecklist(ctx.raw(key)).map { it.text to it.done }
            listOf(DetailRowModel.CheckList(config.label, rows))
        }
        FieldType.TABLE -> {
            val model = parseTable(ctx.raw(key))
            // 载荷里还没有列定义（这条记录从没填过表）时，回落到字段配置的 options ——
            // 否则详情页会画一张**既没有表头也没有行**的空表，用户看不出这里该填什么。
            val cols = model.columns.ifEmpty { tableColumnsOf(config) }
            listOf(
                DetailRowModel.TableRows(
                    label = config.label,
                    columns = cols.map { it.label },
                    rows = model.rows,
                    // 列类型必须带过去：BOOLEAN 列要画成勾圈（购物「已买」），
                    // 不带的话用户看到的是字面量 "true" —— 既难读、又点不动。
                    columnTypes = cols.map { it.type }
                )
            )
        }
        FieldType.PERSON -> listOf(DetailRowModel.Persons(config.label, ctx.list(key).ifEmpty { listOfNotNull(ctx.str(key)) }))
        FieldType.IMAGE, FieldType.VIDEO, FieldType.AUDIO, FieldType.FILE ->
            listOf(DetailRowModel.Media(config.label, ctx.list(key).ifEmpty { listOfNotNull(ctx.str(key)) }))
        FieldType.MAP -> {
            val model = parseMap(ctx.raw(key))
            val pointCount = model.route.size + model.track?.pts.orEmpty().size
            listOf(DetailRowModel.MapMini(config.label, pointCount))
        }
        FieldType.RICH_TEXT, FieldType.TEXT -> ctx.str(key)?.let {
            if (it.length > LONG_TEXT) {
                listOf(DetailRowModel.Paragraph(config.label, it))
            } else {
                listOf(DetailRowModel.Kv(config.label, it))
            }
        } ?: listOf(DetailRowModel.Paragraph(config.label, ""))
        FieldType.FORMULA, FieldType.REMAINING, FieldType.ELAPSED -> {
            val d = ctx.derived(config) ?: config.defaultValue.toDoubleOrNull()
            listOf(
                DetailRowModel.Kv(
                    config.label,
                    d?.let { fmtNumber(it) + config.unit.takeIf { u -> u.isNotBlank() }?.let { u -> " $u" }.orEmpty() }
                        ?: DetailCtx.PLACEHOLDER
                )
            )
        }
        // STREAK 算不出「本条记录」的值：它要的是该模板跨记录的打卡历史，
        // 所以 DerivedFields.eval 没有也不该有 STREAK 分支。此前它被并进上面那一支，
        // eval 返回 null ⇒ 这一行**永远显示「—」**。改为取详情聚合层的打卡连击，
        // 与页面顶部的「连续 N 天」同源。
        FieldType.STREAK -> listOf(
            DetailRowModel.Kv(
                config.label,
                ctx.aggregates.checkInLongest?.let {
                    fmtNumber(it.toDouble()) +
                        config.unit.takeIf { u -> u.isNotBlank() }?.let { u -> " $u" }.orEmpty()
                } ?: DetailCtx.PLACEHOLDER
            )
        )
        FieldType.NUMBER, FieldType.CURRENCY, FieldType.DURATION, FieldType.SLIDER ->
            listOf(DetailRowModel.Kv(config.label, ctx.format(config, ctx.str(key))))
        FieldType.DATE -> listOf(
            // 必须走 ctx.date()（内部是 DateUtils.parseDateValueOrNull：毫秒与 "yyyy-MM-dd" 都认）。
            // 这里原本是 str(key).toLongOrNull()，**只认毫秒**；而记录表单写的正是 ISO 字符串
            // （DatePillRow → LocalDate.toString()），于是表单里选的日期在详情页显示为空白。
            // 编辑页没坏只是巧合：decodeValue 有个 `?: raw` 兜底，详情页没有。
            DetailRowModel.Kv(config.label, ctx.date(key)?.let { fmtDate(it) } ?: DetailCtx.PLACEHOLDER)
        )
        FieldType.TIME -> listOf(DetailRowModel.Kv(config.label, ctx.str(key) ?: DetailCtx.PLACEHOLDER))
        FieldType.DATETIME -> listOf(
            // 同上：改用 ctx.dateMs()，至少能解析毫秒与纯日期两种存量写法。
            // DATETIME 目前没有专用控件（走 ComplexFieldCard 的 else 落成普通文本框），
            // 用户手输的非标准串仍解析不出 —— 那属于「控件缺失」，不是解析口径问题。
            DetailRowModel.Kv(config.label, ctx.dateMs(key)?.let { fmtDateTime(it) } ?: DetailCtx.PLACEHOLDER)
        )
        FieldType.SHORT_TEXT, FieldType.EMAIL, FieldType.PHONE, FieldType.LOCATION ->
            listOf(DetailRowModel.Kv(config.label, ctx.str(key) ?: DetailCtx.PLACEHOLDER))
    }
}

private fun progressRow(ctx: DetailCtx, config: FieldConfig): DetailRowModel.Bar? {
    if (!FieldContracts.of(config.type).progressCapable) return null
    val isPercent = config.type == FieldType.PERCENT || config.type == FieldType.PERCENTAGE
    if (!config.showAsProgress && !isPercent) return null
    val form = resolveProgressForm(config)
    val segments = progressSegments(form, ctx, config)
    return if (isPercent) {
        percentProgressRow(ctx, config, form, segments)
    } else {
        progressValueRow(ctx, config, form, segments)
    }
}

private fun percentProgressRow(
    ctx: DetailCtx,
    config: FieldConfig,
    form: ProgressForm,
    segments: Int
): DetailRowModel.Bar {
    val value = ctx.num(config.key)
    return DetailRowModel.Bar(
        label = config.label,
        fraction = ((value ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
        value = value?.let { "${fmtNumber(it)}%" } ?: DetailCtx.PLACEHOLDER,
        form = form,
        segments = segments,
        // 百分比进度按「满量程 100」算步长 → 没给 step 时每次 5%。
        stepper = value?.let {
            BarStepper(config.key, progressStepOf(config, 100.0), it, config.min, config.max)
        }
    )
}

private fun progressValueRow(
    ctx: DetailCtx,
    config: FieldConfig,
    form: ProgressForm,
    segments: Int
): DetailRowModel.Bar? {
    val progress = ctx.progressOf(config) ?: return null
    // 带分母的进度：标签按设计稿拼成「已订 ¥1,800 / ¥4,000」（金额型带 ¥），
    // 值（右侧）给百分比 —— dtl_04 的 bar 行两个量都要，缺一个就不是那一行。
    val hasTarget = config.progressTargetKey.isNotBlank() || config.max != null
    // 金额型（CURRENCY）用汇号写法，和设计稿 dtl_04「已订 ¥1,800 / ¥4,000」一致；
    // 非金额仍走既有 `ctx.format`（保留各自的单位）。
    fun amountText(key: String): String? = ctx.num(key)?.let { v ->
        if (config.type == FieldType.CURRENCY) fmtMoney(v) else ctx.format(config, ctx.str(key))
    }
    val label = if (hasTarget && config.type != FieldType.CHECKLIST) {
        val cur = amountText(config.key) ?: ctx.format(config, ctx.str(config.key))
        val total = config.progressTargetKey.takeIf { it.isNotBlank() }?.let { targetKey ->
            amountText(targetKey)
        } ?: config.max?.let { fmtNumber(it) }
        if (total != null) "${config.label} $cur / $total" else config.label
    } else {
        config.label
    }
    return DetailRowModel.Bar(
        label = label,
        fraction = progress.fraction,
        value = if (config.type == FieldType.CHECKLIST) {
            "${fmtNumber(progress.current ?: 0.0)} / ${fmtNumber(progress.total ?: 0.0)}"
        } else if (hasTarget) {
            "${(progress.fraction * 100).roundToInt()}%"
        } else {
            ctx.format(config, ctx.str(config.key))
        },
        form = form,
        segments = segments,
        // CHECKLIST 的进度靠勾选推进，不给数值步进器。
        stepper = progress.current?.takeIf { config.type != FieldType.CHECKLIST }?.let {
            BarStepper(config.key, progressStepOf(config, progress.total), it, config.min, config.max)
        }
    )
}

/** 进度行的就地加减：[fieldKey] 是要改的字段，[step] 是每次步长，[current] 是当前值。 */
data class BarStepper(
    val fieldKey: String,
    val step: Double,
    val current: Double,
    val min: Double?,
    val max: Double?
)

private fun progressSegments(form: ProgressForm, ctx: DetailCtx, config: FieldConfig): Int {
    val target = config.progressTargetKey.takeIf { it.isNotBlank() }?.let { ctx.num(it) }
        ?: config.max
        ?: ctx.progressOf(config)?.total
    return progressSegmentsFor(form, target)
}

private fun billingCycleRes(value: String): Int = when (value) {
    "monthly" -> R.string.life_detail_cycle_monthly
    "quarterly" -> R.string.life_detail_cycle_quarterly
    "yearly" -> R.string.life_detail_cycle_yearly
    else -> 0
}

private const val LONG_TEXT = 40

// ============================================================
// 模板专属结构（§14.11 #11/#12/#13）
// ============================================================

/**
 * ③ 结构区里**不属于任何字段**的那几块：打卡 / 心情的**月热力**、日记的**时间线**。
 *
 * ⚠️ **只做有真实数据源的**。设计稿里另外三种行（`bars` 立柱 / `stack` 堆叠占比 / `spark` 梳齿波形）
 * **不在详情页画**，理由要分清（2026-09-22 已纠正旧判断「算不出」）：
 * - 「周报月报」已于 **v1.27 退役**（报告改由**统计页**承担）：它的「分布占比」与「每日条数」
 *   现在就是统计页的 `categoryShare` / `heatWeeks` —— **详情页不再有该模板**；
 * - 详情页内的 `bars` / `stack` 属**未实现**（数据其实算得出：`getDayCountsBetweenDemoAware`
 *   与 `getDayCategoryCountsBetweenDemoAware` 直出真值），按「宁可缺一格，不可造假」**不画占位**。
 */
fun templateExtraGroups(ctx: DetailCtx, dayRows: List<LifeItemDao.TemplateDayRow>): List<DetailGroup> {
    val byDay = dayRows.groupBy { it.day }
    return when (ctx.template.icon) {
        "calendar_month" -> listOfNotNull(checkInStatsGroup(ctx)) + listOf(
            heatGroup(ctx, byDay, HeatMode.BINARY, R.string.life_detail_heat_checkin)
        )
        "mood" -> listOf(heatGroup(ctx, byDay, HeatMode.MOOD, R.string.life_detail_heat_mood))
        "book" -> listOfNotNull(timelineGroup(dayRows))
        "timer" -> listOfNotNull(
            focusSessionsGroup(ctx.aggregates.focusSessions),
            focusWeekGroup(ctx.aggregates.focusWeekBars)
        )
        else -> emptyList()
    }
}

/** 打卡统计组：一致性 + 本月 / 累计（checkInMonthCount / checkInTotal 此前无渲染消费方）。 */
private fun checkInStatsGroup(ctx: DetailCtx): DetailGroup? {
    val consistency = ctx.aggregates.checkInConsistency ?: return null
    return DetailGroup(
        rows = listOf(
            DetailRowModel.CheckInStats(
                hits = consistency.hits,
                window = consistency.window,
                percent = consistency.percent,
                monthCount = ctx.aggregates.checkInMonthCount,
                total = ctx.aggregates.checkInTotal
            )
        ),
        titleRes = R.string.life_detail_stat_checkin
    )
}

/** 月热力网格：7 列（周一起始）、最多 35 格（§14.12(6)）。空月**照常画**（空值占位，不留白）。 */
private fun heatGroup(
    ctx: DetailCtx,
    byDay: Map<String, List<LifeItemDao.TemplateDayRow>>,
    mode: HeatMode,
    titleRes: Int
): DetailGroup {
    val first = ctx.today.withDayOfMonth(1)
    val cells = (1..first.lengthOfMonth()).map { dom ->
        val key = first.withDayOfMonth(dom).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val rows = byDay[key].orEmpty()
        val mood = if (mode == HeatMode.MOOD) rows.lastOrNull()?.let { moodOf(it.fieldsData) } else null
        HeatCell(dayOfMonth = dom, count = rows.size, moodKey = mood)
    }
    return DetailGroup(
        rows = listOf(
            DetailRowModel.Heat(
                label = "",
                mode = mode,
                cells = cells,
                // DayOfWeek：MONDAY=1 … SUNDAY=7；周一起始 ⟹ 前导空位数 = value - 1
                leadingBlanks = first.dayOfWeek.value - 1
            )
        ),
        titleRes = titleRes
    )
}

/** 日记时间线：最近 5 天（按天倒序）。无记录则整块不出现（时间线为空没有意义）。 */
private fun timelineGroup(dayRows: List<LifeItemDao.TemplateDayRow>): DetailGroup? {
    val entries = dayRows.groupBy { it.day }.entries.sortedBy { it.key }.takeLast(5).reversed().map { (day, rows) ->
        val objects = rows.map { parseObj(it.fieldsData) }
        TimelineEntry(
            day = day.takeLast(5),
            title = rows.last().title,
            weather = objects.mapNotNull { it.stringOrNull("weather") }.lastOrNull(),
            mood = objects.mapNotNull { it.stringOrNull("mood") }.lastOrNull(),
            photoCount = objects.sumOf { it.photoCount() }
        )
    }
    if (entries.isEmpty()) return null
    return DetailGroup(
        rows = listOf(DetailRowModel.Timeline(label = "", entries = entries)),
        titleRes = R.string.life_habit_filter_timeline
    )
}

private fun focusSessionsGroup(entries: List<FocusSessionEntry>): DetailGroup? {
    if (entries.isEmpty()) return null
    return DetailGroup(
        rows = listOf(DetailRowModel.FocusSessions(entries)),
        titleRes = R.string.life_detail_focus_sessions
    )
}

private fun focusWeekGroup(bars: List<FocusWeekBar>): DetailGroup? {
    if (bars.isEmpty()) return null
    return DetailGroup(
        rows = listOf(DetailRowModel.WeekBars(bars)),
        titleRes = R.string.life_detail_focus_week
    )
}

private val JSON_PLAIN = Json { ignoreUnknownKeys = true }

private fun parseObj(raw: String): JsonObject = runCatching { JSON_PLAIN.decodeFromString<JsonObject>(raw) }
    .getOrNull()
    ?: JsonObject(emptyMap())

private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun moodOf(fieldsData: String): String? = parseObj(fieldsData).stringOrNull("mood")

/** 卡片摘要的呈现类型（与详情页英雄区共用同一套口径）。 */
