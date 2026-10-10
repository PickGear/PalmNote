package com.palmnote.ui.life

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.palmnote.data.db.entity.BuiltinFieldText
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.util.DateUtils
import com.palmnote.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.palmnote.ui.theme.Spacing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/**
 * 记录卡片：**首页列表与模板编辑器的「卡片」预览共用这一份渲染**。
 *
 * ## 为什么要有这个文件（这是一个被修掉的死配置）
 *
 * `FieldConfig.showInCard`（编辑器里的「卡片字段」开关）此前**没有任何渲染方**：
 * `LifeDataSeeder` 里配了 40+ 处、字段库可以改、模板管理页还统计它（`cardFieldCount`），
 * 但首页真实卡片 `FlowCard` 只画「图标 + 标题 + 模板名」——
 * 于是这个开关**开了等于没开**，而模板编辑器的「卡片」预览只能自己另编一套
 * （有进度字段时画进度卡、没有时退化成一句「N 个字段」），与真实卡片**没有任何对应关系**。
 *
 * 这里把「卡片」收成唯一实现，于是三件事同时成立：
 * 1. `showInCard` **真的有反馈**（用户勾了就看得见）；
 * 2. 预览**等于真实**（同一份代码渲染，不会再漂移）；
 * 3. 不再存在第二套卡片外观。
 *
 * 卡面只放**一行**字段值（最多 3 项，超出折成「+N」）：卡片是列表项，
 * 高度要稳、要能一眼扫过；字段的完整呈现归详情页（那里有结构区分组）。
 */

/** 与各 ViewModel 里的私有 JSON 同口径（忽略未知键，老数据不缺字段）。 */
internal val CARD_JSON = Json { ignoreUnknownKeys = true }

/**
 * 卡面上一格：标签 + 已格式化的值。
 *
 * [days] 非空表示这是一个「距某天的天数」（`ELAPSED`），由 UI 层按正负与本地化措辞呈现
 * （「还有 12 天 / 已过 3 天 / 就是今天」）——因为「剩余天数 -3 天」这种读法本身是矛盾的：
 * 标签已经说了方向，值再带符号就会互相打脸。方向词交给值来说，标签就不参与这一格。
 */
internal data class CardFieldValue(
    val label: String,
    val value: String,
    val days: Long? = null
)

/** 卡面最多显示 [max] 项：返回（要显示的前 N 项，被折叠的条数）。 */
internal fun visibleCardFields(fields: List<CardFieldValue>, max: Int = 3): Pair<List<CardFieldValue>, Int> =
    fields.take(max) to (fields.size - max).coerceAtLeast(0)

/**
 * 「上次记录」的相对天数：**null = 从未记录；0 = 今天；n = n 天前**。
 *
 * 这是记录型 app 的核心读数（「上次发生：N 天前」）：
 * 它回答的不是"这条记录是什么"，而是「**我该不该再记一笔**」——
 * 对喝了几杯奶茶、多久没理发、上次体检是什么时候这类事，这才是用户真正要的信息。
 * 时间源取模板下最近一条记录的 `updatedAt`（调用方聚合），因此不需要新查询。
 */
internal fun relativeDaysSince(lastMs: Long?, today: LocalDate): Long? {
    if (lastMs == null) return null
    val last = DateUtils.millisToLocalDate(lastMs)
    return java.time.temporal.ChronoUnit.DAYS.between(last, today).coerceAtLeast(0)
}

/**
 * 从**存储态**（`fieldsConfig` + `fieldsData` 两段 JSON 字符串）抽出卡面字段。
 * 解析失败一律返回空表 —— 卡片宁可少一行，也不能因为一条脏数据崩掉整张列表。
 *
 * 不做语言处理（纯函数形态，测试直接用）；生产渲染走 [localizedCardFieldValues]。
 */
internal fun cardFieldValues(
    fieldsConfig: String,
    fieldsData: String,
    today: LocalDate = LocalDate.now(),
    repeatYearly: Boolean = false
): List<CardFieldValue> {
    val configs = runCatching { CARD_JSON.decodeFromString<List<FieldConfig>>(fieldsConfig) }
        .getOrDefault(emptyList())
    if (configs.isEmpty()) return emptyList()
    val obj = runCatching { CARD_JSON.parseToJsonElement(fieldsData).jsonObject }.getOrNull()
        ?: return emptyList()
    return cardFieldValues(configs, obj, today, yearlyRepeatOf(obj, repeatYearly))
}

/**
 * 生产渲染用的入口：在 [cardFieldValues] 之上把内置模板的字段名 / 单位翻成当前语言
 * （`BuiltinFieldText`）—— 字段名是**建库时的中文**，不翻的话英文界面下卡面全是中文。
 */
