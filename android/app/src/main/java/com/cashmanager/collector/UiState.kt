package com.cashmanager.collector

data class SummaryUiState(
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
)

data class TransactionsUiState(
    val items: List<TransactionRecord> = emptyList(),
    val filter: TransactionFilter = TransactionFilter(),
    val loading: Boolean = true,
)

data class SourcesUiState(
    val items: List<SourceProfileRecord> = emptyList(),
    val loading: Boolean = true,
)

data class CalendarTransactionsUiState(
    val items: List<TransactionRecord> = emptyList(),
    val loading: Boolean = true,
)

data class SavedFiltersUiState(
    val items: List<SavedFilterRecord> = emptyList(),
)

data class AccountsUiState(
    val items: List<LedgerAccountRecord> = emptyList(),
    val loading: Boolean = true,
)

data class CategoryBreakdownUiState(
    val expenseItems: List<CategoryTotal> = emptyList(),
    val incomeItems: List<CategoryTotal> = emptyList(),
    val rules: List<CategoryRuleRecord> = emptyList(),
    val loading: Boolean = true,
)

sealed interface UiEvent {
    data class Message(val messageRes: Int) : UiEvent
    data class TextMessage(val message: String) : UiEvent
}
