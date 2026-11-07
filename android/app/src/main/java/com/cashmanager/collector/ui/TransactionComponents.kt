package com.cashmanager.collector.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cashmanager.collector.Formatting
import com.cashmanager.collector.R
import com.cashmanager.collector.TransactionRecord

@Composable
internal fun TransactionCard(
    item: TransactionRecord,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    val title = item.merchant ?: item.category ?: item.note
        ?: context.getString(
            when (item.kind) {
                "income" -> R.string.type_income
                "neutral" -> R.string.type_neutral
                else -> R.string.type_expense
            },
        )
    val amountColor = when (item.kind) {
        "income" -> MaterialTheme.colorScheme.secondary
        "neutral" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    val subtitle = buildString {
        append(Formatting.date(context, item.date))
        if (item.sourceType != "manual") {
            append("  ·  ")
            append(sourceLabel(context, item.sourceType))
        }
        if (!item.sourceApp.isNullOrBlank()) {
            append(" · ")
            append(item.sourceApp)
        }
        if (item.kind == "neutral") {
            append(" · ")
            append(context.getString(R.string.neutral_tag))
        }
    }
    val note = item.note?.takeIf {
        it.isNotBlank() && it != item.category && it != item.merchant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(amountColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (item.kind) {
                        "income" -> Icons.AutoMirrored.Outlined.TrendingUp
                        "neutral" -> Icons.AutoMirrored.Outlined.ReceiptLong
                        else -> Icons.AutoMirrored.Outlined.TrendingDown
                    },
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatting.signedAmount(context, item.amountCents, item.kind),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    maxLines = 1,
                )
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = context.getString(R.string.delete_action),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TransactionDetailsDialog(
    item: TransactionRecord,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.detail_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = Formatting.signedAmount(context, item.amountCents, item.kind),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (item.kind) {
                        "income" -> MaterialTheme.colorScheme.secondary
                        "neutral" -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    },
                )
                HorizontalDivider()
                DetailRow(context.getString(R.string.detail_date, Formatting.date(context, item.date)))
                DetailRow(
                    context.getString(
                        R.string.detail_type,
                        context.getString(
                            when (item.kind) {
                                "income" -> R.string.type_income
                                "neutral" -> R.string.type_neutral
                                else -> R.string.type_expense
                            },
                        ),
                    ),
                )
                DetailRow(context.getString(R.string.detail_source, sourceLabel(context, item.sourceType)))
                item.sourceApp?.let {
                    DetailRow(context.getString(R.string.detail_source_app, it))
                }
                item.paymentMethod?.let {
                    DetailRow(context.getString(R.string.detail_payment_method, it))
                }
                item.sourceTxnId?.let {
                    DetailRow(context.getString(R.string.detail_source_txn, it))
                }
                item.merchant?.let {
                    DetailRow(context.getString(R.string.detail_merchant, it))
                }
                item.category?.let {
                    DetailRow(context.getString(R.string.detail_category, it))
                }
                item.note?.let {
                    DetailRow(context.getString(R.string.detail_note, it))
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

@Composable
internal fun DetailRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
    )
}
