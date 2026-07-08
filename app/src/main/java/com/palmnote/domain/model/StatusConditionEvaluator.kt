package com.palmnote.domain.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StatusConditionEvaluator {
    fun evaluate(condition: JsonObject, fieldsData: Map<String, Any>): Boolean {
        val field = condition["field"]?.jsonPrimitive?.content ?: return false
        val operator = condition["operator"]?.jsonPrimitive?.content ?: return false
        val compareField = condition["compareField"]?.jsonPrimitive?.content
        val compareValue = condition["compareValue"]

        val fv = fieldsData[field] ?: return false
        val tv = when {
            compareField != null -> fieldsData[compareField]
            compareValue != null -> parseCompareValue(compareValue)
            else -> return false
        } ?: return false

        return when (operator) {
            ">=" -> compareNumeric(fv, tv) { a, b -> a >= b }
            "<=" -> compareNumeric(fv, tv) { a, b -> a <= b }
            ">" -> compareNumeric(fv, tv) { a, b -> a > b }
            "<" -> compareNumeric(fv, tv) { a, b -> a < b }
            "==" -> fv == tv
            "start_lte_today" -> compareDateLteToday(fv, field)
            "end_lt_today" -> compareDateLteToday(fv, field, orEqual = false)
            "lte_today" -> compareDateLteToday(fv, field)
            else -> false
        }
    }

    private fun compareDateLteToday(value: Any, fieldName: String, orEqual: Boolean = true): Boolean {
        val millis = when (value) {
            is String -> value.toLongOrNull()
            is Number -> value.toLong()
            else -> null
        } ?: return false
        val date = try { Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() } catch (_: Exception) { return false }
        val today = LocalDate.now()
        return if (orEqual) !date.isAfter(today) else date.isBefore(today)
    }

    private fun parseCompareValue(element: JsonElement): Any? {
        val prim = element.jsonPrimitive
        val content = prim.content
        return when {
            prim.isString -> content
            content.toLongOrNull() != null -> content.toLong()
            else -> content
        }
    }

    private fun compareNumeric(a: Any, b: Any, cmp: (Long, Long) -> Boolean): Boolean {
        val aLong = when (a) {
            is Number -> a.toLong()
            is String -> a.toLongOrNull()
            else -> null
        }
        val bLong = when (b) {
            is Number -> b.toLong()
            is String -> b.toLongOrNull()
            else -> null
        }
        return if (aLong != null && bLong != null) cmp(aLong, bLong) else false
    }
}
