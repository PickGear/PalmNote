package com.palmnote.ui.life

import androidx.compose.ui.graphics.Color
import com.palmnote.app.R
import com.palmnote.data.db.dao.LifeItemDao
import com.palmnote.data.db.entity.LifeItem
import com.palmnote.data.db.entity.LifeTemplate
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldGroup
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.model.ProgressValue
import com.palmnote.domain.model.parseChecklist
import com.palmnote.domain.model.parseRange
import com.palmnote.domain.model.parseTable
import com.palmnote.domain.model.compoundRawOf
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val accent: Color
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
        val cur = num(c.key) ?: return null
        val target = c.progressTargetKey.takeIf { it.isNotBlank() }?.let { num(it) }
            ?: c.max
            ?: return null
        if (target <= 0.0) return null
        return ProgressValue(cur, target, (cur / target).toFloat().coerceIn(0f, 1f))
    }

    /** 派生字段求值（REMAINING / ELAPSED / FORMULA）；算不出返回 null。 */
    fun derived(config: FieldConfig): Double? = DerivedFields.eval(obj, config)

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
        private val MD = DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"),
            Locale.getDefault()
        )
        private val NUMERIC_TYPES = setOf(
            FieldType.NUMBER, FieldType.CURRENCY, FieldType.PERCENT, FieldType.PERCENTAGE,
            FieldType.SLIDER, FieldType.DURATION, FieldType.RATING
        )
    }
}

/**
 * 模板身份色：唯一真源是种子里那串 hex（§5.2），解析不出时回落模块色 —— **不新造色**。
 */
fun identityColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex.orEmpty())) }.getOrNull() ?: ModuleLife

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

    private fun specOf(mood: String?): MoodSpec? =
        mood?.trim()?.lowercase()?.let { v -> specs.firstOrNull { v in it.keys } }

    /** 表情记号（emoji 是图形记号、非语言文案，不走字符串资源）。 */
    fun emojiOf(mood: String?): String = specOf(mood)?.emoji ?: "☺"

    /** 情绪色；认不出返回 null（调用方按空值处理，不编色）。 */
    fun colorOf(mood: String?): Color? = specOf(mood)?.color
}

/** 数字格式化：整数不带小数，小数最多一位。 */fun fmtNumber(v: Double): String =
    if (v == v.toLong().toDouble()) String.format(Locale.CHINA, "%,d", v.toLong())
    else String.format(Locale.CHINA, "%,.1f", v)

