package com.cashmanager.collector.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashmanager.collector.AccountsUiState
import com.cashmanager.collector.CashManagerViewModel
import com.cashmanager.collector.Formatting
import com.cashmanager.collector.LedgerAccountRecord
import com.cashmanager.collector.R
import com.cashmanager.collector.SourceProfileRecord
import com.cashmanager.collector.SourcesUiState

@Composable
internal fun ProvidersRoute(viewModel: CashManagerViewModel) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var showAddAccount by rememberSaveable { mutableStateOf(false) }
    var mappingSource by remember { mutableStateOf<SourceProfileRecord?>(null) }
    var importSourceId by remember { mutableStateOf<Long?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importSourceId?.let { sourceId ->
                viewModel.importStatement(sourceId, uri)
            }
        }
        importSourceId = null
    }

    ProvidersScreen(
        sources = sources,
        accounts = accounts,
        onSourceEnabledChange = viewModel::setSourceEnabled,
        onMapSourceAccount = { source -> mappingSource = source },
        onAddAccount = { showAddAccount = true },
        onImportSource = { sourceId ->
            importSourceId = sourceId
            importLauncher.launch(
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "text/plain",
                    "application/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "application/octet-stream",
                ),
            )
        },
    )

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onSave = { name, type, openingBalanceCents ->
                viewModel.addAccount(name, type, openingBalanceCents)
                showAddAccount = false
            },
        )
    }

    mappingSource?.let { source ->
        AccountPickerDialog(
            sourceName = source.displayName,
            accounts = accounts.items,
            onDismiss = { mappingSource = null },
            onSelect = { accountId ->
                viewModel.setSourceAccount(source.id, accountId)
                mappingSource = null
            },
        )
    }
}

@Composable
private fun ProvidersScreen(
    sources: SourcesUiState,
    accounts: AccountsUiState,
    onSourceEnabledChange: (Long, Boolean) -> Unit,
    onMapSourceAccount: (SourceProfileRecord) -> Unit,
    onAddAccount: () -> Unit,
    onImportSource: (Long) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CollapsibleSection(
                title = context.getString(R.string.sources_official_title),
            ) {
                sources.items.forEach { source ->
                    SourceProfileCard(
                        source = source,
                        onEnabledChange = { enabled -> onSourceEnabledChange(source.id, enabled) },
                        onMapAccount = { onMapSourceAccount(source) },
                        onImport = { onImportSource(source.id) },
                    )
                }
            }
        }
        item {
            CollapsibleSection(
                title = context.getString(R.string.accounts_title),
            ) {
                accounts.items.forEach { account ->
                    AccountCard(account)
                }
                OutlinedButton(
                    onClick = onAddAccount,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.add_account_action))
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "sectionArrowRotation",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
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
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: LedgerAccountRecord) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = context.getString(
                        R.string.account_type_line,
                        context.getString(
                            if (account.type == "liability") {
                                R.string.account_type_liability
                            } else {
                                R.string.account_type_asset
                            },
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatting.currency(context, account.currentBalanceCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = context.getString(
                        R.string.account_opening_balance,
                        Formatting.currency(context, account.openingBalanceCents),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    var openingBalance by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.add_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(context.getString(R.string.account_name_hint)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = typeIndex == 0,
                        onClick = { typeIndex = 0 },
                        label = { Text(context.getString(R.string.account_type_asset)) },
                    )
                    FilterChip(
                        selected = typeIndex == 1,
                        onClick = { typeIndex = 1 },
                        label = { Text(context.getString(R.string.account_type_liability)) },
                    )
                }
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = {
                        openingBalance = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(context.getString(R.string.opening_balance_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
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
                        error = context.getString(R.string.account_name_required)
                        return@Button
                    }
                    val openingCents = if (openingBalance.isBlank()) {
                        0L
                    } else if (openingBalance.trim() == "0" || openingBalance.trim() == "0.00") {
                        0L
                    } else {
                        Formatting.parseAmountToCents(openingBalance, Formatting.locale(context))
                            ?: run {
                                error = context.getString(R.string.invalid_amount)
                                return@Button
                            }
                    }
                    onSave(
                        normalizedName,
                        if (typeIndex == 1) "liability" else "asset",
                        openingCents,
                    )
                },
            ) {
                Text(context.getString(R.string.save_action))
            }
        },
    )
}

@Composable
private fun AccountPickerDialog(
    sourceName: String,
    accounts: List<LedgerAccountRecord>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.map_account_title, sourceName)) },
        text = {
            if (accounts.isEmpty()) {
                Text(context.getString(R.string.manual_account_none))
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(accounts, key = { it.id }) { account ->
                        OutlinedButton(
                            onClick = { onSelect(account.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(account.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel_action))
            }
        },
    )
}

@Composable
private fun SourceProfileCard(
    source: SourceProfileRecord,
    onEnabledChange: (Boolean) -> Unit,
    onMapAccount: () -> Unit,
    onImport: () -> Unit,
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = context.getString(
                            R.string.source_account_line,
                            source.accountName ?: context.getString(R.string.source_account_none),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = source.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            if (source.lastImportStatus != null) {
                val importTime = source.lastImportAt
                    ?.let { Formatting.timestamp(context, it) }
                    ?: "-"
                Text(
                    text = context.getString(
                        if (source.lastImportStatus == "success") {
                            R.string.source_last_import_success
                        } else {
                            R.string.source_last_import_failed
                        },
                        importTime,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onMapAccount,
                    modifier = Modifier.weight(1f),
                    enabled = source.enabled,
                ) {
                    Text(context.getString(R.string.source_map_account_action))
                }
                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    enabled = source.enabled,
                ) {
                    Text(context.getString(R.string.source_import_action))
                }
            }
        }
    }
}
