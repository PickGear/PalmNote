package com.palmnote.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

/**
 * 金额值类型，以最小货币单位「分」存储，避免浮点精度误差。
 * 1 元 = 100 分。运算全部为整数运算，精确无误差。
 */
@JvmInline
value class Money(val cents: Long) {

    operator fun plus(other: Money): Money = Money(cents + other.cents)

    operator fun minus(other: Money): Money = Money(cents - other.cents)

    operator fun unaryMinus(): Money = Money(-cents)

    operator fun times(times: Int): Money = Money(cents * times)

    val isPositive: Boolean get() = cents > 0

    val isNegative: Boolean get() = cents < 0

    val isZero: Boolean get() = cents == 0L

    fun abs(): Money = Money(if (cents < 0) -cents else cents)

    companion object {
        val ZERO = Money(0L)

        /** 由元（Double）构造，四舍五入到分 */
        fun fromYuan(yuan: Double): Money {
            if (yuan.isNaN() || yuan.isInfinite()) return ZERO
            return Money((yuan * 100).roundToLong())
        }

        /** 解析用户输入/导入的元字符串为分，如 "1,234.56" → 123456 */
        fun parse(value: String): Money? {
            val cleaned = value.replace(",", "").trim()
            if (cleaned.isEmpty()) return null
            return try {
                val bd = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
                Money(bd.movePointRight(2).longValueExact())
            } catch (_: NumberFormatException) {
                null
            } catch (_: ArithmeticException) {
                null
            }
        }
    }
}

/** 分 → 元字符串（无千分位分隔），如 123456 → "1234.56" */
fun Long.toYuanString(): String = String.format(java.util.Locale.US, "%.2f", this / 100.0)

/** Long 分 → Money */
fun Long.toMoney(): Money = Money(this)
