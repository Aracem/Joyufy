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
import kotlinx.coroutines.flow.combine
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
        observeAccounts()
        checkMissingSnapshots()
    }

    private fun observeAccounts() {
        scope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                val summaries = accounts.map { account ->
                    val balance = when (account.type) {
                        AccountType.INVESTMENT ->
                            snapshotRepository.getLatestSnapshot(account.id)?.totalValue ?: 0.0
                        AccountType.BANK, AccountType.CASH ->
                            transactionRepository.getAccountBalance(account.id)
                    }
                    AccountSummary(account, balance)
                }
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
        // Monday 00:00 UTC of the current week
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = local.dayOfWeek.ordinal // Monday = 0
        val millisInDay = 86_400_000L
        return (now.toEpochMilliseconds() / millisInDay - dayOfWeek) * millisInDay
    }
}
