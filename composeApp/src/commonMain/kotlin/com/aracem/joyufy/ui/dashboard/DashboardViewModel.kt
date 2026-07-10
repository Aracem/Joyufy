package com.aracem.joyufy.ui.dashboard

import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.PreferencesRepository
import com.aracem.joyufy.update.UpdateInfo
import com.aracem.joyufy.update.checkForUpdate
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.aracem.joyufy.ui.components.MILLIS_IN_WEEK
import com.aracem.joyufy.ui.components.currentWeekStartMillis
import com.aracem.joyufy.ui.components.monthEndMillis
import com.aracem.joyufy.ui.components.monthStartMillis
import com.aracem.joyufy.ui.components.weekStartsForRange
import com.aracem.joyufy.ui.components.weekStartsForYtd
import com.aracem.joyufy.ui.components.yearEndMillis
import com.aracem.joyufy.ui.components.yearStartMillis

data class AccountSummary(
    val account: Account,
    val balance: Double,
)

data class CategoryBreakdown(
    val label: String,
    val amount: Double,
    val fraction: Float,   // 0..1 relative to the largest category
)

data class MonthlySummary(
    val income: Double,
    val expenses: Double,
    val net: Double,
    val investmentDelta: Double,
    val topCategories: List<CategoryBreakdown>,   // top expense categories, max 4
)

data class MonthBreakdown(
    val monthNumber: Int,   // 1..12
    val income: Double,
    val expenses: Double,
    val investmentDelta: Double,  // change in investment value (positive = gain, negative = loss)
    val net: Double,              // income - expenses + investmentDelta
    val topCategories: List<CategoryBreakdown>,
)

data class AnnualSummary(
    val year: Int,
    val months: List<MonthBreakdown>,   // 12 entries, Jan..Dec
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalInvestmentDelta: Double,
    val totalNet: Double,
)

