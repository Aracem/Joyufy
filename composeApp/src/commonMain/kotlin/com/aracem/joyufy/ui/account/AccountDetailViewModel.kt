package com.aracem.joyufy.ui.account

import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.domain.logic.hasManualInvestmentFlowAnnotations
import com.aracem.joyufy.domain.logic.withDerivedInvestmentFlows
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.dashboard.ChartRange
import com.aracem.joyufy.ui.dashboard.ChartRangePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.aracem.joyufy.ui.components.MILLIS_IN_WEEK
import com.aracem.joyufy.ui.components.currentWeekStartMillis
import com.aracem.joyufy.ui.components.weekStartsForRange
import com.aracem.joyufy.ui.components.weekStartsForYtd

data class SingleAccountPoint(
    val weekDate: Long,
    val balance: Double,
)

data class InvestmentPerformancePoint(
    val snapshot: InvestmentSnapshot,
    val previousValue: Double?,
    val valueChange: Double,
    val contributionAdjustedGain: Double,
    val marketPerformance: Double,
    val returnPct: Double?,
)

data class InvestmentPerformanceSummary(
    val deposits: Double,
    val withdrawals: Double,
    val fees: Double,
    val dividends: Double,
    val contributionAdjustedGain: Double,
    val marketPerformance: Double,
    val timeWeightedReturnPct: Double?,
)

sealed interface InvestmentListItem {
    data class Snapshot(
        val snapshot: InvestmentSnapshot,
        val performance: InvestmentPerformancePoint?,
    ) : InvestmentListItem
    data class Tx(val transaction: Transaction) : InvestmentListItem
}

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val account: Account? = null,
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val snapshots: List<InvestmentSnapshot> = emptyList(),
    val investmentFeed: List<InvestmentListItem> = emptyList(),
    val investmentPerformance: List<InvestmentPerformancePoint> = emptyList(),
    val investmentPerformanceSummary: InvestmentPerformanceSummary? = null,
    val hasInferredInvestmentFlows: Boolean = false,
    val allAccounts: List<Account> = emptyList(),
    val accountHistory: List<SingleAccountPoint> = emptyList(),
    val chartRange: ChartRange = ChartRangePreference.range.value,
    val periodChange: Double? = null,
    val periodChangePct: Double? = null,
    // Distinct category strings the user has used, ranked by frequency and
    // recency. Fed to AddTransactionDialog so real habits appear before the
    // static preset catalog.
    val customCategories: List<String> = emptyList(),
)

sealed interface AccountDetailEvent {
    data object Idle : AccountDetailEvent
    data class TransactionsDeleted(val count: Int) : AccountDetailEvent
    data class SnapshotDeleted(val count: Int) : AccountDetailEvent
    data class Restored(val count: Int) : AccountDetailEvent
    data class Error(val message: String) : AccountDetailEvent
}

