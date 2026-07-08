package com.palmnote.domain.util

import android.content.Context
import com.palmnote.PalmNoteApp
import com.palmnote.R
import java.text.DecimalFormat

object CurrencyUtils {
    private val currencyFormat = DecimalFormat("#,##0.##")

    private fun getAppContext(): Context = PalmNoteApp.instance

    fun formatCurrency(amount: Double): String =
        getAppContext().getString(R.string.currency_format, currencyFormat.format(amount))

    fun formatCurrency(context: Context, amount: Double): String =
        context.getString(R.string.currency_format, currencyFormat.format(amount))

    fun formatCompact(amount: Double): String {
        val twoDecimals = DecimalFormat("#.##")
        val context = getAppContext()
        return when {
            amount >= 10000_0000 -> context.getString(R.string.currency_compact_yi, twoDecimals.format(amount / 10000_0000))
            amount >= 10000 -> context.getString(R.string.currency_compact_wan, twoDecimals.format(amount / 10000))
            else -> context.getString(R.string.currency_format, currencyFormat.format(amount))
        }
    }

    fun formatCompact(context: Context, amount: Double): String {
        val twoDecimals = DecimalFormat("#.##")
        return when {
            amount >= 10000_0000 -> context.getString(R.string.currency_compact_yi, twoDecimals.format(amount / 10000_0000))
            amount >= 10000 -> context.getString(R.string.currency_compact_wan, twoDecimals.format(amount / 10000))
            else -> context.getString(R.string.currency_format, currencyFormat.format(amount))
        }
    }
}
