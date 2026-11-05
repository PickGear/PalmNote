package com.palmnote.domain.util

import android.content.Context
import com.palmnote.R
import com.palmnote.domain.model.Money
import java.text.DecimalFormat

object CurrencyUtils {
    private val currencyFormat = DecimalFormat("#,##0.00")

    private val twoDecimals = DecimalFormat("#.##")

    /**
     * 金额（分）转元字符串，如 123456 → ¥1,234.56。
     * 负数负号放币符前（-¥1,234.56），与 [formatCompact] / 小组件侧 formatMoneyCompact 同一约定，
     * 避免同一个数在详情页和卡片上出现「¥-」与「-¥」两种写法。
     */
    fun formatCurrency(context: Context, amount: Money): String {
        val sign = if (amount.cents < 0) "-" else ""
        return sign + context.getString(
            R.string.currency_format,
            currencyFormat.format(kotlin.math.abs(amount.cents) / 100.0)
        )
    }

    /**
     * 金额紧凑格式：≥1亿 显示亿，≥100万 显示万。
     * 负数（信用卡欠款超过资产时净资产为负）负号放币符前（-¥280.00），
     * 与小组件侧 formatMoneyCompact 同一约定，别让币符夹在中间变成「¥-280」。
     */
    fun formatCompact(context: Context, amount: Money): String {
        val sign = if (amount.cents < 0) "-" else ""
        val abs = kotlin.math.abs(amount.cents)
        return when {
            abs >= 10000_0000_00L ->
                sign + context.getString(R.string.currency_compact_yi, twoDecimals.format(abs / 100.0 / 10000_0000))
            abs >= 1_0000_0000L ->
                sign + context.getString(R.string.currency_compact_wan, twoDecimals.format(abs / 100.0 / 10000))
            else -> sign + context.getString(R.string.currency_format, currencyFormat.format(abs / 100.0))
        }
    }
}