class AccountDetailViewModel(
    private val accountId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()

    private val _event = MutableStateFlow<AccountDetailEvent>(AccountDetailEvent.Idle)
    val event: StateFlow<AccountDetailEvent> = _event.asStateFlow()

    private var lastDeletedTransactions: List<Transaction> = emptyList()
    private var lastDeletedSnapshot: InvestmentSnapshot? = null

    init {
        load()
    }

    private fun load() {
        scope.launch {
            val account = accountRepository.getAccountById(accountId) ?: return@launch
            _uiState.value = _uiState.value.copy(account = account)

            // Always observe all other accounts (for destination picker)
            launch {
                accountRepository.observeAccounts().collect { accounts ->
                    _uiState.value = _uiState.value.copy(
                        allAccounts = accounts.filter { it.id != accountId }
                    )
                }
            }

            // Distinct categories across all accounts, ranked by frequency and
            // recency. This includes preset labels too, so the dialog opens with
            // the user's real categories before the generic catalog.
            launch {
                transactionRepository.observeAllTransactions().collect { all ->
                    val out = all
                        .asSequence()
                        .mapNotNull { tx -> tx.category?.trim()?.takeIf { it.isNotEmpty() }?.let { it to tx.date } }
                        .groupBy { (label, _) -> label.lowercase() }
                        .map { (_, entries) ->
                            val label = entries.maxBy { it.second }.first
                            val count = entries.size
                            val lastUsed = entries.maxOf { it.second }
                            Triple(label, count, lastUsed)
                        }
                        .sortedWith(
                            compareByDescending<Triple<String, Int, Long>> { it.second }
                                .thenByDescending { it.third }
                                .thenBy { it.first.lowercase() },
                        )
                        .map { it.first }
                    _uiState.value = _uiState.value.copy(customCategories = out)
                }
            }

            // All account types observe transactions
            launch {
                transactionRepository.observeTransactionsForAccount(accountId).collect { txns ->
                    // For INVESTMENT accounts the balance comes from snapshots — don't overwrite it here
                    val newBalance = if (account.type != AccountType.INVESTMENT) {
                        calculateBalance(account.type)
                    } else {
                        _uiState.value.balance
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        balance = newBalance,
                        transactions = txns,
                    )
                }
            }

            // Observe history based on account type
            if (account.type == AccountType.INVESTMENT) {
                launch {
                    combine(
                        snapshotRepository.observeSnapshotsForAccount(accountId),
                        transactionRepository.observeTransactionsForAccount(accountId),
                        ChartRangePreference.range,
                    ) { snapshots, transactions, range ->
                        val performance = buildInvestmentPerformance(snapshots, transactions)
                        val balance = snapshots.firstOrNull()?.totalValue
                            ?: transactionRepository.getAccountBalance(accountId)
                        _uiState.value = _uiState.value.copy(
                            balance = balance,
                            snapshots = snapshots,
                            investmentFeed = buildInvestmentFeed(snapshots, transactions, performance),
                            investmentPerformance = performance,
                            investmentPerformanceSummary = buildInvestmentPerformanceSummary(performance),
                            hasInferredInvestmentFlows = hasInferredInvestmentFlows(snapshots, performance),
                        )
                        buildInvestmentHistory(snapshots, range)
                    }.collect { history ->
                        _uiState.value = _uiState.value.copy(
                            accountHistory = history,
                            periodChange = periodChange(history),
                            periodChangePct = periodChangePct(history),
                        )
                    }
                }
            } else {
                launch {
                    combine(
                        transactionRepository.observeTransactionsForAccount(accountId),
                        ChartRangePreference.range,
                    ) { transactions, range ->
                        buildBankCashHistory(transactions, range)
                    }.collect { history ->
                        _uiState.value = _uiState.value.copy(
                            accountHistory = history,
                            periodChange = periodChange(history),
                            periodChangePct = periodChangePct(history),
                        )
                    }
                }
            }
        }
    }

    private fun buildInvestmentFeed(
        snapshots: List<InvestmentSnapshot>,
        transactions: List<Transaction>,
        performance: List<InvestmentPerformancePoint>,
    ): List<InvestmentListItem> {
        val performanceBySnapshotId = performance.associateBy { it.snapshot.id }
        // Keep only the most recent snapshot per week in case of duplicate data in DB
        val sortedSnapshots = snapshots
            .sortedByDescending { it.weekDate }
            .distinctBy { it.weekDate }
        // Dedupe transactions by id — a defence against stray duplicates in the
        // emitted list (LazyColumn keys crash otherwise).
        val sortedTxns = transactions
            .distinctBy { it.id }
            .sortedByDescending { it.date }

        // No snapshots → all transactions go into a single flat list. Bailing
        // out here also avoids overflow on Long.MAX_VALUE + MILLIS_IN_WEEK,
        // which previously made every tx match every bucket and produced
        // duplicate keys.
        if (sortedSnapshots.isEmpty()) {
            return sortedTxns.map { InvestmentListItem.Tx(it) }
        }

        val result = mutableListOf<InvestmentListItem>()
        // Build week intervals: each snapshot owns [weekDate, weekDate + 7 days)
        // Transactions not covered by any snapshot go at the top (newer) or bottom (older).
        val newestWeekStart = sortedSnapshots.first().weekDate
        val oldestWeekStart = sortedSnapshots.last().weekDate

        // Transactions newer than the most recent snapshot's window
        sortedTxns
            .filter { it.date >= newestWeekStart + MILLIS_IN_WEEK }
            .forEach { result.add(InvestmentListItem.Tx(it)) }

        // Each snapshot owns transactions within [weekDate, weekDate + 7 days)
        sortedSnapshots.forEach { snapshot ->
            result.add(InvestmentListItem.Snapshot(snapshot, performanceBySnapshotId[snapshot.id]))
            val weekEnd = snapshot.weekDate + MILLIS_IN_WEEK
            sortedTxns
                .filter { it.date >= snapshot.weekDate && it.date < weekEnd }
                .forEach { result.add(InvestmentListItem.Tx(it)) }
        }

        // Transactions older than the oldest snapshot's window
        sortedTxns
            .filter { it.date < oldestWeekStart }
            .forEach { result.add(InvestmentListItem.Tx(it)) }

        return result
    }

    private fun buildInvestmentHistory(
        snapshots: List<InvestmentSnapshot>,
        range: ChartRange,
    ): List<SingleAccountPoint> {
        if (snapshots.isEmpty()) return emptyList()
        val now = currentWeekStartMillis()
        val weekStarts = if (range == ChartRange.YTD) weekStartsForYtd(now) else weekStartsForRange(range.weeks, now)

        val points = weekStarts.map { weekStart ->
            val weekEnd = weekStart + MILLIS_IN_WEEK - 1
            val balance = snapshots
                .filter { it.weekDate <= weekEnd }
                .maxByOrNull { it.weekDate }
                ?.totalValue ?: 0.0
            SingleAccountPoint(weekDate = weekStart, balance = balance)
        }

        val firstNonZero = points.indexOfFirst { it.balance != 0.0 }
        return if (firstNonZero >= 0) points.drop(firstNonZero) else emptyList()
    }

    private fun buildBankCashHistory(
        transactions: List<Transaction>,
        range: ChartRange,
    ): List<SingleAccountPoint> {
        if (transactions.isEmpty()) return emptyList()
        val now = currentWeekStartMillis()
        val weekStarts = if (range == ChartRange.YTD) weekStartsForYtd(now) else weekStartsForRange(range.weeks, now)

        val points = weekStarts.map { weekStart ->
            val weekEnd = weekStart + MILLIS_IN_WEEK - 1
            val balance = transactions
                .filter { it.date <= weekEnd }
                .sumOf { tx ->
                    if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
                }
            SingleAccountPoint(weekDate = weekStart, balance = balance)
        }

        val firstNonZero = points.indexOfFirst { it.balance != 0.0 }
        return if (firstNonZero >= 0) points.drop(firstNonZero) else emptyList()
    }

    private suspend fun calculateBalance(type: AccountType): Double =
        transactionRepository.getAccountBalance(accountId)

    fun setChartRange(range: ChartRange) {
        ChartRangePreference.set(range)
        _uiState.value = _uiState.value.copy(chartRange = range)
    }

    fun addTransaction(
        type: TransactionType,
        amount: Double,
        category: String?,
        description: String?,
        relatedAccountId: Long?,
        date: Long,
    ) {
        scope.launch {
            when {
                // TRANSFER: EXPENSE on origin, INCOME on destination
                type == TransactionType.TRANSFER && relatedAccountId != null -> {
                    transactionRepository.insertTransaction(
                        accountId = accountId,
                        type = TransactionType.EXPENSE,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = relatedAccountId,
                        date = date,
                    )
                    transactionRepository.insertTransaction(
                        accountId = relatedAccountId,
                        type = TransactionType.INCOME,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = accountId,
                        date = date,
                    )
                }
                else -> {
                    transactionRepository.insertTransaction(
                        accountId = accountId,
                        type = type,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = relatedAccountId,
                        date = date,
                    )
                }
            }
        }
    }

    fun addSnapshot(
        totalValue: Double,
        weekDate: Long,
        deposits: Double,
        withdrawals: Double,
        fees: Double,
        dividends: Double,
        note: String?,
    ) {
        scope.launch {
            snapshotRepository.insertSnapshot(
                accountId = accountId,
                totalValue = totalValue,
                weekDate = weekDate,
                deposits = deposits,
                withdrawals = withdrawals,
                fees = fees,
                dividends = dividends,
                note = note,
            )
        }
    }

    fun updateTransaction(
        id: Long,
        type: TransactionType,
        amount: Double,
        category: String?,
        description: String?,
        relatedAccountId: Long?,
        date: Long,
    ) {
        scope.launch {
            val original = transactionRepository.getTransactionById(id)
            val originalRelatedId = original?.relatedAccountId

            if (originalRelatedId != null) {
                // Original was a transfer — delete both legs and recreate
                val sister = transactionRepository.getRelatedTransfer(
                    relatedAccountId = accountId,
                    originAccountId = originalRelatedId,
                    amount = original.amount,
                    type = original.oppositeTransferLegType(),
                    date = original.date,
                )
                transactionRepository.deleteTransaction(id)
                if (sister != null) transactionRepository.deleteTransaction(sister.id)

                if (type == TransactionType.TRANSFER && relatedAccountId != null) {
                    // Still a transfer — recreate both legs
                    transactionRepository.insertTransaction(
                        accountId = accountId,
                        type = TransactionType.EXPENSE,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = relatedAccountId,
                        date = date,
                    )
                    transactionRepository.insertTransaction(
                        accountId = relatedAccountId,
                        type = TransactionType.INCOME,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = accountId,
                        date = date,
                    )
                } else {
                    // Changed from transfer to income/expense — single transaction
                    transactionRepository.insertTransaction(
                        accountId = accountId,
                        type = type,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = null,
                        date = date,
                    )
                }
            } else if (type == TransactionType.TRANSFER && relatedAccountId != null) {
                // Changed from income/expense to transfer — delete original, create two legs
                transactionRepository.deleteTransaction(id)
                transactionRepository.insertTransaction(
                    accountId = accountId,
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    category = category,
                    description = description,
                    relatedAccountId = relatedAccountId,
                    date = date,
                )
                transactionRepository.insertTransaction(
                    accountId = relatedAccountId,
                    type = TransactionType.INCOME,
                    amount = amount,
                    category = category,
                    description = description,
                    relatedAccountId = accountId,
                    date = date,
                )
            } else {
                // Simple income/expense update
                transactionRepository.updateTransaction(id, type, amount, category, description, null, date)
            }
        }
    }

    fun updateSnapshot(
        id: Long,
        totalValue: Double,
        weekDate: Long,
        deposits: Double,
        withdrawals: Double,
        fees: Double,
        dividends: Double,
        note: String?,
    ) {
        scope.launch {
            snapshotRepository.updateSnapshot(
                id = id,
                totalValue = totalValue,
                weekDate = weekDate,
                deposits = deposits,
                withdrawals = withdrawals,
                fees = fees,
                dividends = dividends,
                note = note,
            )
        }
    }

    fun deleteTransaction(id: Long) {
        scope.launch {
            runCatching {
                val tx = transactionRepository.getTransactionById(id) ?: return@runCatching emptyList<Transaction>()
                val deleted = linkedMapOf(tx.id to tx)
                if (tx.relatedAccountId != null) {
                    val sister = transactionRepository.getRelatedTransfer(
                        relatedAccountId = accountId,
                        originAccountId = tx.relatedAccountId,
                        amount = tx.amount,
                        type = tx.oppositeTransferLegType(),
                        date = tx.date,
                    )
                    if (sister != null) deleted[sister.id] = sister
                }
                deleted.keys.forEach { transactionRepository.deleteTransaction(it) }
                deleted.values.toList()
            }.onSuccess { deleted ->
                lastDeletedTransactions = deleted
                if (deleted.isNotEmpty()) _event.value = AccountDetailEvent.TransactionsDeleted(deleted.size)
            }.onFailure {
                _event.value = AccountDetailEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun deleteSnapshot(id: Long) {
        scope.launch {
            runCatching {
                val snapshot = _uiState.value.snapshots.firstOrNull { it.id == id }
                snapshotRepository.deleteSnapshot(id)
                snapshot
            }.onSuccess { snapshot ->
                lastDeletedSnapshot = snapshot
                if (snapshot != null) _event.value = AccountDetailEvent.SnapshotDeleted(1)
            }.onFailure {
                _event.value = AccountDetailEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun undoLastTransactionDelete() {
        val transactions = lastDeletedTransactions
        if (transactions.isEmpty()) return
        scope.launch {
            runCatching {
                transactions.sortedBy { it.id }.forEach { tx ->
                    transactionRepository.insertTransactionWithId(
                        id = tx.id,
                        accountId = tx.accountId,
                        type = tx.type,
                        amount = tx.amount,
                        category = tx.category,
                        description = tx.description,
                        relatedAccountId = tx.relatedAccountId,
                        date = tx.date,
                        reviewStatus = tx.reviewStatus,
                        importBatch = tx.importBatch,
                    )
                }
                transactions.size
            }.onSuccess { count ->
                lastDeletedTransactions = emptyList()
                _event.value = AccountDetailEvent.Restored(count)
            }.onFailure {
                _event.value = AccountDetailEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun undoLastSnapshotDelete() {
        val snapshot = lastDeletedSnapshot ?: return
        scope.launch {
            runCatching {
                snapshotRepository.insertSnapshotWithId(
                    id = snapshot.id,
                    accountId = snapshot.accountId,
                    totalValue = snapshot.totalValue,
                    weekDate = snapshot.weekDate,
                    deposits = snapshot.deposits,
                    withdrawals = snapshot.withdrawals,
                    fees = snapshot.fees,
                    dividends = snapshot.dividends,
                    note = snapshot.note,
                )
            }.onSuccess {
                lastDeletedSnapshot = null
                _event.value = AccountDetailEvent.Restored(1)
            }.onFailure {
                _event.value = AccountDetailEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun resetEvent() {
        _event.value = AccountDetailEvent.Idle
    }

    private fun periodChange(history: List<SingleAccountPoint>): Double? {
        val first = history.firstOrNull()?.balance ?: return null
        val last = history.lastOrNull()?.balance ?: return null
        return last - first
    }

    private fun periodChangePct(history: List<SingleAccountPoint>): Double? {
        val first = history.firstOrNull()?.balance?.takeIf { it != 0.0 } ?: return null
        val change = periodChange(history) ?: return null
        return (change / first) * 100.0
    }

    private fun Transaction.oppositeTransferLegType(): TransactionType =
        when (type) {
            TransactionType.INCOME -> TransactionType.EXPENSE
            TransactionType.EXPENSE,
            TransactionType.TRANSFER -> TransactionType.INCOME
        }

}

internal fun buildInvestmentPerformance(
    snapshots: List<InvestmentSnapshot>,
    transactions: List<Transaction> = emptyList(),
): List<InvestmentPerformancePoint> {
    val sorted = snapshots
        .sortedBy { it.weekDate }
        .distinctBy { it.weekDate }
        .map { it.withDerivedInvestmentFlows(transactions) }
    return sorted.mapIndexed { index, snapshot ->
        val previousValue = sorted.getOrNull(index - 1)?.totalValue
        val valueChange = previousValue?.let { snapshot.totalValue - it } ?: 0.0
        val contributionAdjustedGain = previousValue?.let {
            valueChange - snapshot.deposits + snapshot.withdrawals
        } ?: 0.0
        val marketPerformance = previousValue?.let {
            contributionAdjustedGain + snapshot.fees - snapshot.dividends
        } ?: 0.0
        val returnPct = previousValue
            ?.takeIf { it > 0.0 }
            ?.let { (contributionAdjustedGain / it) * 100.0 }
        InvestmentPerformancePoint(
            snapshot = snapshot,
            previousValue = previousValue,
            valueChange = valueChange,
            contributionAdjustedGain = contributionAdjustedGain,
            marketPerformance = marketPerformance,
            returnPct = returnPct,
        )
    }
}

private fun hasInferredInvestmentFlows(
    snapshots: List<InvestmentSnapshot>,
    performance: List<InvestmentPerformancePoint>,
): Boolean {
    val snapshotsById = snapshots.associateBy { it.id }
    return performance.any { point ->
        val original = snapshotsById[point.snapshot.id] ?: return@any false
        !original.hasManualInvestmentFlowAnnotations() &&
            (point.snapshot.deposits != 0.0 || point.snapshot.withdrawals != 0.0)
    }
}

internal fun buildInvestmentPerformanceSummary(
    points: List<InvestmentPerformancePoint>,
): InvestmentPerformanceSummary? {
    if (points.isEmpty()) return null
    val periods = points.filter { it.previousValue != null }
    val twrPct = periods
        .mapNotNull { it.returnPct?.let { pct -> 1.0 + pct / 100.0 } }
        .takeIf { it.isNotEmpty() }
        ?.fold(1.0) { acc, value -> acc * value }
        ?.let { (it - 1.0) * 100.0 }
    return InvestmentPerformanceSummary(
        deposits = points.sumOf { it.snapshot.deposits },
        withdrawals = points.sumOf { it.snapshot.withdrawals },
        fees = points.sumOf { it.snapshot.fees },
        dividends = points.sumOf { it.snapshot.dividends },
        contributionAdjustedGain = periods.sumOf { it.contributionAdjustedGain },
        marketPerformance = periods.sumOf { it.marketPerformance },
        timeWeightedReturnPct = twrPct,
    )
}
