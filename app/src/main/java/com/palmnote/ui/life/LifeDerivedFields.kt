package com.palmnote.ui.life

import com.palmnote.domain.model.DerivedEvaluator
import com.palmnote.domain.model.FieldConfig
import com.palmnote.domain.model.FieldType
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/**
 * 派生字段求值的**唯一入口**（§3.5：求值上收到契约层，四态共用同一结果）。
 *
 * 这里只是按类型分派到 [DerivedEvaluator]；`options` 承载参考字段 key
 * （如 `ELAPSED.options[0] = "targetDate"`、`REMAINING.options = [被减, 减]`）。
 * **算不出就返回 null** —— 上层不渲染数值，而不是显示 0。
 */
object DerivedFields {

    fun eval(obj: JsonObject, config: FieldConfig, today: LocalDate = LocalDate.now()): Double? =
        when (config.type) {
            FieldType.FORMULA -> config.defaultValue
                .takeIf { it.isNotBlank() }
                ?.let { DerivedEvaluator.formula(obj, it) }

            FieldType.REMAINING -> config.options.getOrNull(0)
                ?.let { a -> DerivedEvaluator.remaining(obj, a, config.options.getOrNull(1)) }

            FieldType.ELAPSED -> config.options.getOrNull(0)
                ?.let { key -> DerivedEvaluator.elapsedDays(obj, key, today)?.toDouble() }

            else -> null
        }
}
