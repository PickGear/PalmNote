package com.palmnote.ui.utils

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * §13.3 B 档前置件：数字格式化器（此前全模块 NumberFormat/DecimalFormat 0 处，钱与天数一律裸拼）。
 * 规则（§3.5.1 / §13.6）：
 * - 千分位分组；
 * - 负数语义写 `-¥100`，绝不写 `¥-100`；
 * - 币种不重复：带 ¥ 前缀时不再追加「元」（修 `¥100元` 病）；
 * - **小数位统一最多两位**（`#,##0.##`）：此前 hero 用 `#,##0.####`、详情页用 `%,.1f`，
 *   同一个数在两处显示不同精度 —— 显示口径必须只有一处。
 *
 * 生活模块的 `fmtNumber` / `fmtMoney`（app 侧）**都委托到这里**，别再各写一套。
 */
object LifeNumFormat {

    /**
     * `DecimalFormat` 非线程安全，但本格式化器只在 UI 线程组合期调用。
     *
     * 分隔符**固定按 `Locale.US`**（`,` 分组 / `.` 小数）：设计稿与全 app 的数字都是 `1,234.56`，
     * 不该因为设备语言（如德语 `1.234,56`）而变样。
     *
     * 舍入用 **HALF_UP**（四舍五入）而不是 `DecimalFormat` 默认的 HALF_EVEN（银行家舍入）：
     * 后者会让 `3,500.125` 显示成 `3,500.12` —— 记账/金额场景里用户预期是 `3,500.13`。
     */
    private val grouped = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).apply {
        roundingMode = java.math.RoundingMode.HALF_UP
    }

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
