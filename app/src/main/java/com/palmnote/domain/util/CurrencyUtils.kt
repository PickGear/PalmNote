package com.palmnote.domain.util

import android.content.Context
import com.palmnote.PalmNoteApp
import com.palmnote.R
import com.palmnote.domain.model.Money
import java.text.DecimalFormat

object CurrencyUtils {
    private val currencyFormat = DecimalFormat("#,##0.00")

    private val twoDecimals = DecimalFormat("#.##")

    private fun getAppContext(): Context = PalmNoteApp.instance

    /** 金额（分）转元字符串，如 123456 → ¥1,234.56 */
    fun formatCurrency(amount: Money): String =
        getAppContext().getString(R.string.currency_format, currencyFormat.format(amount.cents / 100.0))

    fun formatCurrency(context: Context, amount: Money): String =
        context.getString(R.string.currency_format, currencyFormat.format(amount.cents / 100.0))

    /** 金额紧凑格式：≥1亿 显示亿，≥100万 显示万 */
    fun formatCompact(amount: Money): String {
        val context = getAppContext()
        return when {
            amount.cents >= 10000_0000_00L ->
                context.getString(R.string.currency_compact_yi, twoDecimals.format(amount.cents / 100.0 / 10000_0000))
            amount.cents >= 1_0000_0000L ->
                context.getString(R.string.currency_compact_wan, twoDecimals.format(amount.cents / 100.0 / 10000))
            else -> context.getString(R.string.currency_format, currencyFormat.format(amount.cents / 100.0))
        }
    }

    fun formatCompact(context: Context, amount: Money): String {
        return when {
            amount.cents >= 10000_0000_00L ->
                context.getString(R.string.currency_compact_yi, twoDecimals.format(amount.cents / 100.0 / 10000_0000))
            amount.cents >= 1_0000_0000L ->
                context.getString(R.string.currency_compact_wan, twoDecimals.format(amount.cents / 100.0 / 10000))
            else -> context.getString(R.string.currency_format, currencyFormat.format(amount.cents / 100.0))
        }
    }
}
