package com.cashmanager.collector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashmanager.collector.CalendarTransactionsUiState
import com.cashmanager.collector.CashManagerViewModel
import com.cashmanager.collector.Formatting
import com.cashmanager.collector.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Composable
internal fun CalendarRoute(
    viewModel: CashManagerViewModel,
    onOpenBills: (String, String) -> Unit,
) {
    val calendar by viewModel.calendarTransactions.collectAsStateWithLifecycle()
    CalendarScreen(
        state = calendar,
        onOpenBills = onOpenBills,
    )
}

private enum class CalendarMode {
    WEEK,
    MONTH,
    YEAR,
}

internal data class DayBillSummary(
    val date: LocalDate,
    val count: Int,
    val expenseCents: Long,
    val incomeCents: Long,
)

internal data class ChartPoint(
    val label: String,
    val expenseCents: Long,
    val incomeCents: Long,
)

@Composable
private fun CalendarScreen(
    state: CalendarTransactionsUiState,
    onOpenBills: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val locale = Formatting.locale(context)
    val today = remember { LocalDate.now() }
    var anchorDateValue by rememberSaveable { mutableStateOf(today.toString()) }
    val anchorDate = remember(anchorDateValue) { LocalDate.parse(anchorDateValue) }
    var modeIndex by rememberSaveable { mutableIntStateOf(CalendarMode.MONTH.ordinal) }
    val mode = CalendarMode.entries[modeIndex]
    var rangeExpanded by rememberSaveable { mutableStateOf(false) }
    var rangeStart by rememberSaveable {
        mutableStateOf(
            Formatting.date(
                context,
                YearMonth.from(today).atDay(1).toString(),
            ),
        )
    }
    var rangeEnd by rememberSaveable {
        mutableStateOf(
            Formatting.date(
                context,
                YearMonth.from(today).atEndOfMonth().toString(),
            ),
        )
    }
    var rangeError by remember { mutableStateOf<String?>(null) }

    val weekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(6)
    val displayedMonth = YearMonth.from(anchorDate)
    val monthStart = displayedMonth.atDay(1)
    val monthEnd = displayedMonth.atEndOfMonth()
    val yearStart = LocalDate.of(anchorDate.year, 1, 1)
    val yearEnd = LocalDate.of(anchorDate.year, 12, 31)

    val dailySummaries = remember(state.items) {
        state.items.mapNotNull { item ->
            val date = runCatching { LocalDate.parse(item.date) }.getOrNull()
                ?: return@mapNotNull null
            date to item
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        ).mapValues { (date, items) ->
            DayBillSummary(
                date = date,
                count = items.size,
                expenseCents = items.filter { it.kind == "expense" }.sumOf { it.amountCents },
                incomeCents = items.filter { it.kind == "income" }.sumOf { it.amountCents },
            )
        }
    }

    fun setWeekRange(anchor: LocalDate) {
        val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(6)
        rangeStart = Formatting.date(context, start.toString())
        rangeEnd = Formatting.date(context, end.toString())
    }

    fun setMonthRange(anchor: LocalDate) {
        val month = YearMonth.from(anchor)
        rangeStart = Formatting.date(context, month.atDay(1).toString())
        rangeEnd = Formatting.date(context, month.atEndOfMonth().toString())
    }

    fun setYearRange(anchor: LocalDate) {
        rangeStart = Formatting.date(context, LocalDate.of(anchor.year, 1, 1).toString())
        rangeEnd = Formatting.date(context, LocalDate.of(anchor.year, 12, 31).toString())
    }

    fun applyModeRange(targetMode: CalendarMode, anchor: LocalDate) {
        when (targetMode) {
            CalendarMode.WEEK -> setWeekRange(anchor)
            CalendarMode.MONTH -> setMonthRange(anchor)
            CalendarMode.YEAR -> setYearRange(anchor)
        }
    }

    fun parseRange(): Pair<LocalDate, LocalDate>? {
        val start = normalizeFilterDate(rangeStart)?.let(LocalDate::parse) ?: return null
        val end = normalizeFilterDate(rangeEnd)?.let(LocalDate::parse) ?: return null
        if (start > end) return null
        return start to end
    }

    fun monthSummary(yearMonth: YearMonth): DayBillSummary? {
        val summaries = dailySummaries.values.filter {
            YearMonth.from(it.date) == yearMonth
        }
        if (summaries.isEmpty()) return null
        return DayBillSummary(
            date = yearMonth.atDay(1),
            count = summaries.sumOf { it.count },
            expenseCents = summaries.sumOf { it.expenseCents },
            incomeCents = summaries.sumOf { it.incomeCents },
        )
    }

    fun totalsBetween(start: LocalDate, end: LocalDate): List<DayBillSummary> {
        return generateSequence(start) { date ->
            date.plusDays(1).takeIf { it <= end }
        }.map { date ->
            dailySummaries[date] ?: DayBillSummary(
                date = date,
                count = 0,
                expenseCents = 0L,
                incomeCents = 0L,
            )
        }.toList()
    }

    fun dailyChartPoints(start: LocalDate, end: LocalDate): List<ChartPoint> {
        val formatter = DateTimeFormatter.ofPattern("MM.dd")
        return totalsBetween(start, end).map { summary ->
            ChartPoint(
                label = formatter.format(summary.date),
                expenseCents = summary.expenseCents,
                incomeCents = summary.incomeCents,
            )
        }
    }

    fun monthlyChartPoints(start: LocalDate, end: LocalDate): List<ChartPoint> {
        val formatter = DateTimeFormatter.ofPattern("yyyy.MM")
        val firstMonth = YearMonth.from(start)
        val lastMonth = YearMonth.from(end)
        val points = mutableListOf<ChartPoint>()
        var month = firstMonth
        while (month <= lastMonth) {
            val summary = monthSummary(month)
            points += ChartPoint(
                label = formatter.format(month.atDay(1)),
                expenseCents = summary?.expenseCents ?: 0L,
                incomeCents = summary?.incomeCents ?: 0L,
            )
            month = month.plusMonths(1)
        }
        return points
    }

    val activeRange = parseRange()
    val chartPoints = activeRange?.let { (start, end) ->
        val dayCount = ChronoUnit.DAYS.between(start, end) + 1
        if (dayCount <= 62L) {
            dailyChartPoints(start, end)
        } else {
            monthlyChartPoints(start, end)
        }
    } ?: emptyList()

    val currentRangeLabel = activeRange?.let { (start, end) ->
        "${Formatting.date(context, start.toString())} - ${Formatting.date(context, end.toString())}"
    } ?: context.getString(R.string.invalid_date)

    fun openCurrentRange() {
        val normalizedStart = normalizeFilterDate(rangeStart)
        val normalizedEnd = normalizeFilterDate(rangeEnd)
        if (normalizedStart == null || normalizedEnd == null) {
            rangeError = context.getString(R.string.invalid_date)
            return
        }
        if (normalizedStart > normalizedEnd) {
            rangeError = context.getString(R.string.date_range_invalid)
            return
        }
        rangeError = null
        onOpenBills(normalizedStart, normalizedEnd)
    }

    fun changeMode(newMode: CalendarMode) {
        modeIndex = newMode.ordinal
        rangeError = null
        anchorDateValue = today.toString()
        applyModeRange(newMode, today)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rangeExpanded = !rangeExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = context.getString(R.string.calendar_range_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = currentRangeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { rangeExpanded = !rangeExpanded }) {
                            Text(
                                context.getString(
                                    if (rangeExpanded) {
                                        R.string.filter_collapse
                                    } else {
                                        R.string.filter_expand
                                    },
                                ),
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = rangeExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    CalendarMode.WEEK to R.string.calendar_range_week,
                                    CalendarMode.MONTH to R.string.calendar_range_month,
                                    CalendarMode.YEAR to R.string.calendar_range_year,
                                ).forEach { (itemMode, labelRes) ->
                                    FilterChip(
                                        selected = mode == itemMode,
                                        onClick = { changeMode(itemMode) },
                                        label = { Text(context.getString(labelRes)) },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = rangeStart,
                                    onValueChange = {
                                        rangeStart = it
                                        rangeError = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(context.getString(R.string.start_date_hint)) },
                                    singleLine = true,
                                    isError = rangeError != null,
                                )
                                OutlinedTextField(
                                    value = rangeEnd,
                                    onValueChange = {
                                        rangeEnd = it
                                        rangeError = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(context.getString(R.string.end_date_hint)) },
                                    singleLine = true,
                                    isError = rangeError != null,
                                )
                            }
                            rangeError?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Button(
                                onClick = ::openCurrentRange,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(context.getString(R.string.calendar_view_bills))
                            }
                        }
                    }
                }
            }
        }

        item {
            when (mode) {
                CalendarMode.WEEK -> WeekCalendarCard(
                    weekStart = weekStart,
                    summaries = dailySummaries,
                    onOpenDay = { date ->
                        onOpenBills(date.toString(), date.toString())
                    },
                    onPreviousWeek = {
                        val newAnchor = anchorDate.minusWeeks(1)
                        anchorDateValue = newAnchor.toString()
                        setWeekRange(newAnchor)
                    },
                    onNextWeek = {
                        val newAnchor = anchorDate.plusWeeks(1)
                        anchorDateValue = newAnchor.toString()
                        setWeekRange(newAnchor)
                    },
                )
                CalendarMode.YEAR -> YearCalendarCard(
                    year = anchorDate.year,
                    monthSummary = ::monthSummary,
                    onOpenMonth = { yearMonth ->
                        modeIndex = CalendarMode.MONTH.ordinal
                        anchorDateValue = yearMonth.atDay(1).toString()
                        setMonthRange(yearMonth.atDay(1))
                    },
                    onPreviousYear = {
                        val newAnchor = anchorDate.minusYears(1)
                        anchorDateValue = newAnchor.toString()
                        setYearRange(newAnchor)
                    },
                    onNextYear = {
                        val newAnchor = anchorDate.plusYears(1)
                        anchorDateValue = newAnchor.toString()
                        setYearRange(newAnchor)
                    },
                )
                CalendarMode.MONTH -> MonthCalendarCard(
                    month = displayedMonth,
                    summaries = dailySummaries,
                    onOpenDay = { date ->
                        onOpenBills(date.toString(), date.toString())
                    },
                    onPreviousMonth = {
                        val newAnchor = anchorDate.minusMonths(1)
                        anchorDateValue = newAnchor.toString()
                        setMonthRange(newAnchor)
                    },
                    onNextMonth = {
                        val newAnchor = anchorDate.plusMonths(1)
                        anchorDateValue = newAnchor.toString()
                        setMonthRange(newAnchor)
                    },
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = context.getString(R.string.calendar_chart_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(
                            color = MaterialTheme.colorScheme.error,
                            label = context.getString(R.string.calendar_chart_expense),
                        )
                        LegendDot(
                            color = MaterialTheme.colorScheme.secondary,
                            label = context.getString(R.string.calendar_chart_income),
                        )
                    }
                    if (chartPoints.isEmpty()) {
                        Text(
                            text = context.getString(R.string.calendar_chart_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        BillLineChart(
                            points = chartPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = chartPoints.first().label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = chartPoints[chartPoints.size / 2].label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = chartPoints.last().label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
