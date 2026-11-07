package com.cashmanager.collector

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object Formatting {
    private val currencyFormatters = ConcurrentHashMap<Pair<Locale, String>, NumberFormat>()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val timestampFormatters = ConcurrentHashMap<Locale, DateFormat>()

    fun locale(context: Context): Locale =
        context.resources.configuration.locales[0]

    fun currency(context: Context, cents: Long): String {
        val locale = locale(context)
        val currencyCode = context.getString(R.string.currency_code)
        val formatter = currencyFormatters.getOrPut(locale to currencyCode) {
            NumberFormat.getCurrencyInstance(locale).apply {
                currency = Currency.getInstance(currencyCode)
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
        }
        return synchronized(formatter) {
            formatter.format(cents / 100.0)
        }
    }

    fun signedAmount(context: Context, cents: Long, kind: String): String {
        val sign = when (kind) {
            "income" -> "+"
            "neutral" -> ""
            else -> "-"
        }
        return "$sign${currency(context, cents)}"
    }

    fun date(context: Context, isoDate: String): String {
        return try {
            LocalDate.parse(isoDate).format(dateFormatter)
        } catch (_: Exception) {
            isoDate
        }
    }

    fun timestamp(context: Context, timestampMillis: Long): String {
        val locale = locale(context)
        val formatter = timestampFormatters.getOrPut(locale) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale)
        }
        return synchronized(formatter) {
            formatter.format(Date(timestampMillis))
        }
    }

    fun parseAmountToCents(input: String, locale: Locale): Long? {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        var normalized = input.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace(symbols.groupingSeparator.toString(), "")
        if (symbols.decimalSeparator != '.') {
            normalized = normalized.replace(symbols.decimalSeparator, '.')
        }
        if (normalized.isBlank()) return null

        val amount = normalized.toBigDecimalOrNull() ?: return null
        if (amount <= BigDecimal.ZERO) return null
        return try {
            amount.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }
}
