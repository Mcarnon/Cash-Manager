package com.cashmanager.collector.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashmanager.collector.CashManagerViewModel
import com.cashmanager.collector.Formatting
import com.cashmanager.collector.R
import com.cashmanager.collector.SavedFilterRecord
import com.cashmanager.collector.TransactionFilter
import com.cashmanager.collector.TransactionRecord
import com.cashmanager.collector.TransactionsUiState

@Composable
internal fun BillsRoute(
    viewModel: CashManagerViewModel,
    requestedDateRange: BillDateRange?,
    onDateRangeRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val savedFilters by viewModel.savedFilters.collectAsStateWithLifecycle()
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    var sourceType by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var showAddTransaction by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(requestedDateRange) {
        val request = requestedDateRange ?: return@LaunchedEffect
        startDate = request.startDate.orEmpty()
        endDate = request.endDate.orEmpty()
        typeIndex = 0
        sourceType = ""
        keyword = ""
        viewModel.applyFilter(
            TransactionFilter(
                startDate = request.startDate,
                endDate = request.endDate,
            ),
        )
        onDateRangeRequestHandled()
    }

    LaunchedEffect(transactions.filter) {
        val filter = transactions.filter
        startDate = filter.startDate.orEmpty()
        endDate = filter.endDate.orEmpty()
        typeIndex = typeIndexForKind(filter.kind)
        sourceType = filter.sourceType.orEmpty()
        keyword = filter.keywords.joinToString(" ")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BillsScreen(
            state = transactions,
            startDate = startDate,
            endDate = endDate,
            typeIndex = typeIndex,
            sourceType = sourceType,
            keyword = keyword,
            savedFilters = savedFilters.items,
            onTypeChange = { typeIndex = it },
            onSourceTypeChange = { sourceType = it },
            onKeywordChange = { keyword = it },
            onAddSavedFilter = viewModel::addSavedFilter,
            onDeleteSavedFilter = viewModel::deleteSavedFilter,
            onApplyFilter = viewModel::applyFilter,
            onResetFilter = {
                startDate = ""
                endDate = ""
                typeIndex = 0
                sourceType = ""
                keyword = ""
                viewModel.resetFilters()
            },
            onDelete = viewModel::deleteTransaction,
        )

        FloatingActionButton(
            onClick = { showAddTransaction = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = context.getString(R.string.add_transaction_action),
            )
        }

        if (showAddTransaction) {
            AddTransactionSheet(
                accounts = accounts.items,
                onDismiss = { showAddTransaction = false },
                onSave = { date, amountCents, kind, categoryValue, note, accountId ->
                    viewModel.addTransaction(date, amountCents, kind, categoryValue, note, accountId)
                    showAddTransaction = false
                },
            )
        }
    }
}

