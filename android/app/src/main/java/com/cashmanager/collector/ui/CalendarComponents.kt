package com.cashmanager.collector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cashmanager.collector.Formatting
import com.cashmanager.collector.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
internal fun WeekCalendarCard(
    weekStart: LocalDate,
    summaries: Map<LocalDate, DayBillSummary>,
    onOpenDay: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val context = LocalContext.current
    val locale = Formatting.locale(context)
    val weekEnd = weekStart.plusDays(6)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPreviousWeek) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = "${Formatting.date(context, weekStart.toString())} - " +
                        Formatting.date(context, weekEnd.toString()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onNextWeek) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
            (0L..6L).forEach { offset ->
                val date = weekStart.plusDays(offset)
                val summary = summaries[date]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDay(date) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Formatting.date(context, date.toString()),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (summary != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = context.getString(
                                    R.string.calendar_bill_count,
                                    summary.count,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (summary.expenseCents > 0L) {
                                Text(
                                    text = context.getString(
                                        R.string.calendar_expense_label,
                                        Formatting.currency(context, summary.expenseCents),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (summary.incomeCents > 0L) {
                                Text(
                                    text = context.getString(
                                        R.string.calendar_income_label,
                                        Formatting.currency(context, summary.incomeCents),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
internal fun MonthCalendarCard(
    month: YearMonth,
    summaries: Map<LocalDate, DayBillSummary>,
    onOpenDay: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val context = LocalContext.current
    val locale = Formatting.locale(context)
    val today = remember { LocalDate.now() }
    val monthTitle = remember(month, locale) {
        val pattern = if (locale.language == "zh") "yyyy年M月" else "MMMM yyyy"
        DateTimeFormatter.ofPattern(pattern, locale).format(month.atDay(1))
    }
    val weekdayLabels = remember(locale) {
        listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        ).map { it.getDisplayName(TextStyle.SHORT, locale) }
    }
    val calendarDays = remember(month) {
        buildList<LocalDate?> {
            repeat(month.atDay(1).dayOfWeek.value - 1) { add(null) }
            for (day in 1..month.lengthOfMonth()) {
                add(month.atDay(day))
            }
            while (size % 7 != 0) add(null)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPreviousMonth) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = monthTitle,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onNextMonth) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            calendarDays.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            summary = date?.let { summaries[it] },
                            isToday = date == today,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (date != null) onOpenDay(date)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun YearCalendarCard(
    year: Int,
    monthSummary: (YearMonth) -> DayBillSummary?,
    onOpenMonth: (YearMonth) -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPreviousYear) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = context.getString(R.string.calendar_year_title, year),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onNextYear) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
            (1..12).chunked(3).forEach { months ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    months.forEach { monthNumber ->
                        val yearMonth = YearMonth.of(year, monthNumber)
                        val summary = monthSummary(yearMonth)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenMonth(yearMonth) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = yearMonth.month.getDisplayName(
                                        TextStyle.SHORT,
                                        Formatting.locale(context),
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = context.getString(
                                        R.string.calendar_bill_count,
                                        summary?.count ?: 0,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = Formatting.currency(
                                        context,
                                        summary?.expenseCents ?: 0L,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    if (months.size < 3) {
                        repeat(3 - months.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CalendarDayCell(
    date: LocalDate?,
    summary: DayBillSummary?,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (date == null) {
        Spacer(modifier = modifier.height(62.dp))
        return
    }
    val context = LocalContext.current
    val backgroundColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        summary != null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(62.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (summary != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (summary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (summary.expenseCents > 0L) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                        )
                    }
                    if (summary.incomeCents > 0L) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }
                Text(
                    text = context.getString(R.string.calendar_bill_count, summary.count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun LegendDot(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun BillLineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
) {
    val expenseColor = MaterialTheme.colorScheme.error
    val incomeColor = MaterialTheme.colorScheme.secondary
    val maxValue = points.maxOfOrNull {
        maxOf(it.expenseCents, it.incomeCents)
    }?.coerceAtLeast(1L) ?: 1L

    Canvas(modifier = modifier) {
        val left = 8.dp.toPx()
        val right = 8.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = 10.dp.toPx()
        val chartWidth = (size.width - left - right).coerceAtLeast(1f)
        val chartHeight = (size.height - top - bottom).coerceAtLeast(1f)

        repeat(4) { index ->
            val y = top + chartHeight * index / 3f
            drawLine(
                color = Color.Gray.copy(alpha = 0.16f),
                start = Offset(left, y),
                end = Offset(size.width - right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        fun drawSeries(
            color: Color,
            valueSelector: (ChartPoint) -> Long,
        ) {
            if (points.isEmpty()) return
            val path = Path()
            val positions = points.mapIndexed { index, point ->
                val x = if (points.size == 1) {
                    left + chartWidth / 2f
                } else {
                    left + chartWidth * index / (points.size - 1f)
                }
                val ratio = valueSelector(point).toFloat() / maxValue.toFloat()
                val y = top + chartHeight - chartHeight * ratio
                Offset(x, y)
            }
            positions.forEachIndexed { index, position ->
                if (index == 0) {
                    path.moveTo(position.x, position.y)
                } else {
                    path.lineTo(position.x, position.y)
                }
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            positions.forEach { position ->
                drawCircle(
                    color = color,
                    radius = 3.dp.toPx(),
                    center = position,
                )
            }
        }

        drawSeries(expenseColor) { it.expenseCents }
        drawSeries(incomeColor) { it.incomeCents }
    }
}
