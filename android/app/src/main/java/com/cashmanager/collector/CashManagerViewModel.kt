package com.cashmanager.collector

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashmanager.collector.importing.StatementImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CashManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransactionDatabase(application)
    private val statementImporter = StatementImporter(database)

    private val _summary = MutableStateFlow(SummaryUiState())
    val summary = _summary.asStateFlow()

    private val _transactions = MutableStateFlow(TransactionsUiState())
    val transactions = _transactions.asStateFlow()

    private val _sources = MutableStateFlow(SourcesUiState())
    val sources = _sources.asStateFlow()

    private val _calendarTransactions = MutableStateFlow(CalendarTransactionsUiState())
    val calendarTransactions = _calendarTransactions.asStateFlow()

    private val _savedFilters = MutableStateFlow(SavedFiltersUiState())
    val savedFilters = _savedFilters.asStateFlow()

    private val _accounts = MutableStateFlow(AccountsUiState())
    val accounts = _accounts.asStateFlow()

    private val _categoryBreakdown = MutableStateFlow(CategoryBreakdownUiState())
    val categoryBreakdown = _categoryBreakdown.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 2)
    val events = _events.asSharedFlow()

    private var currentFilter = TransactionFilter()
    private var summaryJob: Job? = null
    private var transactionsJob: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        loadSummary()
        loadTransactions(currentFilter)
        loadSources()
        loadAccounts()
        loadCategoryBreakdown()
        loadCalendarTransactions()
        loadSavedFilters()
    }

    fun applyFilter(filter: TransactionFilter) {
        currentFilter = filter
        loadTransactions(filter, showLoading = true)
    }

    fun resetFilters() {
        currentFilter = TransactionFilter()
        loadTransactions(currentFilter, showLoading = true)
    }

    fun addTransaction(
        date: String,
        amountCents: Long,
        kind: String,
        category: String?,
        note: String?,
        accountId: Long?,
    ) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    database.insert(date, amountCents, kind, category, note, accountId)
                }
            }.onSuccess {
                _events.emit(UiEvent.Message(R.string.save_success))
                refreshAll()
            }.onFailure {
                _events.emit(UiEvent.Message(R.string.save_failed))
            }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.delete(id)
            }
            refreshAll()
        }
    }

    fun setSourceEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.setSourceEnabled(id, enabled)
            }
            loadSources()
        }
    }

    fun setSourceAccount(sourceId: Long, accountId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.setSourceAccount(sourceId, accountId)
            }
            loadSources()
        }
    }

    fun addAccount(name: String, type: String, openingBalanceCents: Long) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    database.insertLedgerAccount(name, type, openingBalanceCents)
                }
            }.onSuccess {
                _events.emit(UiEvent.Message(R.string.account_created))
                loadAccounts()
                loadSources()
            }.onFailure {
                _events.emit(UiEvent.Message(R.string.account_create_failed))
            }
        }
    }

    fun addCategoryRule(name: String, keywords: String, kind: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    database.insertCategoryRule(name, keywords, kind)
                }
            }.onSuccess {
                _events.emit(UiEvent.Message(R.string.category_rule_added))
                loadCategoryBreakdown()
            }.onFailure {
                _events.emit(UiEvent.Message(R.string.category_rule_failed))
            }
        }
    }

    fun deleteCategoryRule(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.deleteCategoryRule(id)
            }
            loadCategoryBreakdown()
        }
    }

    fun addSavedFilter(category: String, name: String, value: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.insertSavedFilter(category, name, value)
            }
            loadSavedFilters()
        }
    }

    fun deleteSavedFilter(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.deleteSavedFilter(id)
            }
            loadSavedFilters()
        }
    }

    fun importStatement(sourceId: Long, uri: Uri) {
        viewModelScope.launch {
            _events.emit(UiEvent.TextMessage(getApplication<Application>().getString(R.string.import_in_progress)))
            val report = withContext(Dispatchers.IO) {
                statementImporter.import(getApplication(), sourceId, uri)
            }
            _events.emit(UiEvent.TextMessage(report.message))
            refreshAll()
        }
    }

    private fun loadSummary() {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            val (expense, income) = withContext(Dispatchers.IO) {
                database.totals()
            }
            _summary.value = SummaryUiState(
                expenseCents = expense,
                incomeCents = income,
            )
        }
    }

    private fun loadTransactions(
        filter: TransactionFilter,
        showLoading: Boolean = false,
    ) {
        _transactions.update {
            it.copy(
                filter = filter,
                loading = showLoading || it.loading,
            )
        }
        transactionsJob?.cancel()
        transactionsJob = viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                database.list(filter)
            }
            _transactions.update {
                it.copy(
                    items = items,
                    filter = filter,
                    loading = false,
                )
            }
        }
    }

    private fun loadSources() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                database.listSourceProfiles()
            }
            _sources.value = SourcesUiState(
                items = items,
                loading = false,
            )
        }
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                database.listLedgerAccounts()
            }
            _accounts.value = AccountsUiState(
                items = items,
                loading = false,
            )
        }
    }

    private fun loadCategoryBreakdown() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                CategoryBreakdownUiState(
                    expenseItems = database.categoryTotals("expense"),
                    incomeItems = database.categoryTotals("income"),
                    rules = database.listCategoryRules(),
                    loading = false,
                )
            }
            _categoryBreakdown.value = state
        }
    }

    private fun loadCalendarTransactions() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                database.list(TransactionFilter())
            }
            _calendarTransactions.value = CalendarTransactionsUiState(
                items = items,
                loading = false,
            )
        }
    }

    private fun loadSavedFilters() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                database.listSavedFilters()
            }
            _savedFilters.value = SavedFiltersUiState(items = items)
        }
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }
}
