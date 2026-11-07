package com.cashmanager.collector.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cashmanager.collector.Formatting
import com.cashmanager.collector.LedgerAccountRecord
import com.cashmanager.collector.R
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTransactionSheet(
    accounts: List<LedgerAccountRecord>,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String?, String?, Long?) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var amount by rememberSaveable { mutableStateOf("") }
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    var date by rememberSaveable { mutableStateOf(Formatting.date(context, LocalDate.now().toString())) }
    var category by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var selectedAccountId by rememberSaveable {
        mutableStateOf(accounts.firstOrNull()?.id)
    }
    var amountError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }

    fun closeWithAnimation(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    fun save() {
        val amountCents = Formatting.parseAmountToCents(
            amount,
            Formatting.locale(context),
        )
        if (amountCents == null) {
            amountError = context.getString(R.string.invalid_amount)
            return
        }
        amountError = null

        val normalizedDate = normalizeFilterDate(date)
        if (normalizedDate == null) {
            dateError = context.getString(R.string.invalid_date)
            return
        }
        dateError = null

        closeWithAnimation {
            onSave(
                normalizedDate,
                amountCents,
                if (kindIndex == 1) "income" else "expense",
                category.trim().takeIf { it.isNotEmpty() },
                note.trim().takeIf { it.isNotEmpty() },
                selectedAccountId,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.padding(top = 48.dp),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = context.getString(R.string.add_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it
                    amountError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(context.getString(R.string.amount_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kindIndex == 0,
                    onClick = { kindIndex = 0 },
                    label = { Text(context.getString(R.string.type_expense)) },
                )
                FilterChip(
                    selected = kindIndex == 1,
                    onClick = { kindIndex = 1 },
                    label = { Text(context.getString(R.string.type_income)) },
                )
            }

            OutlinedTextField(
                value = date,
                onValueChange = {
                    date = it
                    dateError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(context.getString(R.string.date_hint)) },
                singleLine = true,
                isError = dateError != null,
                supportingText = dateError?.let { { Text(it) } },
            )
            Text(
                text = context.getString(R.string.manual_account_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (accounts.isEmpty()) {
                Text(
                    text = context.getString(R.string.manual_account_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = selectedAccountId == account.id,
                            onClick = { selectedAccountId = account.id },
                            label = { Text(account.name) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(context.getString(R.string.optional_category_hint)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(context.getString(R.string.optional_note_hint)) },
                minLines = 2,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { closeWithAnimation(onDismiss) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(context.getString(R.string.cancel_action))
                }
                Button(
                    onClick = ::save,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(context.getString(R.string.save_transaction))
                }
            }
        }
    }
}
