package com.cashmanager.collector

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cashmanager.collector.importing.ImportReport
import com.cashmanager.collector.importing.ParsedStatement
import java.security.MessageDigest

class TransactionDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext = context.applicationContext

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
        seedDefaultAccountsAndSources(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_transactions_date_id " +
                "ON transactions(date DESC, id DESC)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_transactions_account_id " +
                "ON transactions(account_id)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 8) {
            migrateToSimplifiedSchema(db)
        }
        if (oldVersion < 9) {
            createCategoryRulesTable(db)
        }
        if (oldVersion < 10) {
            createSavedFiltersTable(db)
        }
    }

    
    private fun migrateToSimplifiedSchema(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            val hasTransactions = tableExists(db, "transactions")
            val hasSources = tableExists(db, "source_profiles")
            val hasAccounts = tableExists(db, "ledger_accounts")

            if (hasTransactions) db.execSQL("ALTER TABLE transactions RENAME TO transactions_legacy")
            if (hasSources) db.execSQL("ALTER TABLE source_profiles RENAME TO source_profiles_legacy")
            if (hasAccounts) db.execSQL("ALTER TABLE ledger_accounts RENAME TO ledger_accounts_legacy")
            db.execSQL("DROP INDEX IF EXISTS idx_transactions_profile_txn")
            db.execSQL("DROP INDEX IF EXISTS idx_transactions_source_external")

            db.execSQL("DROP TABLE IF EXISTS transaction_candidates")
            db.execSQL("DROP TABLE IF EXISTS raw_evidence")
            db.execSQL("DROP TABLE IF EXISTS import_batches")
            db.execSQL("DROP TABLE IF EXISTS audit_logs")
            db.execSQL("DROP TABLE IF EXISTS detection_events")

            createSchema(db)

            if (hasAccounts) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO ledger_accounts(
                        id, name, type, opening_balance_cents, created_at
                    )
                    SELECT id, name, type, opening_balance_cents, created_at
                    FROM ledger_accounts_legacy
                    """.trimIndent(),
                )
            }
            if (hasSources) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO source_profiles(
                        id, provider, display_name, channel, account_id, enabled,
                        last_import_status, last_import_at, created_at
                    )
                    SELECT id, provider, display_name, channel, account_id, enabled,
                           last_import_status, last_import_at, created_at
                    FROM source_profiles_legacy
                    """.trimIndent(),
                )
            }
            if (hasTransactions) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO transactions(
                        id, date, amount_cents, kind, category, merchant, note,
                        source_type, source_app, external_id, created_at,
                        source_profile_id, source_txn_id, amount_raw, payment_method,
                        account_id
                    )
                    SELECT id, date, amount_cents, kind, category, merchant, note,
                           source_type, source_app, external_id, created_at,
                           source_profile_id, source_txn_id, amount_raw, payment_method,
                           account_id
                    FROM transactions_legacy
                    """.trimIndent(),
                )
            }

            db.execSQL("DELETE FROM source_profiles WHERE channel <> 'official_import'")
            db.execSQL("DROP TABLE IF EXISTS transactions_legacy")
            db.execSQL("DROP TABLE IF EXISTS source_profiles_legacy")
            db.execSQL("DROP TABLE IF EXISTS ledger_accounts_legacy")
            seedDefaultAccountsAndSources(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ledger_accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                type TEXT NOT NULL DEFAULT 'asset',
                opening_balance_cents INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS source_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                provider TEXT NOT NULL,
                display_name TEXT NOT NULL,
                channel TEXT NOT NULL DEFAULT 'official_import',
                account_id INTEGER,
                enabled INTEGER NOT NULL DEFAULT 1,
                last_import_status TEXT,
                last_import_at INTEGER,
                created_at INTEGER NOT NULL,
                UNIQUE(provider, channel, display_name)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
                kind TEXT NOT NULL CHECK (kind IN ('expense', 'income', 'neutral')),
                category TEXT,
                merchant TEXT,
                note TEXT,
                source_type TEXT NOT NULL DEFAULT 'manual',
                source_app TEXT,
                external_id TEXT,
                created_at INTEGER NOT NULL,
                source_profile_id INTEGER,
                source_txn_id TEXT,
                amount_raw TEXT,
                payment_method TEXT,
                account_id INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_profile_txn
            ON transactions(source_profile_id, source_txn_id)
            WHERE source_profile_id IS NOT NULL AND source_txn_id IS NOT NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_source_external
            ON transactions(source_type, external_id)
            WHERE external_id IS NOT NULL
            """.trimIndent(),
        )
        createCategoryRulesTable(db)
        createSavedFiltersTable(db)
    }

    private fun createSavedFiltersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_filters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                name TEXT NOT NULL,
                value TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createCategoryRulesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS category_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                keywords TEXT NOT NULL,
                kind TEXT NOT NULL DEFAULT 'expense',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun seedDefaultAccountsAndSources(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val accounts = listOf(
            appContext.getString(R.string.default_account_cash),
            appContext.getString(R.string.default_account_wechat),
            appContext.getString(R.string.default_account_alipay),
            appContext.getString(R.string.default_account_bank),
        )
        accounts.forEach { name ->
            db.execSQL(
                "INSERT OR IGNORE INTO ledger_accounts(name, type, created_at) VALUES(?, 'asset', ?)",
                arrayOf(name, now),
            )
        }

        val accountIds = mutableMapOf<String, Long>()
        db.rawQuery("SELECT id, name FROM ledger_accounts", null).use { cursor ->
            while (cursor.moveToNext()) {
                accountIds[cursor.getString(1)] = cursor.getLong(0)
            }
        }

        fun insertSource(provider: String, displayName: String, accountName: String) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO source_profiles(
                    provider, display_name, channel, account_id, enabled, created_at
                ) VALUES(?, ?, 'official_import', ?, 1, ?)
                """.trimIndent(),
                arrayOf(provider, displayName, accountIds[accountName], now),
            )
        }

        insertSource(
            "wechat",
            appContext.getString(R.string.default_source_wechat),
            appContext.getString(R.string.default_account_wechat),
        )
        insertSource(
            "alipay",
            appContext.getString(R.string.default_source_alipay),
            appContext.getString(R.string.default_account_alipay),
        )
        insertSource(
            "bank",
            appContext.getString(R.string.default_source_bank),
            appContext.getString(R.string.default_account_bank),
        )
    }

    fun insert(
        date: String,
        amountCents: Long,
        kind: String,
        category: String?,
        note: String?,
        accountId: Long? = null,
    ): Long {
        val values = ContentValues().apply {
            put("date", date)
            put("amount_cents", amountCents)
            put("kind", kind)
            put("category", category)
            putNull("merchant")
            put("note", note)
            put("source_type", "manual")
            putNull("source_app")
            putNull("external_id")
            put("created_at", System.currentTimeMillis())
            if (accountId == null) putNull("account_id") else put("account_id", accountId)
        }
        return writableDatabase.insertOrThrow("transactions", null, values)
    }

    fun listLedgerAccounts(): List<LedgerAccountRecord> {
        val items = mutableListOf<LedgerAccountRecord>()
        readableDatabase.rawQuery(
            """
            SELECT a.id, a.name, a.type, a.opening_balance_cents,
                   a.opening_balance_cents + COALESCE(SUM(
                       CASE WHEN t.kind = 'income' THEN t.amount_cents
                            WHEN t.kind = 'expense' THEN -t.amount_cents
                            ELSE 0 END
                   ), 0) AS current_balance
            FROM ledger_accounts a
            LEFT JOIN transactions t ON t.account_id = a.id
            GROUP BY a.id, a.name, a.type, a.opening_balance_cents
            ORDER BY a.id
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += LedgerAccountRecord(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    type = cursor.getString(2),
                    openingBalanceCents = cursor.getLong(3),
                    currentBalanceCents = cursor.getLong(4),
                )
            }
        }
        return items
    }

    fun insertLedgerAccount(name: String, type: String, openingBalanceCents: Long): Long {
        return writableDatabase.insertOrThrow(
            "ledger_accounts",
            null,
            ContentValues().apply {
                put("name", name)
                put("type", type)
                put("opening_balance_cents", openingBalanceCents)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun setSourceAccount(sourceId: Long, accountId: Long) {
        writableDatabase.execSQL(
            "UPDATE source_profiles SET account_id = ? WHERE id = ?",
            arrayOf(accountId, sourceId),
        )
    }

    fun listSourceProfiles(): List<SourceProfileRecord> {
        val items = mutableListOf<SourceProfileRecord>()
        readableDatabase.rawQuery(
            """
            SELECT s.id, s.provider, s.display_name, s.channel, s.account_id, a.name,
                   s.enabled, s.last_import_status, s.last_import_at
            FROM source_profiles s
            LEFT JOIN ledger_accounts a ON a.id = s.account_id
            WHERE s.channel = 'official_import'
            ORDER BY CASE s.provider
                WHEN 'wechat' THEN 0
                WHEN 'alipay' THEN 1
                WHEN 'bank' THEN 2
                ELSE 3 END, s.id
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += SourceProfileRecord(
                    id = cursor.getLong(0),
                    provider = cursor.getString(1),
                    displayName = cursor.getString(2),
                    channel = cursor.getString(3),
                    accountId = cursor.getLong(4).takeIf { !cursor.isNull(4) },
                    accountName = cursor.getString(5),
                    enabled = cursor.getInt(6) != 0,
                    lastImportStatus = cursor.getString(7),
                    lastImportAt = cursor.getLong(8).takeIf { !cursor.isNull(8) },
                )
            }
        }
        return items
    }

    fun findSourceProfile(id: Long): SourceProfileRecord? {
        return findSourceProfileById(readableDatabase, id)
    }

    fun setSourceEnabled(id: Long, enabled: Boolean) {
        writableDatabase.execSQL(
            "UPDATE source_profiles SET enabled = ? WHERE id = ?",
            arrayOf(if (enabled) 1 else 0, id),
        )
    }

    fun listCategoryRules(): List<CategoryRuleRecord> {
        val items = mutableListOf<CategoryRuleRecord>()
        readableDatabase.query(
            "category_rules",
            arrayOf("id", "name", "keywords", "kind"),
            null,
            null,
            null,
            null,
            "id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += CategoryRuleRecord(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    keywords = cursor.getString(2),
                    kind = cursor.getString(3),
                )
            }
        }
        return items
    }

    fun insertCategoryRule(name: String, keywords: String, kind: String): Long {
        return writableDatabase.insertOrThrow(
            "category_rules",
            null,
            ContentValues().apply {
                put("name", name)
                put("keywords", keywords)
                put("kind", kind)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun deleteCategoryRule(id: Long) {
        writableDatabase.delete("category_rules", "id = ?", arrayOf(id.toString()))
    }

    fun listSavedFilters(): List<SavedFilterRecord> {
        val items = mutableListOf<SavedFilterRecord>()
        readableDatabase.query(
            "saved_filters",
            arrayOf("id", "category", "name", "value"),
            null,
            null,
            null,
            null,
            "id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += SavedFilterRecord(
                    id = cursor.getLong(0),
                    category = cursor.getString(1),
                    name = cursor.getString(2),
                    value = cursor.getString(3),
                )
            }
        }
        return items
    }

    fun insertSavedFilter(category: String, name: String, value: String): Long {
        return writableDatabase.insertOrThrow(
            "saved_filters",
            null,
            ContentValues().apply {
                put("category", category)
                put("name", name)
                put("value", value)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun deleteSavedFilter(id: Long) {
        writableDatabase.delete("saved_filters", "id = ?", arrayOf(id.toString()))
    }

    fun categoryTotals(kind: String): List<CategoryTotal> {
        val rules = listCategoryRules()
        val totals = linkedMapOf<String, Long>()
        readableDatabase.rawQuery(
            """
            SELECT kind, category, merchant, note, source_type, source_app, amount_cents
            FROM transactions
            WHERE kind = ?
            """.trimIndent(),
            arrayOf(kind),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val transactionKind = cursor.getString(0)
                val category = cursor.getString(1)
                val merchant = cursor.getString(2)
                val note = cursor.getString(3)
                val sourceType = cursor.getString(4)
                val sourceApp = cursor.getString(5)
                val amountCents = cursor.getLong(6)
                val haystack = listOfNotNull(
                    category,
                    merchant,
                    note,
                    sourceType,
                    sourceApp,
                ).joinToString(" ").lowercase()
                val matchedRule = rules.firstOrNull { rule ->
                    (rule.kind == "all" || rule.kind == transactionKind) &&
                        ruleKeywords(rule.keywords).any { keyword ->
                            haystack.contains(keyword.lowercase())
                        }
                }
                val label = matchedRule?.name
                    ?: category?.takeIf { it.isNotBlank() }
                    ?: ""
                totals[label] = (totals[label] ?: 0L) + amountCents
            }
        }
        return totals
            .map { CategoryTotal(it.key, it.value) }
            .sortedByDescending { it.amountCents }
    }

    private fun ruleKeywords(value: String): List<String> {
        return value.split(Regex("[\\s\\u3000,，、;；|]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun markImportFailed(sourceId: Long) {
        writableDatabase.execSQL(
            "UPDATE source_profiles SET last_import_status = 'failed', last_import_at = ? WHERE id = ?",
            arrayOf(System.currentTimeMillis(), sourceId),
        )
    }

    
    fun saveImportedStatement(
        sourceId: Long,
        fileName: String,
        statement: ParsedStatement,
    ): ImportReport {
        val database = writableDatabase
        val now = System.currentTimeMillis()
        database.beginTransaction()
        return try {
            val profile = findSourceProfileById(database, sourceId)
                ?: run {
                    database.setTransactionSuccessful()
                    return ImportReport(false, appContext.getString(R.string.import_source_missing))
                }

            var inserted = 0
            var insertedNeutral = 0
            var duplicates = 0
            for (row in statement.rows) {
                val rowHash = sha256("${profile.provider}|${row.externalTxnId.orEmpty()}|${row.rawRow}")
                val sourceTxnId = row.externalTxnId ?: "row:$rowHash"
                if (transactionExists(database, profile.id, sourceTxnId)) {
                    duplicates++
                    continue
                }

                val values = ContentValues().apply {
                    put("date", row.date)
                    put("amount_cents", row.amountCents)
                    put("kind", row.kind)
                    put("category", row.category)
                    put("merchant", row.merchant)
                    put("note", row.note)
                    put("source_type", profile.provider)
                    put("source_app", fileName)
                    put("external_id", sourceTxnId)
                    put("created_at", now)
                    put("source_profile_id", profile.id)
                    put("source_txn_id", sourceTxnId)
                    put("amount_raw", row.amountRaw)
                    put("payment_method", row.paymentMethod)
                    put("account_id", profile.accountId)
                }
                val id = database.insertWithOnConflict(
                    "transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE,
                )
                if (id != -1L) {
                    inserted++
                    if (row.kind == "neutral") insertedNeutral++
                } else {
                    duplicates++
                }
            }

            database.execSQL(
                "UPDATE source_profiles SET last_import_status = 'success', last_import_at = ? WHERE id = ?",
                arrayOf(now, sourceId),
            )
            database.setTransactionSuccessful()
            ImportReport(
                accepted = true,
                message = appContext.getString(
                    R.string.import_complete,
                    inserted,
                    insertedNeutral,
                    duplicates,
                    statement.skippedRows,
                ),
                imported = inserted,
                duplicates = duplicates,
                skipped = statement.skippedRows,
                neutral = insertedNeutral,
            )
        } catch (error: Exception) {
            ImportReport(
                false,
                appContext.getString(
                    R.string.import_failed,
                    error.message ?: appContext.getString(R.string.import_file_unreadable),
                ),
            )
        } finally {
            database.endTransaction()
        }
    }

    fun list(filter: TransactionFilter = TransactionFilter()): List<TransactionRecord> {
        val query = buildFilterQuery(filter)
        val items = mutableListOf<TransactionRecord>()
        readableDatabase.query(
            "transactions",
            arrayOf(
                "id",
                "date",
                "amount_cents",
                "kind",
                "category",
                "merchant",
                "note",
                "source_type",
                "source_app",
                "payment_method",
                "source_txn_id",
            ),
            query.selection,
            query.selectionArgs,
            null,
            null,
            "date DESC, id DESC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += TransactionRecord(
                    id = cursor.getLong(0),
                    date = cursor.getString(1),
                    amountCents = cursor.getLong(2),
                    kind = cursor.getString(3),
                    category = cursor.getString(4),
                    merchant = cursor.getString(5),
                    note = cursor.getString(6),
                    sourceType = cursor.getString(7),
                    sourceApp = cursor.getString(8),
                    paymentMethod = cursor.getString(9),
                    sourceTxnId = cursor.getString(10),
                )
            }
        }
        return items
    }

    private data class FilterQuery(
        val selection: String?,
        val selectionArgs: Array<String>?,
    )

    private fun buildFilterQuery(filter: TransactionFilter): FilterQuery {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        filter.startDate?.let {
            clauses += "date >= ?"
            args += it
        }
        filter.endDate?.let {
            clauses += "date <= ?"
            args += it
        }
        filter.kind?.let {
            clauses += "kind = ?"
            args += it
        }
        filter.sourceType?.let {
            clauses += "source_type = ?"
            args += it
        }
        filter.category?.let {
            clauses += "category LIKE ?"
            args += "%$it%"
        }
        filter.keywords.forEach { keyword ->
            clauses += "(note LIKE ? OR category LIKE ? OR merchant LIKE ? OR source_type LIKE ? OR source_app LIKE ? OR date LIKE ?)"
            repeat(6) { args += "%$keyword%" }
        }

        return FilterQuery(
            selection = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            selectionArgs = args.takeIf { it.isNotEmpty() }?.toTypedArray(),
        )
    }

    fun delete(id: Long) {
        writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
    }

    fun totals(): Pair<Long, Long> {
        var expense = 0L
        var income = 0L
        readableDatabase.rawQuery(
            """
            SELECT
                COALESCE(SUM(CASE WHEN kind = 'expense' THEN amount_cents ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN kind = 'income' THEN amount_cents ELSE 0 END), 0)
            FROM transactions
            """.trimIndent(),
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                expense = cursor.getLong(0)
                income = cursor.getLong(1)
            }
        }
        return expense to income
    }

    private fun findSourceProfileById(
        database: SQLiteDatabase,
        id: Long,
    ): SourceProfileRecord? {
        database.rawQuery(
            """
            SELECT s.id, s.provider, s.display_name, s.channel, s.account_id, a.name,
                   s.enabled, s.last_import_status, s.last_import_at
            FROM source_profiles s
            LEFT JOIN ledger_accounts a ON a.id = s.account_id
            WHERE s.id = ? AND s.channel = 'official_import'
            LIMIT 1
            """.trimIndent(),
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return SourceProfileRecord(
                id = cursor.getLong(0),
                provider = cursor.getString(1),
                displayName = cursor.getString(2),
                channel = cursor.getString(3),
                accountId = cursor.getLong(4).takeIf { !cursor.isNull(4) },
                accountName = cursor.getString(5),
                enabled = cursor.getInt(6) != 0,
                lastImportStatus = cursor.getString(7),
                lastImportAt = cursor.getLong(8).takeIf { !cursor.isNull(8) },
            )
        }
    }

    private fun transactionExists(
        database: SQLiteDatabase,
        sourceProfileId: Long,
        sourceTxnId: String,
    ): Boolean {
        return database.rawQuery(
            """
            SELECT 1 FROM transactions
            WHERE source_profile_id = ? AND source_txn_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(sourceProfileId.toString(), sourceTxnId),
        ).use { it.moveToFirst() }
    }

    private fun tableExists(database: SQLiteDatabase, table: String): Boolean {
        return database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(table),
        ).use { it.moveToFirst() }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private const val DATABASE_NAME = "cash_manager.db"
        private const val DATABASE_VERSION = 10
    }
}
