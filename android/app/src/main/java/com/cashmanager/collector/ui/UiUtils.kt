package com.cashmanager.collector.ui

import android.content.Context
import com.cashmanager.collector.R
import java.time.LocalDate

internal data class BillDateRange(
    val startDate: String?,
    val endDate: String?,
)

internal fun sourceLabel(context: Context, sourceType: String): String = when (sourceType) {
    "manual" -> context.getString(R.string.source_manual)
    "wechat" -> context.getString(R.string.source_wechat)
    "alipay" -> context.getString(R.string.source_alipay)
    "bank" -> context.getString(R.string.source_bank)
    "cash" -> context.getString(R.string.source_cash)
    else -> sourceType
}

internal fun extractKeywords(input: String): List<String> {
    return input
        .split(Regex("[\\s\\u3000,，、;；|]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

internal fun normalizeFilterDate(value: String): String? {
    val cleaned = value.trim()
    if (cleaned.isEmpty()) return null
    val currentYear = LocalDate.now().year

    fun of(year: Int, month: Int, day: Int): String? = try {
        LocalDate.of(year, month, day).toString()
    } catch (_: Exception) {
        null
    }

    Regex("""^(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?$""").find(cleaned)?.let {
        return of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
    }
    Regex("""^(\d{4})(\d{2})(\d{2})$""").find(cleaned)?.let {
        return of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
    }
    Regex("""^(\d{1,2})[-/.月](\d{1,2})日?$""").find(cleaned)?.let {
        return of(currentYear, it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    return null
}

internal fun kindFilterForTypeIndex(index: Int): String? = when (index) {
    1 -> "expense"
    2 -> "income"
    3 -> "neutral"
    else -> null
}

internal fun typeIndexForKind(kind: String?): Int = when (kind) {
    "expense" -> 1
    "income" -> 2
    "neutral" -> 3
    else -> 0
}

internal val savedTypeFilterOptions = listOf(
    "expense" to R.string.type_expense,
    "income" to R.string.type_income,
    "neutral" to R.string.type_neutral,
)

internal val sourceFilterOptions = listOf(
    "" to R.string.source_all,
    "wechat" to R.string.source_wechat,
    "alipay" to R.string.source_alipay,
    "bank" to R.string.source_bank,
    "manual" to R.string.source_manual,
)