internal fun localizedCardFieldValues(
    context: Context,
    fieldsConfig: String,
    fieldsData: String,
    today: LocalDate = LocalDate.now(),
    repeatYearly: Boolean = false
): List<CardFieldValue> {
    val configs = runCatching { CARD_JSON.decodeFromString<List<FieldConfig>>(fieldsConfig) }
        .getOrDefault(emptyList())
    if (configs.isEmpty()) return emptyList()
    val obj = runCatching { CARD_JSON.parseToJsonElement(fieldsData).jsonObject }.getOrNull()
        ?: return emptyList()
    return cardFieldValues(
        BuiltinFieldText.localizeConfigs(context, configs),
        obj,
        today,
        yearlyRepeatOf(obj, repeatYearly)
    )
}

/** 模板开了「每年重复」时的滚动上下文；农历与否取生日模板的 `lunar` 字段（与提醒同判据）。 */
private fun yearlyRepeatOf(obj: JsonObject, repeatYearly: Boolean): YearlyRepeat? =
    if (!repeatYearly) null else YearlyRepeat(lunar = parseLunarFlag((obj["lunar"] as? JsonPrimitive)?.contentOrNull))

/** 纯函数核心（可单测）：只取已启用 + `showInCard` + 能格式化的字段。 */
internal fun cardFieldValues(
    configs: List<FieldConfig>,
    obj: JsonObject,
    today: LocalDate,
    yearly: YearlyRepeat? = null
): List<CardFieldValue> = configs.asSequence()
    .filter { it.showInCard && !it.disabled }
    .mapNotNull { cfg ->
        val text = cardValueText(cfg, configs, obj, today, yearly) ?: return@mapNotNull null
        CardFieldValue(
            label = cfg.label.ifBlank { cfg.key },
            value = text,
            // 只有「距某天的天数」需要 UI 层再按方向措辞一次（其余值已是终稿）
            days = if (cfg.type == FieldType.ELAPSED) {
                DerivedFields.eval(obj, cfg, today, yearly)?.toLong()
            } else {
                null
            }
        )
    }
    .toList()

/**
 * 单个字段的卡面值；**表达不了的返回 null**（不是显示 0 或空串）。
 *
 * 派生字段（还差 / 已经过 / 公式）在 `fieldsData` 里**没有存值**，故走 [DerivedFields] 现算——
 * 这正好让「存钱还差多少」这类字段在卡片上活得起来（seeder 里它们的 `showInCard` 就是 true）。
 * 算不出同样返回 null：宁可不出，也不出 0。
 */
internal fun cardValueText(
    cfg: FieldConfig,
    configs: List<FieldConfig>,
    obj: JsonObject,
    today: LocalDate,
    yearly: YearlyRepeat? = null
): String? = when (cfg.type) {
    FieldType.CURRENCY -> rawOf(cfg, obj).toDoubleOrNull()?.let { fmtMoney(it) }
    FieldType.NUMBER, FieldType.DURATION, FieldType.SLIDER, FieldType.PERCENTAGE, FieldType.PERCENT,
    FieldType.RATING -> rawOf(cfg, obj).toDoubleOrNull()?.let { fmtNumber(it) + unitSuffix(cfg.unit) }
    // 布尔在卡面上只表达「有」：true → ✓，false/空 → 不出（免得每张卡都挂一排「否」）
    FieldType.BOOLEAN -> if (rawOf(cfg, obj).equals("true", ignoreCase = true)) "✓" else null
    FieldType.DATE, FieldType.DATETIME -> dateText(rawOf(cfg, obj), today)
    FieldType.TEXT, FieldType.SHORT_TEXT, FieldType.RICH_TEXT, FieldType.URL, FieldType.EMAIL,
    FieldType.PHONE, FieldType.LOCATION, FieldType.SELECT, FieldType.TAG, FieldType.MULTI_SELECT,
    FieldType.PERSON -> rawOf(cfg, obj).trim().takeIf { it.isNotBlank() }?.let { short(it) }
    FieldType.ELAPSED, FieldType.REMAINING, FieldType.FORMULA -> derivedText(cfg, configs, obj, today, yearly)
    // CHECKLIST / TABLE / 媒体 / 地图 / RANGE / COLOR / STREAK：一行卡面表达不了，留给详情页
    else -> null
}

