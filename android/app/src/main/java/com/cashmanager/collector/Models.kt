package com.cashmanager.collector

data class TransactionRecord(
    val id: Long,
    val date: String,
    val amountCents: Long,
    val kind: String,
    val category: String?,
    val merchant: String?,
    val note: String?,
    val sourceType: String,
    val sourceApp: String?,
    val paymentMethod: String?,
    val sourceTxnId: String?,
)

data class TransactionFilter(
    val startDate: String? = null,
    val endDate: String? = null,
    val kind: String? = null,
    val sourceType: String? = null,
    val category: String? = null,
    val keywords: List<String> = emptyList(),
)

data class LedgerAccountRecord(
    val id: Long,
    val name: String,
    val type: String,
    val openingBalanceCents: Long,
    val currentBalanceCents: Long,
)

data class CategoryRuleRecord(
    val id: Long,
    val name: String,
    val keywords: String,
    val kind: String,
)

data class CategoryTotal(
    val category: String,
    val amountCents: Long,
)

data class SourceProfileRecord(
    val id: Long,
    val provider: String,
    val displayName: String,
    val channel: String,
    val accountId: Long?,
    val accountName: String?,
    val enabled: Boolean,
    val lastImportStatus: String?,
    val lastImportAt: Long?,
)

data class SavedFilterRecord(
    val id: Long,
    val category: String,
    val name: String,
    val value: String,
)