/** 金额：`¥1,234`（不以 0 冒充，取不到就不渲染）。 */
fun fmtMoney(v: Double): String = "¥" + fmtNumber(v)

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
fun computeCheckInStreak(dayStrings: List<String>): Int {
    val days = dayStrings.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    if (days.isEmpty()) return 0
    val today = LocalDate.now()
    // 从今天或昨天起算（今天没打不算断，从昨天续）
    var cursor = if (days.contains(today)) today else today.minusDays(1)
    if (!days.contains(cursor)) return 0
    var streak = 0
    while (days.contains(cursor)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** 结构区一行（§14.12(5)）—— 由字段契约的 DisplayKind 决定渲染器。 */
sealed interface DetailRowModel {
    /** 键值行（文本 / 数值 / 金额 / 日期 / 派生值 / 区间 / 表格单行）。 */
    data class Kv(val label: String, val value: String?, val emoji: Boolean = false) : DetailRowModel

    /** 真实状态开关（BOOLEAN / COLOR）。 */
    data class Toggle(val label: String, val checked: Boolean) : DetailRowModel

    /** 链接（URL）。 */
    data class Link(val label: String, val value: String) : DetailRowModel

    /** 评分（RATING）。 */
    data class Stars(val label: String, val score: Int, val max: Int = 5) : DetailRowModel

    /** 比例条（PERCENT）。 */
    data class Bar(val label: String, val fraction: Float, val value: String) : DetailRowModel

    /** 勾选清单（CHECKLIST）。 */
    data class CheckList(val label: String, val rows: List<Pair<String, Boolean>>) : DetailRowModel

    /** 详情表的每一条（TABLE → 拆成多行）。 */
    data class TableRows(val label: String, val columns: List<String>, val rows: List<List<String>>) : DetailRowModel

    /** 选项胶囊（SELECT / MULTI_SELECT / TAG）—— **只读**（详情页不给改，§14.12(8)）。 */
    data class Chips(val label: String, val values: List<String>) : DetailRowModel

    /** 段落（RICH_TEXT / TEXT 长文）。 */
    data class Paragraph(val label: String, val text: String) : DetailRowModel

    /** 人物堆叠（PERSON）。 */
    data class Persons(val label: String, val names: List<String>) : DetailRowModel

    /** 媒体缩略（IMAGE / VIDEO / AUDIO / FILE）。 */
    data class Media(val label: String, val paths: List<String>) : DetailRowModel

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
}

/** 热力的两种语义（§14.12(6)）。 */
enum class HeatMode { BINARY, MOOD }

/**
 * 热力一格。
 * - [BINARY]：`count > 0` 即有；
 * - [MOOD]：`moodKey` 为当天的情绪值（多色），取不到就不是心情日。
 */
data class HeatCell(val dayOfMonth: Int, val count: Int, val moodKey: String?)

/** 时间线一条（day = `MM-dd`，summary = 已本地化前的**数据片段**由 UI 拼）。 */
data class TimelineEntry(val day: String, val title: String, val weather: String?, val mood: String?)

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
    val titleRes: Int = 0
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
    val labelRes: Int = 0
)

/** 把一组字段装配成结构区的行（**契约驱动**：类型 → DisplayKind → 渲染器）。 */
fun buildRows(ctx: DetailCtx, config: FieldConfig): List<DetailRowModel> {
    val key = config.key
    return when (config.type) {
        FieldType.BOOLEAN -> listOf(
            DetailRowModel.Toggle(config.label, ctx.flag(key) ?: false)
        )
        FieldType.COLOR -> listOf(DetailRowModel.Toggle(config.label, ctx.flag(key) ?: false))
        FieldType.URL -> ctx.str(key)?.let { listOf(DetailRowModel.Link(config.label, it)) }
            ?: listOf(DetailRowModel.Link(config.label, DetailCtx.PLACEHOLDER))
        FieldType.RATING -> listOf(
            DetailRowModel.Stars(config.label, ctx.num(key)?.toInt() ?: 0)
        )
        FieldType.PERCENT, FieldType.PERCENTAGE -> {
            val v = ctx.num(key)
            listOf(DetailRowModel.Bar(config.label, ((v ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f), v?.let { "${fmtNumber(it)}%" } ?: DetailCtx.PLACEHOLDER))
        }
        FieldType.SELECT, FieldType.TAG -> listOf(
            DetailRowModel.Chips(config.label, listOfNotNull(ctx.str(key)))
        )
        FieldType.MULTI_SELECT -> listOf(DetailRowModel.Chips(config.label, ctx.list(key)))
        FieldType.RANGE -> {
            val r = parseRange(ctx.str(key))
            listOf(
                DetailRowModel.Kv(
                    config.label,
                    if (r == null) DetailCtx.PLACEHOLDER
                    else listOfNotNull(fmtDate(r.start.toLongOrNull()), fmtDate(r.end.toLongOrNull())).joinToString(" – ")
                )
            )
        }
        FieldType.CHECKLIST -> {
            val rows = parseChecklist(ctx.raw(key)).map { it.text to it.done }
            listOf(DetailRowModel.CheckList(config.label, rows))
        }
        FieldType.TABLE -> {
            val model = parseTable(ctx.raw(key))
            listOf(
                DetailRowModel.TableRows(
                    label = config.label,
                    columns = model.columns.map { it.label },
                    rows = model.rows
                )
            )
        }
        FieldType.PERSON -> listOf(DetailRowModel.Persons(config.label, ctx.list(key).ifEmpty { listOfNotNull(ctx.str(key)) }))
        FieldType.IMAGE, FieldType.VIDEO, FieldType.AUDIO, FieldType.FILE ->
            listOf(DetailRowModel.Media(config.label, ctx.list(key).ifEmpty { listOfNotNull(ctx.str(key)) }))
        FieldType.MAP -> listOf(DetailRowModel.MapMini(config.label, ctx.list(key).size))
        FieldType.RICH_TEXT, FieldType.TEXT -> ctx.str(key)?.let {
            if (it.length > LONG_TEXT) listOf(DetailRowModel.Paragraph(config.label, it))
            else listOf(DetailRowModel.Kv(config.label, it))
        } ?: listOf(DetailRowModel.Paragraph(config.label, ""))
        FieldType.FORMULA, FieldType.REMAINING, FieldType.ELAPSED, FieldType.STREAK -> {
            val d = ctx.derived(config) ?: config.defaultValue.toDoubleOrNull()
            listOf(
                DetailRowModel.Kv(
                    config.label,
                    d?.let { fmtNumber(it) + config.unit.takeIf { u -> u.isNotBlank() }?.let { u -> " $u" }.orEmpty() }
                        ?: DetailCtx.PLACEHOLDER
                )
            )
        }
        FieldType.NUMBER, FieldType.CURRENCY, FieldType.DURATION, FieldType.SLIDER ->
            listOf(DetailRowModel.Kv(config.label, ctx.format(config, ctx.str(key))))
        FieldType.DATE -> listOf(DetailRowModel.Kv(config.label, ctx.str(key)?.let { fmtDate(it.toLongOrNull()) } ?: DetailCtx.PLACEHOLDER))
        FieldType.TIME -> listOf(DetailRowModel.Kv(config.label, ctx.str(key) ?: DetailCtx.PLACEHOLDER))
        FieldType.DATETIME -> listOf(DetailRowModel.Kv(config.label, ctx.str(key)?.let { fmtDateTime(it.toLongOrNull()) } ?: DetailCtx.PLACEHOLDER))
        FieldType.SHORT_TEXT, FieldType.EMAIL, FieldType.PHONE, FieldType.LOCATION ->
            listOf(DetailRowModel.Kv(config.label, ctx.str(key) ?: DetailCtx.PLACEHOLDER))
    }
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
        "calendar_month" -> listOf(heatGroup(ctx, byDay, HeatMode.BINARY, R.string.life_detail_heat_checkin))
        "mood" -> listOf(heatGroup(ctx, byDay, HeatMode.MOOD, R.string.life_detail_heat_mood))
        "book" -> listOfNotNull(timelineGroup(dayRows))
        else -> emptyList()
    }
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
    val entries = dayRows.takeLast(5).reversed().map { row ->
        val obj = parseObj(row.fieldsData)
        TimelineEntry(
            day = row.day.takeLast(5),
            title = row.title,
            weather = obj.stringOrNull("weather"),
            mood = obj.stringOrNull("mood")
        )
    }
    if (entries.isEmpty()) return null
    return DetailGroup(
        rows = listOf(DetailRowModel.Timeline(label = "", entries = entries)),
        titleRes = R.string.life_habit_filter_timeline
    )
}

private val JSON_PLAIN = Json { ignoreUnknownKeys = true }

private fun parseObj(raw: String): JsonObject =
    runCatching { JSON_PLAIN.decodeFromString<JsonObject>(raw) }.getOrNull() ?: JsonObject(emptyMap())

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun moodOf(fieldsData: String): String? = parseObj(fieldsData).stringOrNull("mood")

/** 卡片摘要的呈现类型（与详情页英雄区共用同一套口径）。 */
enum class BoardCardKind { NUMBER, PROGRESS, TEXT, TODO }

/**
 * 卡片内容摘要：由**同一个** [DetailCtx] 推导，保证卡片与详情页口径一致。
 * 只放**数据**（数值 / 天数 / 计数），**不放 UI 文案** —— 文案由卡片在 Composable 里成型，
 * 这样中英资源才不会因为硬编码而失配。
 */
data class CardSummary(
    val kind: BoardCardKind,
    /** 主数值（金额 / 个数 / 文本标题）。 */
    val value: String,
    /** 次要说明（字段名 / 模板名）。 */
    val label: String,
    val fraction: Float?,
    val warning: Boolean,
    /** 天数型：正 = 还有，负 = 已经（卡片按方向词 + 单位自行成型）。 */
    val days: Long? = null,
    val doneCount: Int = 0,
    val totalCount: Int = 0
)

