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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

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
    val accountsMissingSnapshot: List<Account> = emptyList(),
    val chartMode: ChartMode = ChartMode.AREA,
    val chartRange: ChartRange = ChartRange.ONE_YEAR,
    val periodChange: Double? = null,
    val periodChangePct: Double? = null,
    val hiddenAccountIds: Set<Long> = emptySet(),
    val showTotal: Boolean = true,
    val monthlySummary: MonthlySummary? = null,
    val annualSummary: AnnualSummary? = null,
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
                transactionRepository.observeAllBankCashTransactions(),
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

    private fun observeMonthlySummary() {
        scope.launch {
            transactionRepository.observeAllBankCashTransactions().collect { transactions ->
                val summary = buildMonthlySummary(transactions)
                _uiState.value = _uiState.value.copy(monthlySummary = summary)
            }
        }
    }

    private fun observeAnnualSummary() {
        scope.launch {
            combine(
                transactionRepository.observeAllBankCashTransactions(),
                snapshotRepository.observeAllSnapshots(),
                _uiState.map { it.selectedAnalysisYear },
            ) { transactions, snapshots, year ->
                buildAnnualSummary(transactions, snapshots, year)
            }.collect { summary ->
                _uiState.value = _uiState.value.copy(annualSummary = summary)
            }
        }
    }

    private fun buildAnnualSummary(
        transactions: List<Transaction>,
        snapshots: List<InvestmentSnapshot>,
        year: Int,
    ): AnnualSummary? {
        val tz = TimeZone.currentSystemDefault()
        val yearStart = LocalDate(year, 1, 1).atStartOfDayIn(tz).toEpochMilliseconds()
        val yearEnd = LocalDate(year, 12, 31).atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L - 1

        val thisYear = transactions.filter { it.date in yearStart..yearEnd }

        // Group snapshots by accountId for investment delta calculation
        val snapshotsByAccount = snapshots.groupBy { it.accountId }

        val months = (1..12).map { month ->
            val monthStart = LocalDate(year, month, 1).atStartOfDayIn(tz).toEpochMilliseconds()
            val lastDay = when (month) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                else -> 30
            }
            val monthEnd = LocalDate(year, month, lastDay).atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L - 1

            val monthTxs = thisYear.filter { it.date in monthStart..monthEnd }

            val income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expenses = monthTxs.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.TRANSFER }.sumOf { it.amount }

            // Investment delta: latest snapshot this month minus latest snapshot before this month, per account
            val investmentDelta = snapshotsByAccount.values.sumOf { accountSnapshots ->
                val endValue = accountSnapshots.filter { it.weekDate <= monthEnd }.maxByOrNull { it.weekDate }?.totalValue ?: 0.0
                val startValue = accountSnapshots.filter { it.weekDate < monthStart }.maxByOrNull { it.weekDate }?.totalValue ?: 0.0
                endValue - startValue
            }

            val topCategories = monthTxs
                .filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.TRANSFER }
                .groupBy { it.category?.ifBlank { null } ?: "Otros" }
                .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }.take(4)
                .let { entries ->
                    val max = entries.firstOrNull()?.value ?: 1.0
                    entries.map { (label, amount) ->
                        CategoryBreakdown(label, amount, (amount / max).toFloat().coerceIn(0f, 1f))
                    }
                }

            MonthBreakdown(
                monthNumber = month,
                income = income,
                expenses = expenses,
                investmentDelta = investmentDelta,
                net = income - expenses + investmentDelta,
                topCategories = topCategories,
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

    private fun buildMonthlySummary(transactions: List<Transaction>): MonthlySummary? {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val monthStart = LocalDate(now.year, now.monthNumber, 1)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

        val thisMonth = transactions.filter { it.date >= monthStart }
        if (thisMonth.isEmpty()) return null

        val income = thisMonth
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }
        val expenses = thisMonth
            .filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.TRANSFER }
            .sumOf { it.amount }

        val topCategories = thisMonth
            .filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.TRANSFER }
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

        return MonthlySummary(
            income = income,
            expenses = expenses,
            net = income - expenses,
            topCategories = topCategories,
        )
    }

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
            val missing = snapshotRepository.getAccountsMissingThisWeek(weekDate)
            _uiState.value = _uiState.value.copy(accountsMissingSnapshot = missing)
        }
    }

    fun toggleChartMode() {
        val next = if (_uiState.value.chartMode == ChartMode.AREA) ChartMode.BARS else ChartMode.AREA
        _uiState.value = _uiState.value.copy(chartMode = next)
    }

    fun setChartRange(range: ChartRange) {
        ChartRangePreference.set(range)
        // uiState.chartRange se actualiza reactivamente vía observeChartRange()
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

    private fun currentWeekStartMillis(): Long {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = local.dayOfWeek.ordinal
        val millisInDay = 86_400_000L
        return (now.toEpochMilliseconds() / millisInDay - dayOfWeek) * millisInDay
    }
}
