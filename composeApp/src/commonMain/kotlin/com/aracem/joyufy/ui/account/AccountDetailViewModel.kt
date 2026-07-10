package com.aracem.joyufy.ui.account

import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
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

sealed interface InvestmentListItem {
    data class Snapshot(val snapshot: InvestmentSnapshot) : InvestmentListItem
    data class Tx(val transaction: Transaction) : InvestmentListItem
}

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val account: Account? = null,
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val snapshots: List<InvestmentSnapshot> = emptyList(),
    val investmentFeed: List<InvestmentListItem> = emptyList(),
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

class AccountDetailViewModel(
    private val accountId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()

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
                        val balance = snapshots.firstOrNull()?.totalValue
                            ?: transactionRepository.getAccountBalance(accountId)
                        _uiState.value = _uiState.value.copy(
                            balance = balance,
                            snapshots = snapshots,
                            investmentFeed = buildInvestmentFeed(snapshots, transactions),
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
    ): List<InvestmentListItem> {
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
            result.add(InvestmentListItem.Snapshot(snapshot))
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

    fun addSnapshot(totalValue: Double, weekDate: Long) {
        scope.launch {
            snapshotRepository.insertSnapshot(
                accountId = accountId,
                totalValue = totalValue,
                weekDate = weekDate,
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

    fun updateSnapshot(id: Long, totalValue: Double, weekDate: Long) {
        scope.launch { snapshotRepository.updateSnapshot(id, totalValue, weekDate) }
    }

    fun deleteTransaction(id: Long) {
        scope.launch {
            val tx = transactionRepository.getTransactionById(id)
            transactionRepository.deleteTransaction(id)
            if (tx?.relatedAccountId != null) {
                val sister = transactionRepository.getRelatedTransfer(
                    relatedAccountId = accountId,
                    originAccountId = tx.relatedAccountId,
                    amount = tx.amount,
                    type = tx.oppositeTransferLegType(),
                    date = tx.date,
                )
                if (sister != null) transactionRepository.deleteTransaction(sister.id)
            }
        }
    }

    fun deleteSnapshot(id: Long) {
        scope.launch { snapshotRepository.deleteSnapshot(id) }
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
