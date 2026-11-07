package com.cashmanager.collector.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashmanager.collector.CategoryBreakdownUiState
import com.cashmanager.collector.CategoryRuleRecord
import com.cashmanager.collector.CategoryTotal
import com.cashmanager.collector.CashManagerViewModel
import com.cashmanager.collector.Formatting
import com.cashmanager.collector.R
import com.cashmanager.collector.SummaryUiState

@Composable
internal fun SummaryRoute(viewModel: CashManagerViewModel) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val breakdown by viewModel.categoryBreakdown.collectAsStateWithLifecycle()
    var showRulesDialog by rememberSaveable { mutableStateOf(false) }

    SummaryScreen(
        summary = summary,
        breakdown = breakdown,
        onManageRules = { showRulesDialog = true },
    )

    if (showRulesDialog) {
        CategoryRulesDialog(
            rules = breakdown.rules,
            onDismiss = { showRulesDialog = false },
            onAddRule = viewModel::addCategoryRule,
            onDeleteRule = viewModel::deleteCategoryRule,
        )
    }
}

@Composable
private fun SummaryScreen(
    summary: SummaryUiState,
    breakdown: CategoryBreakdownUiState,
    onManageRules: () -> Unit,
) {
    val context = LocalContext.current
    var chartKindIndex by rememberSaveable { mutableIntStateOf(0) }
    val chartItems = if (chartKindIndex == 0) {
        breakdown.expenseItems
    } else {
        breakdown.incomeItems
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = context.getString(R.string.summary_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SummaryCard(
                label = context.getString(R.string.summary_income),
                amount = Formatting.currency(context, summary.incomeCents),
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                accent = IncomeGreen,
            )
        }
        item {
            SummaryCard(
                label = context.getString(R.string.summary_expense),
                amount = Formatting.currency(context, summary.expenseCents),
                icon = Icons.AutoMirrored.Outlined.TrendingDown,
                accent = MaterialTheme.colorScheme.error,
            )
        }
        item {
            SummaryCard(
                label = context.getString(R.string.summary_balance),
                amount = Formatting.currency(context, summary.incomeCents - summary.expenseCents),
                icon = Icons.Outlined.AccountBalanceWallet,
                accent = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            CategoryBreakdownCard(
                breakdown = breakdown,
                chartKindIndex = chartKindIndex,
                items = chartItems,
                onKindChange = { chartKindIndex = it },
                onManageRules = onManageRules,
            )
        }
    }
}

private val IncomeGreen = Color(0xFF2E7D32)

private val pieChartColors = listOf(
    Color(0xFF2563EB),
    Color(0xFF16A34A),
    Color(0xFFF59E0B),
    Color(0xFFDC2626),
    Color(0xFF7C3AED),
    Color(0xFF0891B2),
    Color(0xFFDB2777),
    Color(0xFF65A30D),
    Color(0xFFEA580C),
    Color(0xFF4F46E5),
)

@Composable
private fun CategoryBreakdownCard(
    breakdown: CategoryBreakdownUiState,
    chartKindIndex: Int,
    items: List<CategoryTotal>,
    onKindChange: (Int) -> Unit,
    onManageRules: () -> Unit,
) {
    val context = LocalContext.current
    val total = items.sumOf { it.amountCents }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = chartKindIndex == 0,
                        onClick = { onKindChange(0) },
                        label = { Text(context.getString(R.string.type_expense)) },
                    )
                    FilterChip(
                        selected = chartKindIndex == 1,
                        onClick = { onKindChange(1) },
                        label = { Text(context.getString(R.string.type_income)) },
                    )
                }
                IconButton(onClick = onManageRules) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = context.getString(R.string.add_category_action),
                    )
                }
            }

            when {
                breakdown.loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                items.isEmpty() -> {
                    Text(
                        text = context.getString(R.string.category_chart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CategoryPieChart(
                            items = items,
                            modifier = Modifier.size(156.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            items.forEachIndexed { index, item ->
                                CategoryLegendRow(
                                    item = item,
                                    totalCents = total,
                                    color = pieChartColors[index % pieChartColors.size],
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPieChart(
    items: List<CategoryTotal>,
    modifier: Modifier = Modifier,
) {
    val total = items.sumOf { it.amountCents }.coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        var startAngle = -90f
        items.forEachIndexed { index, item ->
            val sweep = item.amountCents.toFloat() / total.toFloat() * 360f
            drawArc(
                color = pieChartColors[index % pieChartColors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun CategoryLegendRow(
    item: CategoryTotal,
    totalCents: Long,
    color: Color,
) {
    val context = LocalContext.current
    val percent = if (totalCents <= 0L) 0 else (item.amountCents * 100.0 / totalCents).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.category.ifBlank {
                    context.getString(R.string.uncategorized)
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Formatting.currency(context, item.amountCents),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CategoryRulesDialog(
    rules: List<CategoryRuleRecord>,
    onDismiss: () -> Unit,
    onAddRule: (String, String, String) -> Unit,
    onDeleteRule: (Long) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var keywords by rememberSaveable { mutableStateOf("") }
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.category_rules_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = context.getString(R.string.category_rules_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rules.forEach { rule ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${rule.keywords} · ${ruleKindLabel(context, rule.kind)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteRule(rule.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = context.getString(R.string.delete_action),
                            )
                        }
                    }
                    HorizontalDivider()
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(context.getString(R.string.category_rule_name_hint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = {
                        keywords = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(context.getString(R.string.category_rule_keywords_hint)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kindIndex == 0,
                        onClick = { kindIndex = 0 },
                        label = { Text(context.getString(R.string.category_rule_all)) },
                    )
                    FilterChip(
                        selected = kindIndex == 1,
                        onClick = { kindIndex = 1 },
                        label = { Text(context.getString(R.string.type_expense)) },
                    )
                    FilterChip(
                        selected = kindIndex == 2,
                        onClick = { kindIndex = 2 },
                        label = { Text(context.getString(R.string.type_income)) },
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        val normalizedName = name.trim()
                        val normalizedKeywords = keywords.trim()
                        if (normalizedName.isEmpty() || normalizedKeywords.isEmpty()) {
                            error = context.getString(R.string.category_rule_invalid)
                            return@Button
                        }
                        onAddRule(
                            normalizedName,
                            normalizedKeywords,
                            when (kindIndex) {
                                1 -> "expense"
                                2 -> "income"
                                else -> "all"
                            },
                        )
                        name = ""
                        keywords = ""
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(context.getString(R.string.category_rule_add))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.close_action))
            }
        },
    )
}

private fun ruleKindLabel(context: Context, kind: String): String = when (kind) {
    "expense" -> context.getString(R.string.type_expense)
    "income" -> context.getString(R.string.type_income)
    else -> context.getString(R.string.category_rule_all)
}

@Composable
private fun SummaryCard(
    label: String,
    amount: String,
    icon: ImageVector,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = amount,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
    }
}
