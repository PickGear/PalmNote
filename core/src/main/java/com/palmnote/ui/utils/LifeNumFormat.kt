package com.palmnote.ui.utils

import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * §13.3 B 档前置件：数字格式化器（此前全模块 NumberFormat/DecimalFormat 0 处，钱与天数一律裸拼）。
 * 规则（§3.5.1 / §13.6）：
 * - 千分位分组；
 * - 负数语义写 `-¥100`，绝不写 `¥-100`；
 * - 币种不重复：带 ¥ 前缀时不再追加「元」（修 `¥100元` 病）。
 */
object LifeNumFormat {

    /** DecimalFormat 非线程安全，但本格式化器只在 UI 线程组合期调用。 */
    private val grouped = DecimalFormat("#,##0.####")

    /** 千分位：12345 -> 12,345；12.5 -> 12.5 */
    fun num(value: BigDecimal): String = grouped.format(value)

    fun num(value: Long): String = grouped.format(value)

    fun num(value: Int): String = grouped.format(value.toLong())

    /**
     * 货币：¥ + 千分位；负数 -¥1,234。
     * unit 仅在无法用 ¥ 表达时追加（非「元/¥」单位），杜绝「¥100元」。
     */
    fun money(value: BigDecimal, unit: String = ""): String {
        val body = "\u00A5" + grouped.format(value.abs())
        val sign = if (value.signum() < 0) "-" else ""
        val suffix = if (unit.isNotBlank() && unit != "\u5143" && unit != "\u00A5") unit else ""
        return "$sign$body$suffix"
    }

    /** 带单位数值：1,234步；单位为「元/¥」时走 money 规则。 */
    fun withUnit(value: BigDecimal, unit: String): String {
        if (unit.isBlank()) return num(value)
        if (unit == "\u5143" || unit == "\u00A5") return money(value)
        val sign = if (value.signum() < 0) "-" else ""
        return "$sign${grouped.format(value.abs())}$unit"
    }
}