data class PeriodComparison(
    val currentMonth: MonthBreakdown,
    val previousMonth: MonthBreakdown,
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

data class MissingSnapshotTask(
    val account: Account,
    val lastSnapshotDate: Long?,
)

enum class ChartMode { AREA, BARS }

enum class ChartVisibilityPreset { ALL, LIQUID, INVESTMENTS }

enum class ChartRange(val weeks: Int?) {
    ONE_WEEK(1),
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
    val missingSnapshotTasks: List<MissingSnapshotTask> = emptyList(),
    val chartMode: ChartMode = ChartMode.AREA,
    val chartRange: ChartRange = ChartRange.ONE_YEAR,
    val periodChange: Double? = null,
    val periodChangePct: Double? = null,
    val hiddenAccountIds: Set<Long> = emptySet(),
    val showTotal: Boolean = true,
    val monthlySummary: MonthlySummary? = null,
    val annualSummary: AnnualSummary? = null,
    val periodComparison: PeriodComparison? = null,
    val uncategorizedTransactionCount: Int = 0,
    val selectedAnalysisYear: Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year,
    val analysisExpanded: Boolean = false,
    val updateInfo: UpdateInfo? = null,
)

class DashboardViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
    private val wealthRepository: WealthRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            chartRange = ChartRangePreference.range.value,
            selectedAnalysisYear = currentYear,
            analysisExpanded = preferencesRepository.getAnalysisExpanded(),
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeBalances()
        observeWealthHistory()
        observeMonthlySummary()
        observeAnnualSummary()
        checkMissingSnapshots()
        observeChartRange()
        checkForUpdates()
    }

    private fun observeChartRange() {
        scope.launch {
            ChartRangePreference.range.collect { range ->
                _uiState.value = _uiState.value.copy(chartRange = range)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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
                                        val balance = snapshots.maxByOrNull { it.weekDate }?.totalValue
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
                .catch { _uiState.value = _uiState.value.copy(isLoading = false) }
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

    private fun observeWealthHistory() {
        scope.launch {
            combine(
                accountRepository.observeAccounts(),
                transactionRepository.observeAllTransactions(),
                snapshotRepository.observeAllSnapshots(),
                ChartRangePreference.range,
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
        val now = currentWeekStartMillis()
        val weekStarts = if (range == ChartRange.YTD) weekStartsForYtd(now) else weekStartsForRange(range.weeks, now)

        val snapshotsByAccount = allSnapshots.groupBy { it.accountId }
        val transactionsByAccount = allTransactions.groupBy { it.accountId }

        val points = weekStarts.map { weekStart ->
            val weekEnd = weekStart + MILLIS_IN_WEEK - 1

            val byAccount = accounts
                .filter { account -> account.createdAt <= weekEnd }
                .map { account ->
                val balance = when (account.type) {
                    AccountType.INVESTMENT -> {
                        snapshotsByAccount[account.id]
                            ?.filter { it.weekDate <= weekEnd }
                            ?.maxByOrNull { it.weekDate }
                            ?.totalValue
                            ?: transactionsByAccount[account.id]
                                ?.filter { it.date <= weekEnd }
                                ?.sumOf { tx ->
                                    if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
                                }
                            ?: 0.0
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

    private fun observeMonthlySummary() {
        scope.launch {
            combine(
                transactionRepository.observeAllBankCashTransactions(),
                snapshotRepository.observeAllSnapshots(),
                accountRepository.observeAccounts(),
            ) { transactions, snapshots, accounts ->
                val investmentAccountIds = accounts.filter { it.type == AccountType.INVESTMENT }.map { it.id }.toSet()
                val summary = buildMonthlySummary(transactions, snapshots, investmentAccountIds)
                val comparison = buildPeriodComparison(transactions, snapshots, investmentAccountIds)
                val uncategorizedCount = transactions.count { tx ->
                    tx.relatedAccountId == null && tx.category.isNullOrBlank()
                }
                Triple(summary, comparison, uncategorizedCount)
            }.collect { (summary, comparison, uncategorizedCount) ->
                _uiState.value = _uiState.value.copy(
                    monthlySummary = summary,
                    periodComparison = comparison,
                    uncategorizedTransactionCount = uncategorizedCount,
                )
            }
        }
    }

    private fun observeAnnualSummary() {
        scope.launch {
            combine(
                transactionRepository.observeAllBankCashTransactions(),
                snapshotRepository.observeAllSnapshots(),
                accountRepository.observeAccounts(),
                _uiState.map { it.selectedAnalysisYear },
            ) { transactions, snapshots, accounts, year ->
                val investmentAccountIds = accounts.filter { it.type == AccountType.INVESTMENT }.map { it.id }.toSet()
                buildAnnualSummary(transactions, snapshots, investmentAccountIds, year)
            }.collect { summary ->
                _uiState.value = _uiState.value.copy(annualSummary = summary)
            }
        }
    }

    private fun buildAnnualSummary(
        transactions: List<Transaction>,
        snapshots: List<InvestmentSnapshot>,
        investmentAccountIds: Set<Long>,
        year: Int,
    ): AnnualSummary? {
        val yearStart = yearStartMillis(year)
        val yearEnd = yearEndMillis(year)

        // Exclude transfer legs (both sides have relatedAccountId != null)
        val thisYear = transactions.filter { it.date in yearStart..yearEnd && it.relatedAccountId == null }

        // All BANK/CASH transactions (including transfers) needed to compute capital flows
        val thisYearAll = transactions.filter { it.date in yearStart..yearEnd }

        // Group snapshots by accountId for investment delta calculation
        val snapshotsByAccount = snapshots.groupBy { it.accountId }

        val months = (1..12).map { month ->
            buildMonthBreakdown(
                year = year,
                month = month,
                transactions = thisYearAll,
                snapshotsByAccount = snapshotsByAccount,
                investmentAccountIds = investmentAccountIds,
            )
        }

        if (months.all { it.income == 0.0 && it.expenses == 0.0 && it.investmentDelta == 0.0 }) return null

        return AnnualSummary(
            year = year,
            months = months,
            totalIncome = months.sumOf { it.income },
            totalExpenses = months.sumOf { it.expenses },
            totalInvestmentDelta = months.sumOf { it.investmentDelta },
            totalNet = months.sumOf { it.net },
        )
    }

    private fun buildPeriodComparison(
        transactions: List<Transaction>,
        snapshots: List<InvestmentSnapshot>,
        investmentAccountIds: Set<Long>,
    ): PeriodComparison? {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val previousYear = if (now.monthNumber == 1) now.year - 1 else now.year
        val previousMonth = if (now.monthNumber == 1) 12 else now.monthNumber - 1
        val snapshotsByAccount = snapshots.groupBy { it.accountId }
        val current = buildMonthBreakdown(now.year, now.monthNumber, transactions, snapshotsByAccount, investmentAccountIds)
        val previous = buildMonthBreakdown(previousYear, previousMonth, transactions, snapshotsByAccount, investmentAccountIds)
        return if (!current.hasActivity() && !previous.hasActivity()) {
            null
        } else {
            PeriodComparison(currentMonth = current, previousMonth = previous)
        }
    }

    private fun buildMonthlySummary(
        transactions: List<Transaction>,
        snapshots: List<InvestmentSnapshot>,
        investmentAccountIds: Set<Long>,
    ): MonthlySummary? {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val monthStart = monthStartMillis(now.year, now.monthNumber)
        val monthEnd = monthEndMillis(now.year, now.monthNumber)

        val thisMonth = transactions.filter { it.date in monthStart..monthEnd && it.relatedAccountId == null }
        if (thisMonth.isEmpty() && snapshots.none { it.weekDate in monthStart..monthEnd }) return null

        val breakdown = buildMonthBreakdown(
            year = now.year,
            month = now.monthNumber,
            transactions = transactions,
            snapshotsByAccount = snapshots.groupBy { it.accountId },
            investmentAccountIds = investmentAccountIds,
        )

        return MonthlySummary(
            income = breakdown.income,
            expenses = breakdown.expenses,
            net = breakdown.income - breakdown.expenses,
            investmentDelta = breakdown.investmentDelta,
            topCategories = breakdown.topCategories,
        )
    }

    private fun buildMonthBreakdown(
        year: Int,
        month: Int,
        transactions: List<Transaction>,
        snapshotsByAccount: Map<Long, List<InvestmentSnapshot>>,
        investmentAccountIds: Set<Long>,
    ): MonthBreakdown {
        val monthStart = monthStartMillis(year, month)
        val monthEnd = monthEndMillis(year, month)
        val monthTxsAll = transactions.filter { it.date in monthStart..monthEnd }
        val monthTxs = monthTxsAll.filter { it.relatedAccountId == null }

        val income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expenses = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        val rawInvestmentDelta = snapshotsByAccount.values.sumOf { accountSnapshots ->
            val endValue = accountSnapshots.filter { it.weekDate <= monthEnd }.maxByOrNull { it.weekDate }?.totalValue ?: 0.0
            val startValue = accountSnapshots.filter { it.weekDate < monthStart }.maxByOrNull { it.weekDate }?.totalValue ?: 0.0
            endValue - startValue
        }
        val capitalIn = monthTxsAll
            .filter { it.type == TransactionType.EXPENSE && it.relatedAccountId in investmentAccountIds }
            .sumOf { it.amount }
        val capitalOut = monthTxsAll
            .filter { it.type == TransactionType.INCOME && it.relatedAccountId in investmentAccountIds }
            .sumOf { it.amount }
        val investmentDelta = rawInvestmentDelta - capitalIn + capitalOut

        val topCategories = monthTxs
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category?.ifBlank { null } ?: "Otros" }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .take(4)
            .let { entries ->
                val max = entries.firstOrNull()?.value ?: 1.0
                entries.map { (label, amount) ->
                    CategoryBreakdown(
                        label = label,
                        amount = amount,
                        fraction = (amount / max).toFloat().coerceIn(0f, 1f),
                    )
                }
            }

        return MonthBreakdown(
            monthNumber = month,
            income = income,
            expenses = expenses,
            investmentDelta = investmentDelta,
            net = income - expenses + investmentDelta,
            topCategories = topCategories,
        )
    }

    private fun MonthBreakdown.hasActivity(): Boolean =
        income != 0.0 || expenses != 0.0 || investmentDelta != 0.0

    private fun checkForUpdates() {
        scope.launch {
            val info = checkForUpdate()
            if (info != null) _uiState.value = _uiState.value.copy(updateInfo = info)
        }
    }

    fun dismissUpdateBanner() {
        _uiState.value = _uiState.value.copy(updateInfo = null)
    }

    fun setAnalysisExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(analysisExpanded = expanded)
        preferencesRepository.setAnalysisExpanded(expanded)
    }

    fun navigateAnalysisYear(delta: Int) {
        val newYear = (_uiState.value.selectedAnalysisYear + delta).coerceAtMost(currentYear)
        _uiState.value = _uiState.value.copy(selectedAnalysisYear = newYear)
    }

    private fun checkMissingSnapshots() {
        scope.launch {
            val now = Clock.System.now()
            val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
            // Banner only shown on Fridays (dayOfWeek ordinal: Mon=0 … Fri=4)
            if (local.dayOfWeek.ordinal != 4) return@launch
            val weekDate = currentWeekStartMillis()
            val tasks = snapshotRepository.getAccountsMissingThisWeek(weekDate)
                .map { account ->
                    MissingSnapshotTask(
                        account = account,
                        lastSnapshotDate = snapshotRepository.getLatestSnapshot(account.id)?.weekDate,
                    )
                }
            _uiState.value = _uiState.value.copy(missingSnapshotTasks = tasks)
        }
    }

    fun toggleChartMode() {
        val next = if (_uiState.value.chartMode == ChartMode.AREA) ChartMode.BARS else ChartMode.AREA
        _uiState.value = _uiState.value.copy(chartMode = next)
    }

    fun setChartMode(mode: ChartMode) {
        _uiState.value = _uiState.value.copy(chartMode = mode)
    }

    fun setChartRange(range: ChartRange) {
        ChartRangePreference.set(range)
        // uiState.chartRange se actualiza reactivamente vía observeChartRange()
    }

    fun dismissMissingSnapshotBanner() {
        _uiState.value = _uiState.value.copy(missingSnapshotTasks = emptyList())
    }

    fun toggleAccountVisibility(accountId: Long) {
        val current = _uiState.value.hiddenAccountIds
        _uiState.value = _uiState.value.copy(
            hiddenAccountIds = if (accountId in current) current - accountId else current + accountId
        )
    }

    fun setChartVisibilityPreset(preset: ChartVisibilityPreset) {
        val summaries = _uiState.value.accountSummaries
        val hiddenIds = when (preset) {
            ChartVisibilityPreset.ALL -> emptySet()
            ChartVisibilityPreset.LIQUID -> summaries
                .filter { it.account.type == AccountType.INVESTMENT }
                .map { it.account.id }
                .toSet()
            ChartVisibilityPreset.INVESTMENTS -> summaries
                .filter { it.account.type != AccountType.INVESTMENT }
                .map { it.account.id }
                .toSet()
        }
        _uiState.value = _uiState.value.copy(hiddenAccountIds = hiddenIds, showTotal = true)
    }

    fun toggleTotal() {
        _uiState.value = _uiState.value.copy(showTotal = !_uiState.value.showTotal)
    }

    fun reorderAccounts(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val summaries = _uiState.value.accountSummaries.toMutableList()
        val moved = summaries.removeAt(fromIndex)
        summaries.add(toIndex, moved)
        // Optimistic update — UI reflects new order immediately
        _uiState.value = _uiState.value.copy(accountSummaries = summaries)
        // Persist new positions
        scope.launch(Dispatchers.IO) {
            summaries.forEachIndexed { index, summary ->
                if (summary.account.position != index) {
                    accountRepository.updateAccount(summary.account.copy(position = index))
                }
            }
        }
    }

}
