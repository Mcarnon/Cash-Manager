package com.cashmanager.collector.importing

data class ParsedStatementRow(
    val date: String,
    val amountCents: Long,
    val amountRaw: String,
    val kind: String,
    val category: String?,
    val merchant: String?,
    val note: String?,
    val paymentMethod: String?,
    val externalTxnId: String?,
    val rawRow: String,
)

data class ParsedStatement(
    val rows: List<ParsedStatementRow>,
    val skippedRows: Int,
)

data class ImportReport(
    val accepted: Boolean,
    val message: String,
    val imported: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val neutral: Int = 0,
)

class StatementParseException(message: String) : Exception(message)
