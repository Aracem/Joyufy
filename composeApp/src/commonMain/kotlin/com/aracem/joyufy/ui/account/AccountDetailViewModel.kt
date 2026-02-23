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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

data class SingleAccountPoint(
    val weekDate: Long,
    val balance: Double,
)

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val account: Account? = null,
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val snapshots: List<InvestmentSnapshot> = emptyList(),
    val allAccounts: List<Account> = emptyList(),
    val accountHistory: List<SingleAccountPoint> = emptyList(),
    val chartRange: ChartRange = ChartRange.ONE_YEAR,
    val periodChange: Double? = null,
    val periodChangePct: Double? = null,
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

    private val _chartRange = MutableStateFlow(ChartRange.ONE_YEAR)

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

            // All account types observe transactions
            launch {
                transactionRepository.observeTransactionsForAccount(accountId).collect { txns ->
                    val balance = calculateBalance(account.type)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        balance = balance,
                        transactions = txns,
                    )
                }
            }

            // Observe history based on account type
            if (account.type == AccountType.INVESTMENT) {
                launch {
                    combine(
                        snapshotRepository.observeSnapshotsForAccount(accountId),
                        _chartRange,
                    ) { snapshots, range ->
                        val balance = snapshots.firstOrNull()?.totalValue
                            ?: transactionRepository.getAccountBalance(accountId)
                        _uiState.value = _uiState.value.copy(
                            balance = balance,
                            snapshots = snapshots,
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
                        _chartRange,
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

    private fun buildInvestmentHistory(
        snapshots: List<InvestmentSnapshot>,
        range: ChartRange,
    ): List<SingleAccountPoint> {
        if (snapshots.isEmpty()) return emptyList()
        val millisInWeek = 7 * 86_400_000L
        val now = currentWeekStartMillis()
        val weekStarts = weekStartsForRange(range, now, millisInWeek)

        val points = weekStarts.map { weekStart ->
            val weekEnd = weekStart + millisInWeek - 1
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
        val millisInWeek = 7 * 86_400_000L
        val now = currentWeekStartMillis()
        val weekStarts = weekStartsForRange(range, now, millisInWeek)

        val points = weekStarts.map { weekStart ->
            val weekEnd = weekStart + millisInWeek - 1
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

    private fun weekStartsForRange(range: ChartRange, now: Long, millisInWeek: Long): List<Long> =
        when {
            range == ChartRange.YTD -> {
                val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val jan1Ms = LocalDate(local.year, 1, 1)
                    .atStartOfDayIn(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()
                generateSequence(jan1Ms) { it + millisInWeek }
                    .takeWhile { it <= now }
                    .toList()
            }
            range.weeks != null -> (range.weeks downTo 0).map { now - it * millisInWeek }
            else -> (260 downTo 0).map { now - it * millisInWeek }
        }

    private suspend fun calculateBalance(type: AccountType): Double =
        transactionRepository.getAccountBalance(accountId)

    fun setChartRange(range: ChartRange) {
        _uiState.value = _uiState.value.copy(chartRange = range)
        _chartRange.value = range
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
            transactionRepository.updateTransaction(id, type, amount, category, description, relatedAccountId, date)
        }
    }

    fun updateSnapshot(id: Long, totalValue: Double, weekDate: Long) {
        scope.launch { snapshotRepository.updateSnapshot(id, totalValue, weekDate) }
    }

    fun deleteTransaction(id: Long) {
        scope.launch { transactionRepository.deleteTransaction(id) }
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

    private fun currentWeekStartMillis(): Long {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = local.dayOfWeek.ordinal
        val millisInDay = 86_400_000L
        return (now.toEpochMilliseconds() / millisInDay - dayOfWeek) * millisInDay
    }
}
