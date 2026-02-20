package com.aracem.nexlify.ui.dashboard

import com.aracem.nexlify.data.repository.AccountRepository
import com.aracem.nexlify.data.repository.InvestmentSnapshotRepository
import com.aracem.nexlify.data.repository.TransactionRepository
import com.aracem.nexlify.data.repository.WealthRepository
import com.aracem.nexlify.domain.model.Account
import com.aracem.nexlify.domain.model.AccountType
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class AccountSummary(
    val account: Account,
    val balance: Double,
)

data class WealthPoint(
    val weekDate: Long,
    val totalWealth: Double,
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalWealth: Double = 0.0,
    val wealthHistory: List<WealthPoint> = emptyList(),
    val accountSummaries: List<AccountSummary> = emptyList(),
    val accountsMissingSnapshot: List<Account> = emptyList(),
    val chartMode: ChartMode = ChartMode.AREA,
)

enum class ChartMode { AREA, BARS }

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
                                    snapshotRepository.observeSnapshotsForAccount(account.id)
                                        .flatMapLatest { snapshots ->
                                            flowOf(AccountSummary(account, snapshots.firstOrNull()?.totalValue ?: 0.0))
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

    private fun checkMissingSnapshots() {
        scope.launch {
            val weekDate = currentWeekStartMillis()
            val missing = snapshotRepository.getAccountsMissingThisWeek(weekDate)
            _uiState.value = _uiState.value.copy(accountsMissingSnapshot = missing)
        }
    }

    fun toggleChartMode() {
        val next = if (_uiState.value.chartMode == ChartMode.AREA) ChartMode.BARS else ChartMode.AREA
        _uiState.value = _uiState.value.copy(chartMode = next)
    }

    fun dismissMissingSnapshotBanner() {
        _uiState.value = _uiState.value.copy(accountsMissingSnapshot = emptyList())
    }

    private fun currentWeekStartMillis(): Long {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = local.dayOfWeek.ordinal
        val millisInDay = 86_400_000L
        return (now.toEpochMilliseconds() / millisInDay - dayOfWeek) * millisInDay
    }
}