private fun rawOf(cfg: FieldConfig, obj: JsonObject): String =
    (obj[cfg.key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun unitSuffix(unit: String): String = if (unit.isBlank()) "" else " $unit"

/** 文本类截断：卡面只有一行，超过 [SHORT_MAX] 字打省略号（避免长备注把卡片撑歪）。 */
private const val SHORT_MAX = 18

private fun short(raw: String): String =
    if (raw.length <= SHORT_MAX) raw else raw.take(SHORT_MAX) + "…"

/** 日期走全 app 一致的短格式（同年 MM-dd，跨年保留年份）；解析不出就不出，不猜。 */
private fun dateText(raw: String, today: LocalDate): String? =
    DateUtils.parseDateValueOrNull(raw)?.let { fmtDateShort(DateUtils.millisToLocalDate(it), today) }

/**
 * 派生值：现算，并按**被引用字段**的语义格式化（还差的单位跟着「目标金额」走，而不是跟着自己）。
 * 差值类字段自己不存 unit，所以这一层「继承引用字段的单位/币种」是必要的。
 */
private fun derivedText(
    cfg: FieldConfig,
    configs: List<FieldConfig>,
    obj: JsonObject,
    today: LocalDate,
    yearly: YearlyRepeat? = null
): String? {
    val v = DerivedFields.eval(obj, cfg, today, yearly) ?: return null
    if (cfg.type == FieldType.ELAPSED) return fmtNumber(v) + " 天"
    val ref = configs.firstOrNull { it.key == cfg.options.firstOrNull() }
    return when (ref?.type) {
        FieldType.CURRENCY -> fmtMoney(v)
        else -> fmtNumber(v) + unitSuffix(cfg.unit.ifBlank { ref?.unit.orEmpty() })
    }
}

/**
 * 卡面进度分母：取 `showAsProgress` 字段的当前值 ÷ （`progressTargetKey` 指向字段的值，缺省 `max`）。
 * 与详情页 hero 同口径；缺任一端就返回 null（不出空条）。
 */
internal fun cardProgressFraction(configs: List<FieldConfig>, obj: JsonObject): Float? {
    val cfg = configs.firstOrNull { it.showAsProgress && !it.disabled } ?: return null
    val current = (obj[cfg.key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return null
    val target = configs.firstOrNull { it.key == cfg.progressTargetKey }
        ?.let { (obj[it.key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() }
        ?: cfg.max
        ?: return null
    return if (target <= 0.0) null else (current / target).toFloat().coerceIn(0f, 1f)
}

/** 从存储态取进度分母（列表用；解析失败返回 null）。 */
internal fun cardProgressFraction(fieldsConfig: String, fieldsData: String): Float? {
    val configs = runCatching { CARD_JSON.decodeFromString<List<FieldConfig>>(fieldsConfig) }
        .getOrDefault(emptyList())
    if (configs.isEmpty()) return null
    val obj = runCatching { CARD_JSON.parseToJsonElement(fieldsData).jsonObject }.getOrNull() ?: return null
    return cardProgressFraction(configs, obj)
}

/** 卡面字段一行：最多 3 项 + 「+N」。 */
@Composable
private fun CardFieldsLine(fields: List<CardFieldValue>) {
    val (shown, more) = visibleCardFields(fields)
    // 注意 `map` 是 inline 的（可以在里面调 @Composable），`joinToString` 不是
    val text = shown.map { cardFieldText(it) }.joinToString(" · ") + if (more > 0) " +$more" else ""
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 卡面一格的最终文案：方向类（[CardFieldValue.days]）按「还有 / 已过 / 就是今天」措辞。 */
@Composable
private fun cardFieldText(f: CardFieldValue): String = when {
    f.days == null -> "${f.label} ${f.value}"
    f.days == 0L -> stringResource(R.string.life_card_elapsed_today)
    // quantity 参数要 Int（days 是 Long），格式化参数仍用原值
    f.days > 0 -> pluralStringResource(R.plurals.dashboard_days_until, f.days.toInt(), f.days)
    else -> pluralStringResource(R.plurals.dashboard_days_passed, (-f.days).toInt(), -f.days)
}

/**
 * 记录卡片本体。
 *
 * @param iconKey 模板 `icon`
 * @param colorHex 模板 `color`（`#RRGGBB`；解析失败回落到模块主题色，与全 app 行为一致）
 * @param progress 有进度字段时给分母（0..1），卡面底部画一条 4dp 细条；null 不画
 * @param done 已完成：标题变灰 + 删除线（与首页「看已完成」开关的语义一致）
 */
@Composable
internal fun LifeRecordCard(
    iconKey: String,
    colorHex: String?,
    title: String,
    subtitle: String? = null,
    fields: List<CardFieldValue> = emptyList(),
    progress: Float? = null,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val accent = identityColor(colorHex)
    val shape = MaterialTheme.shapes.large
    val base = modifier.fillMaxWidth().clip(shape)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = if (onClick != null) base.clickable { onClick() } else base
    ) {
        Row(modifier = Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconFor(iconKey), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (fields.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    CardFieldsLine(fields)
                }
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    LifeTrackBar(fraction = progress, color = accent, height = 4.dp)
                }
            }
        }
    }
}
