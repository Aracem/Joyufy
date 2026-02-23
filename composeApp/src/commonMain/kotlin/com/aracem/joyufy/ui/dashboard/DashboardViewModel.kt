package com.aracem.joyufy.ui.dashboard

import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.data.repository.WealthRepository
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

data class AccountSummary(
    val account: Account,
    val balance: Double,
)

data class AccountPoint(
    val account: Account,
    val weekDate: Long,
    val balance: Double,
)

data class WealthPoint(
    val weekDate: Long,
    val totalWealth: Double,
    val byAccount: List<AccountPoint> = emptyList(),
)

enum class ChartMode { AREA, BARS }

enum class ChartRange(val weeks: Int?) {
    ONE_MONTH(4),
    THREE_MONTHS(13),
    SIX_MONTHS(26),
    YTD(null),   // desde el 1 de enero — calculado dinámicamente
    ONE_YEAR(52),
    ALL(null),
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalWealth: Double = 0.0,
    val wealthHistory: List<WealthPoint> = emptyList(),
    val accountSummaries: List<AccountSummary> = emptyList(),
    val accountsMissingSnapshot: List<Account> = emptyList(),
    val chartMode: ChartMode = ChartMode.AREA,
    val chartRange: ChartRange = ChartRange.ONE_YEAR,
    val periodChange: Double? = null,       // absolute change over selected range
    val periodChangePct: Double? = null,    // percentage change over selected range
    val hiddenAccountIds: Set<Long> = emptySet(),
    val showTotal: Boolean = true,
)

class DashboardViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
    private val wealthRepository: WealthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeBalances()
        observeWealthHistory()
        checkMissingSnapshots()
    }

    private fun observeBalances() {
        scope.launch {
            // Observe accounts list — when it changes, set up per-account flows
            accountRepository.observeAccounts()
                .flatMapLatest { accounts ->
                    if (accounts.isEmpty()) {
                        flowOf(emptyList<AccountSummary>())
                    } else {
                        // For each account create a Flow<Double> that reacts to its own data changes
                        val balanceFlows = accounts.map { account ->
                            when (account.type) {
                                AccountType.INVESTMENT ->
                                    combine(
                                        snapshotRepository.observeSnapshotsForAccount(account.id),
                                        transactionRepository.observeTransactionsForAccount(account.id),
                                    ) { snapshots, _ ->
                                        val balance = snapshots.firstOrNull()?.totalValue
                                            ?: transactionRepository.getAccountBalance(account.id)
                                        AccountSummary(account, balance)
                                    }
                                        AccountType.BANK, AccountType.CASH ->
                                    transactionRepository.observeTransactionsForAccount(account.id)
                                        .flatMapLatest { _ ->
                                            flowOf(AccountSummary(
                                                account,
                                                transactionRepository.getAccountBalance(account.id)
                                            ))
                                        }
                            }
                        }
                        combine(balanceFlows) { it.toList() }
                    }
                }
                .collect { summaries ->
                    val totalWealth = summaries.sumOf { it.balance }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        accountSummaries = summaries,
                        totalWealth = totalWealth,
                    )
                }
        }
    }

    private val _chartRange = MutableStateFlow(ChartRange.ONE_YEAR)

    private fun observeWealthHistory() {
        scope.launch {
            combine(
                accountRepository.observeAccounts(),
                transactionRepository.observeAllBankCashTransactions(),
                snapshotRepository.observeAllSnapshots(),
                _chartRange,
            ) { accounts, transactions, snapshots, range ->
                buildWealthHistory(accounts, transactions, snapshots, range)
            }.collect { points ->
                val first = points.firstOrNull()?.totalWealth
                val last = points.lastOrNull()?.totalWealth
                val change = if (first != null && last != null) last - first else null
                val changePct = if (first != null && first != 0.0 && change != null) (change / first) * 100.0 else null
                _uiState.value = _uiState.value.copy(
                    wealthHistory = points,
                    periodChange = change,
                    periodChangePct = changePct,
                )
            }
        }
    }

    private fun buildWealthHistory(
        accounts: List<Account>,
        allTransactions: List<Transaction>,
        allSnapshots: List<InvestmentSnapshot>,
        range: ChartRange,
    ): List<WealthPoint> {
        val millisInWeek = 7 * 86_400_000L
        val now = currentWeekStartMillis()

        // Build the list of week starts based on range
        val weekStarts = when {
            range == ChartRange.YTD -> {
                val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val jan1Ms = LocalDate(local.year, 1, 1)
                    .atStartOfDayIn(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()
                generateSequence(jan1Ms) { it + millisInWeek }
                    .takeWhile { it <= now }
                    .toList()
            }
            range.weeks != null -> {
                val count = range.weeks
                (count downTo 0).map { now - it * millisInWeek }
            }
            else -> {
                // ALL: up to 5 years of history
                (260 downTo 0).map { now - it * millisInWeek }
            }
        }

        val snapshotsByAccount = allSnapshots.groupBy { it.accountId }
        val transactionsByAccount = allTransactions.groupBy { it.accountId }

        val points = weekStarts.map { weekStart ->
            val weekEnd = weekStart + millisInWeek - 1

            val byAccount = accounts.map { account ->
                val balance = when (account.type) {
                    AccountType.INVESTMENT -> {
                        snapshotsByAccount[account.id]
                            ?.filter { it.weekDate <= weekEnd }
                            ?.maxByOrNull { it.weekDate }
                            ?.totalValue ?: 0.0
                    }
                    AccountType.BANK, AccountType.CASH -> {
                        transactionsByAccount[account.id]
                            ?.filter { it.date <= weekEnd }
                            ?.sumOf { tx ->
                                if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
                            } ?: 0.0
                    }
                }
                AccountPoint(account = account, weekDate = weekStart, balance = balance)
            }

            val total = byAccount.sumOf { it.balance }
            WealthPoint(weekDate = weekStart, totalWealth = total, byAccount = byAccount)
        }

        val firstNonZero = points.indexOfFirst { it.totalWealth != 0.0 }
        return if (firstNonZero >= 0) points.drop(firstNonZero) else emptyList()
    }

    private fun checkMissingSnapshots() {
        scope.launch {
            val now = Clock.System.now()
            val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
            // Banner only shown on Fridays (dayOfWeek ordinal: Mon=0 … Fri=4)
            if (local.dayOfWeek.ordinal != 4) return@launch
            val weekDate = currentWeekStartMillis()
            val missing = snapshotRepository.getAccountsMissingThisWeek(weekDate)
            _uiState.value = _uiState.value.copy(accountsMissingSnapshot = missing)
        }
    }

    fun toggleChartMode() {
        val next = if (_uiState.value.chartMode == ChartMode.AREA) ChartMode.BARS else ChartMode.AREA
        _uiState.value = _uiState.value.copy(chartMode = next)
    }

    fun setChartRange(range: ChartRange) {
        _uiState.value = _uiState.value.copy(chartRange = range)
        _chartRange.value = range
    }

    fun dismissMissingSnapshotBanner() {
        _uiState.value = _uiState.value.copy(accountsMissingSnapshot = emptyList())
    }

    fun toggleAccountVisibility(accountId: Long) {
        val current = _uiState.value.hiddenAccountIds
        _uiState.value = _uiState.value.copy(
            hiddenAccountIds = if (accountId in current) current - accountId else current + accountId
        )
    }

    fun toggleTotal() {
        _uiState.value = _uiState.value.copy(showTotal = !_uiState.value.showTotal)
    }

    private fun currentWeekStartMillis(): Long {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = local.dayOfWeek.ordinal
        val millisInDay = 86_400_000L
        return (now.toEpochMilliseconds() / millisInDay - dayOfWeek) * millisInDay
    }
}
