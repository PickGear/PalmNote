package com.palmnote.ui.life

import com.palmnote.domain.model.DerivedEvaluator
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import com.palmnote.domain.util.DateUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate

/**
 * 派生字段求值的**唯一入口**（§3.5：求值上收到契约层，四态共用同一结果）。
 *
 * 这里只是按类型分派到 [DerivedEvaluator]；`options` 承载参考字段 key
 * （如 `ELAPSED.options[0] = "targetDate"`、`REMAINING.options = [被减, 减]`）。
 * **算不出就返回 null** —— 上层不渲染数值，而不是显示 0。
 *
 * [yearly] 非空表示该模板开了「每年重复」：`ELAPSED` 改按**下一次周年**算，
 * 于是读数永远是「距下次还有几天」（≥ 0），而不是某个已经过去的日期。
 */
object DerivedFields {

    internal fun eval(
        obj: JsonObject,
        config: FieldConfig,
        today: LocalDate = LocalDate.now(),
        yearly: YearlyRepeat? = null
    ): Double? =
        when (config.type) {
            FieldType.FORMULA -> config.defaultValue
                .takeIf { it.isNotBlank() }
                ?.let { DerivedEvaluator.formula(obj, it) }

            FieldType.REMAINING -> config.options.getOrNull(0)
                ?.let { a -> DerivedEvaluator.remaining(obj, a, config.options.getOrNull(1)) }

            FieldType.ELAPSED -> config.options.getOrNull(0)?.let { key ->
                // 未开「每年重复」时**原样走契约层**，保证存量行为一字不变
                if (yearly == null) {
                    DerivedEvaluator.elapsedDays(obj, key, today)?.toDouble()
                } else {
                    rolledElapsedDays(obj, key, today, yearly)
                }
            }

            else -> null
        }

    /** 「每年重复」下的距离天数：先把锚点滚到下一次周年，再算差（正 = 还有，0 = 就是今天）。 */
    private fun rolledElapsedDays(
        obj: JsonObject,
        key: String,
        today: LocalDate,
        yearly: YearlyRepeat
    ): Double? {
        val raw = (obj[key] as? JsonPrimitive)?.contentOrNull ?: return null
        val ms = DateUtils.parseDateValueOrNull(raw) ?: return null
        val anchor = DateUtils.millisToLocalDate(ms)
        val next = nextYearlyOccurrence(anchor, today, yearly.lunar)
        return java.time.temporal.ChronoUnit.DAYS.between(today, next).toDouble()
    }
}