@Composable
private fun BillsScreen(
    state: TransactionsUiState,
    startDate: String,
    endDate: String,
    typeIndex: Int,
    sourceType: String,
    keyword: String,
    savedFilters: List<SavedFilterRecord>,
    onTypeChange: (Int) -> Unit,
    onSourceTypeChange: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onAddSavedFilter: (String, String, String) -> Unit,
    onDeleteSavedFilter: (Long) -> Unit,
    onApplyFilter: (TransactionFilter) -> Unit,
    onResetFilter: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    val context = LocalContext.current
    var detailItem by remember { mutableStateOf<TransactionRecord?>(null) }
    var deleteItem by remember { mutableStateOf<TransactionRecord?>(null) }
    var customFilterCategory by remember { mutableStateOf<String?>(null) }

    val expense = state.items.filter { it.kind == "expense" }.sumOf { it.amountCents }
    val income = state.items.filter { it.kind == "income" }.sumOf { it.amountCents }
    val neutralCount = state.items.count { it.kind == "neutral" }

    fun applyFilter() {
        onApplyFilter(
            TransactionFilter(
                startDate = startDate.trim().takeIf { it.isNotEmpty() }
                    ?.let(::normalizeFilterDate),
                endDate = endDate.trim().takeIf { it.isNotEmpty() }
                    ?.let(::normalizeFilterDate),
                kind = kindFilterForTypeIndex(typeIndex),
                sourceType = sourceType.takeIf { it.isNotEmpty() },
                keywords = extractKeywords(keyword),
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FilterCard(
                typeIndex = typeIndex,
                sourceType = sourceType,
                keyword = keyword,
                savedFilters = savedFilters,
                onTypeChange = onTypeChange,
                onSourceTypeChange = onSourceTypeChange,
                onKeywordChange = onKeywordChange,
                onAddSavedFilter = { customFilterCategory = it },
                onDeleteSavedFilter = onDeleteSavedFilter,
                onApply = ::applyFilter,
                onReset = onResetFilter,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = context.getString(
                        R.string.filter_summary,
                        state.items.size,
                        Formatting.currency(context, expense),
                        Formatting.currency(context, income),
                        neutralCount,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = context.getString(R.string.list_interaction_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
        }

        if (state.items.isEmpty() && !state.loading) {
            item {
                EmptyBillsState()
            }
        }

        items(
            items = state.items,
            key = { it.id },
        ) { item ->
            TransactionCard(
                item = item,
                onClick = { detailItem = item },
                onDeleteClick = { deleteItem = item },
            )
        }
    }

    detailItem?.let { item ->
        TransactionDetailsDialog(
            item = item,
            onDismiss = { detailItem = null },
        )
    }

    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text(context.getString(R.string.delete_title)) },
            text = {
                Text(
                    context.getString(
                        R.string.delete_message,
                        Formatting.date(context, item.date),
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) {
                    Text(context.getString(R.string.cancel_action))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(item.id)
                        deleteItem = null
                    },
                ) {
                    Text(
                        text = context.getString(R.string.delete_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }

    customFilterCategory?.let { category ->
        AddSavedFilterDialog(
            category = category,
            onDismiss = { customFilterCategory = null },
            onSave = { name, value ->
                onAddSavedFilter(category, name, value)
                customFilterCategory = null
            },
        )
    }
}

@Composable
private fun FilterCard(
    typeIndex: Int,
    sourceType: String,
    keyword: String,
    savedFilters: List<SavedFilterRecord>,
    onTypeChange: (Int) -> Unit,
    onSourceTypeChange: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onAddSavedFilter: (String) -> Unit,
    onDeleteSavedFilter: (Long) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val typeSavedFilters = savedFilters.filter { it.category == "type" }
    val sourceSavedFilters = savedFilters.filter { it.category == "source" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.filter_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        context.getString(
                            if (expanded) R.string.filter_collapse else R.string.filter_expand,
                        ),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = onKeywordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(context.getString(R.string.keyword_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                    )

                    Text(
                        text = context.getString(R.string.filter_type_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = typeIndex == 0,
                            onClick = { onTypeChange(0) },
                            label = { Text(context.getString(R.string.type_all)) },
                        )
                        FilterChip(
                            selected = typeIndex == 1,
                            onClick = { onTypeChange(1) },
                            label = { Text(context.getString(R.string.type_expense)) },
                        )
                        FilterChip(
                            selected = typeIndex == 2,
                            onClick = { onTypeChange(2) },
                            label = { Text(context.getString(R.string.type_income)) },
                        )
                        FilterChip(
                            selected = typeIndex == 3,
                            onClick = { onTypeChange(3) },
                            label = { Text(context.getString(R.string.type_neutral)) },
                        )
                        typeSavedFilters.forEach { saved ->
                            val savedTypeIndex = when (saved.value) {
                                "expense" -> 1
                                "income" -> 2
                                "neutral" -> 3
                                else -> 0
                            }
                            SavedFilterChip(
                                name = saved.name,
                                selected = typeIndex == savedTypeIndex,
                                onClick = { onTypeChange(savedTypeIndex) },
                                onDelete = { onDeleteSavedFilter(saved.id) },
                            )
                        }
                        IconButton(onClick = { onAddSavedFilter("type") }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = context.getString(R.string.saved_filter_add_type),
                            )
                        }
                    }

                    Text(
                        text = context.getString(R.string.filter_source_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sourceFilterOptions.forEach { (value, labelRes) ->
                            FilterChip(
                                selected = sourceType == value,
                                onClick = { onSourceTypeChange(value) },
                                label = { Text(context.getString(labelRes)) },
                            )
                        }
                        sourceSavedFilters.forEach { saved ->
                            SavedFilterChip(
                                name = saved.name,
                                selected = sourceType == saved.value,
                                onClick = { onSourceTypeChange(saved.value) },
                                onDelete = { onDeleteSavedFilter(saved.id) },
                            )
                        }
                        IconButton(onClick = { onAddSavedFilter("source") }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = context.getString(R.string.saved_filter_add_source),
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onApply,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(context.getString(R.string.filter_action))
                        }
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(context.getString(R.string.reset_filter_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedFilterChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = context.getString(R.string.delete_saved_filter_action),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onDelete),
            )
        },
    )
}

@Composable
private fun AddSavedFilterDialog(
    category: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    val options = if (category == "type") {
        savedTypeFilterOptions
    } else {
        sourceFilterOptions.filter { it.first.isNotEmpty() }
    }
    var selectedValue by rememberSaveable { mutableStateOf(options.first().first) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                context.getString(
                    if (category == "type") {
                        R.string.saved_filter_type_title
                    } else {
                        R.string.saved_filter_source_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(context.getString(R.string.saved_filter_name_hint)) },
                    singleLine = true,
                    isError = error,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { (value, labelRes) ->
                        FilterChip(
                            selected = selectedValue == value,
                            onClick = { selectedValue = value },
                            label = { Text(context.getString(labelRes)) },
                        )
                    }
                }
                if (error) {
                    Text(
                        text = context.getString(R.string.saved_filter_name_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel_action))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedName = name.trim()
                    if (normalizedName.isEmpty()) {
                        error = true
                        return@Button
                    }
                    onSave(normalizedName, selectedValue)
                },
            ) {
                Text(context.getString(R.string.saved_filter_save_action))
            }
        },
    )
}

@Composable
private fun EmptyBillsState() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = context.getString(R.string.no_matching_bills),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
