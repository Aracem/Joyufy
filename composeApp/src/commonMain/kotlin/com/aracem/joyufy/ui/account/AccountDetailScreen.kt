package com.aracem.joyufy.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.AccountLogo
import com.aracem.joyufy.ui.components.AccountLogoInitials
import com.aracem.joyufy.ui.components.MILLIS_IN_DAY
import com.aracem.joyufy.ui.components.SingleAccountChart
import com.aracem.joyufy.ui.components.TooltipIconButton
import com.aracem.joyufy.ui.components.formatCurrency
import com.aracem.joyufy.ui.components.formatPercent
import com.aracem.joyufy.ui.components.parseDateInputToMillis
import com.aracem.joyufy.ui.dashboard.ChartMode
import com.aracem.joyufy.ui.dashboard.ChartRange
import com.aracem.joyufy.ui.dashboard.ChartRangeSelector
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.Positive
import com.aracem.joyufy.ui.theme.joyufyColors
import com.aracem.joyufy.ui.components.formatDate
import com.aracem.joyufy.ui.components.formatWeekRange
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    openSnapshotDialog: Boolean = false,
    openTransactionDialog: Boolean = false,
    focusSearch: Boolean = false,
    launchRequestId: Long = 0L,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val viewModel: AccountDetailViewModel = koinInject { parametersOf(accountId) }
    val state by viewModel.uiState.collectAsState()
    val event by viewModel.event.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddTransaction by remember { mutableStateOf(false) }
    var showAddSnapshot by remember { mutableStateOf(false) }
    var showEditAccount by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editingSnapshot by remember { mutableStateOf<InvestmentSnapshot?>(null) }
    var confirmDeleteTxId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteSnapshotId by remember { mutableStateOf<Long?>(null) }

    // ── Filter state ──────────────────────────────────────────────────────
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TransactionType?>(null) }
    var filterCategory by remember { mutableStateOf<String?>(null) }
    var filterDateFrom by remember { mutableStateOf("") }
    var filterDateTo by remember { mutableStateOf("") }
    var filterAmountMin by remember { mutableStateOf("") }
    var filterAmountMax by remember { mutableStateOf("") }
    var transfersOnly by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val availableCategories = remember(state.transactions) {
        state.transactions
            .mapNotNull { it.category?.ifBlank { null } }
            .distinct()
            .sorted()
    }

    fun clearFilters() {
        searchQuery = ""
        filterType = null
        filterCategory = null
        filterDateFrom = ""
        filterDateTo = ""
        filterAmountMin = ""
        filterAmountMax = ""
        transfersOnly = false
    }

    fun transactionMatchesFilters(tx: Transaction): Boolean {
        val fromDate = filterDateFrom.takeIf { it.isNotBlank() }?.let {
            parseDateInputToMillis(it, dayOffsetMillis = 0L)
        }
        val toDate = filterDateTo.takeIf { it.isNotBlank() }?.let {
            parseDateInputToMillis(it, dayOffsetMillis = MILLIS_IN_DAY - 1)
        }
        val minAmount = filterAmountMin.replace(",", ".").toDoubleOrNull()
        val maxAmount = filterAmountMax.replace(",", ".").toDoubleOrNull()
        val q = searchQuery.trim().lowercase()
        val matchesSearch = q.isBlank() ||
            tx.description?.lowercase()?.contains(q) == true ||
            tx.category?.lowercase()?.contains(q) == true
        val isTransfer = tx.relatedAccountId != null || tx.type == TransactionType.TRANSFER
        val matchesType = when (filterType) {
            null -> true
            TransactionType.TRANSFER -> isTransfer
            else -> tx.type == filterType
        }
        return matchesSearch &&
            matchesType &&
            (filterCategory == null || tx.category == filterCategory) &&
            (fromDate == null || tx.date >= fromDate) &&
            (toDate == null || tx.date <= toDate) &&
            (minAmount == null || tx.amount >= minAmount) &&
            (maxAmount == null || tx.amount <= maxAmount) &&
            (!transfersOnly || isTransfer)
    }

    val isFiltered = searchQuery.isNotBlank() ||
        filterType != null ||
        filterCategory != null ||
        filterDateFrom.isNotBlank() ||
        filterDateTo.isNotBlank() ||
        filterAmountMin.isNotBlank() ||
        filterAmountMax.isNotBlank() ||
        transfersOnly

    val filteredTransactions = remember(
        state.transactions,
        searchQuery,
        filterType,
        filterCategory,
        filterDateFrom,
        filterDateTo,
        filterAmountMin,
        filterAmountMax,
        transfersOnly,
    ) {
        state.transactions.filter(::transactionMatchesFilters)
    }
    val runningBalanceByTransactionId = remember(state.transactions) {
        buildRunningBalanceByTransactionId(state.transactions)
    }
    val filteredTransactionGroups = remember(filteredTransactions) {
        buildTransactionMonthGroups(filteredTransactions)
    }

    val filteredInvestmentFeed = remember(
        state.investmentFeed,
        isFiltered,
        searchQuery,
        filterType,
        filterCategory,
        filterDateFrom,
        filterDateTo,
        filterAmountMin,
        filterAmountMax,
        transfersOnly,
    ) {
        if (!isFiltered) {
            state.investmentFeed
        } else {
            state.investmentFeed.mapNotNull { item ->
                when (item) {
                    is InvestmentListItem.Snapshot -> null
                    is InvestmentListItem.Tx ->
                        if (transactionMatchesFilters(item.transaction)) item else null
                }
            }
        }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    val account = state.account ?: return

    LaunchedEffect(event) {
        when (val ev = event) {
            is AccountDetailEvent.TransactionsDeleted -> {
                val result = snackbarHostState.showSnackbar(
                    message = strings.ledgerDeletedTransactions.format(ev.count),
                    actionLabel = strings.undo,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoLastTransactionDelete()
                }
                viewModel.resetEvent()
            }
            is AccountDetailEvent.SnapshotDeleted -> {
                val result = snackbarHostState.showSnackbar(
                    message = strings.snapshotDeleted,
                    actionLabel = strings.undo,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoLastSnapshotDelete()
                }
                viewModel.resetEvent()
            }
            is AccountDetailEvent.Restored -> {
                snackbarHostState.showSnackbar(strings.ledgerRestoredTransactions.format(ev.count))
                viewModel.resetEvent()
            }
            is AccountDetailEvent.Error -> {
                snackbarHostState.showSnackbar(ev.message.ifBlank { "Error" })
                viewModel.resetEvent()
            }
            AccountDetailEvent.Idle -> Unit
        }
    }

    LaunchedEffect(account.id, launchRequestId) {
        if (openSnapshotDialog && account.type == AccountType.INVESTMENT) {
            showAddSnapshot = true
        }
        if (openTransactionDialog) {
            showAddTransaction = true
        }
        if (focusSearch) {
            if (state.transactions.isNotEmpty()) {
                searchFocusRequester.requestFocus()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        // ── Sticky balance/action header ─────────────────────────────────
        stickyHeader {
            AccountDetailStickyHeader(
                account = account,
                balance = state.balance,
                periodChange = state.periodChange,
                periodChangePct = state.periodChangePct,
                chartRange = state.chartRange,
                onBack = onBack,
                onEditAccount = { showEditAccount = true },
                onAddSnapshot = { showAddSnapshot = true },
                onAddTransaction = { showAddTransaction = true },
            )
        }

        item { HorizontalDivider(color = MaterialTheme.joyufyColors.border) }

        // ── Account history chart ─────────────────────────────────────────
        item {
            AccountHistoryCard(
                history = state.accountHistory,
                chartRange = state.chartRange,
                account = account,
                onRangeChange = viewModel::setChartRange,
            )
        }

        item { HorizontalDivider(color = MaterialTheme.joyufyColors.border) }

        if (account.type == AccountType.INVESTMENT) {
            // ── Investment feed: snapshots + transacciones intercaladas ────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.weeklyValue,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    if (state.transactions.isNotEmpty() && isFiltered) {
                        TextButton(onClick = ::clearFilters) {
                            Text(strings.clearFilters, color = MaterialTheme.joyufyColors.contentSecondary)
                        }
                    }
                }
            }
            if (state.transactions.isNotEmpty()) {
                item {
                    TransactionFilterBar(
                        searchQuery = searchQuery,
                        searchFocusRequester = searchFocusRequester,
                        onSearchChange = { searchQuery = it },
                        filterType = filterType,
                        onTypeChange = { filterType = if (filterType == it) null else it },
                        filterCategory = filterCategory,
                        onCategoryChange = { filterCategory = if (filterCategory == it) null else it },
                        filterDateFrom = filterDateFrom,
                        onDateFromChange = { filterDateFrom = it },
                        filterDateTo = filterDateTo,
                        onDateToChange = { filterDateTo = it },
                        filterAmountMin = filterAmountMin,
                        onAmountMinChange = { filterAmountMin = it },
                        filterAmountMax = filterAmountMax,
                        onAmountMaxChange = { filterAmountMax = it },
                        transfersOnly = transfersOnly,
                        onTransfersOnlyChange = { transfersOnly = it },
                        availableCategories = availableCategories,
                    )
                }
            }
            if (state.investmentFeed.isEmpty()) {
                item { EmptyListHint(strings.noWeeklyRecords, strings.noWeeklyRecordsHint, Icons.Default.DateRange) }
            } else if (filteredInvestmentFeed.isEmpty()) {
                item { EmptyListHint(strings.noSearchResults, strings.noSearchResultsHint, Icons.Default.Search) }
            } else {
                items(filteredInvestmentFeed, key = { item ->
                    when (item) {
                        is InvestmentListItem.Snapshot -> "snap_${item.snapshot.id}"
                        is InvestmentListItem.Tx -> "tx_${item.transaction.id}"
                    }
                }) { item ->
                    when (item) {
                        is InvestmentListItem.Snapshot -> SnapshotRow(
                            snapshot = item.snapshot,
                            onEdit = { editingSnapshot = item.snapshot },
                            onDelete = { confirmDeleteSnapshotId = item.snapshot.id },
                        )
                        is InvestmentListItem.Tx -> TransactionRow(
                            transaction = item.transaction,
                            allAccounts = state.allAccounts,
                            onEdit = { editingTransaction = item.transaction },
                            onDelete = { confirmDeleteTxId = item.transaction.id },
                            indented = true,
                        )
                    }
                }
            }
        } else {
            // ── Transactions (bank / cash) ─────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.transactions,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    if (state.transactions.isNotEmpty() && isFiltered) {
                        TextButton(
                            onClick = ::clearFilters,
                        ) {
                            Text(strings.clearFilters, color = MaterialTheme.joyufyColors.contentSecondary)
                        }
                    }
                }
            }

            if (state.transactions.isNotEmpty()) {
                item {
                    TransactionFilterBar(
                        searchQuery = searchQuery,
                        searchFocusRequester = searchFocusRequester,
                        onSearchChange = { searchQuery = it },
                        filterType = filterType,
                        onTypeChange = { filterType = if (filterType == it) null else it },
                        filterCategory = filterCategory,
                        onCategoryChange = { filterCategory = if (filterCategory == it) null else it },
                        filterDateFrom = filterDateFrom,
                        onDateFromChange = { filterDateFrom = it },
                        filterDateTo = filterDateTo,
                        onDateToChange = { filterDateTo = it },
                        filterAmountMin = filterAmountMin,
                        onAmountMinChange = { filterAmountMin = it },
                        filterAmountMax = filterAmountMax,
                        onAmountMaxChange = { filterAmountMax = it },
                        transfersOnly = transfersOnly,
                        onTransfersOnlyChange = { transfersOnly = it },
                        availableCategories = availableCategories,
                    )
                }
            }

            if (state.transactions.isEmpty()) {
                item { EmptyListHint(strings.noTransactions, strings.noTransactionsHint, Icons.AutoMirrored.Filled.List) }
            } else if (filteredTransactions.isEmpty()) {
                item { EmptyListHint(strings.noSearchResults, strings.noSearchResultsHint, Icons.Default.Search) }
            } else {
                filteredTransactionGroups.forEach { group ->
                    stickyHeader {
                        TransactionMonthHeader(group = group)
                    }
                    items(group.transactions, key = { it.id }) { tx ->
                        TransactionRow(
                            transaction = tx,
                            allAccounts = state.allAccounts,
                            runningBalance = runningBalanceByTransactionId[tx.id],
                            onEdit = { editingTransaction = tx },
                            onDelete = { confirmDeleteTxId = tx.id },
                        )
                    }
                }
            }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        )
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (showEditAccount) {
        CreateAccountDialog(
            existingCount = 0,
            editingAccount = account,
            onDismiss = { showEditAccount = false },
            onCreated = { showEditAccount = false },
        )
    }

    if (showAddTransaction || editingTransaction != null) {
        AddTransactionDialog(
            accountType = account.type,
            availableAccounts = state.allAccounts,
            customCategories = state.customCategories,
            editingTransaction = editingTransaction,
            onDismiss = { showAddTransaction = false; editingTransaction = null },
            onConfirm = { type, amount, category, desc, relatedId, date ->
                val editing = editingTransaction
                if (editing != null) {
                    viewModel.updateTransaction(editing.id, type, amount, category, desc, relatedId, date)
                } else {
                    viewModel.addTransaction(type, amount, category, desc, relatedId, date)
                }
            },
        )
    }

    confirmDeleteTxId?.let { txId ->
        DeleteConfirmDialog(
            title = strings.confirmDeleteTransaction,
            text = strings.confirmDeleteTransactionText,
            onConfirm = { viewModel.deleteTransaction(txId); confirmDeleteTxId = null },
            onDismiss = { confirmDeleteTxId = null },
        )
    }

    confirmDeleteSnapshotId?.let { snapId ->
        DeleteConfirmDialog(
            title = strings.confirmDeleteSnapshot,
            text = strings.confirmDeleteSnapshotText,
            onConfirm = { viewModel.deleteSnapshot(snapId); confirmDeleteSnapshotId = null },
            onDismiss = { confirmDeleteSnapshotId = null },
        )
    }

    if (showAddSnapshot || editingSnapshot != null) {
        AddSnapshotDialog(
            accountName = account.name,
            currentValue = state.snapshots.firstOrNull()?.totalValue,
            editingSnapshot = editingSnapshot,
            onDismiss = { showAddSnapshot = false; editingSnapshot = null },
            onConfirm = { value, weekDate ->
                val editing = editingSnapshot
                if (editing != null) {
                    viewModel.updateSnapshot(editing.id, value, weekDate)
                } else {
                    viewModel.addSnapshot(value, weekDate)
                }
            },
        )
    }
}

@Composable
private fun AccountDetailStickyHeader(
    account: Account,
    balance: Double,
    periodChange: Double?,
    periodChangePct: Double?,
    chartRange: ChartRange,
    onBack: () -> Unit,
    onEditAccount: () -> Unit,
    onAddSnapshot: () -> Unit,
    onAddTransaction: () -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(
                    label = strings.goBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    tint = MaterialTheme.joyufyColors.contentSecondary,
                    modifier = Modifier.size(40.dp),
                    iconSize = 20.dp,
                )
                Spacer(Modifier.width(4.dp))
                if (account.logoUrl != null) {
                    AccountLogo(logoUrl = account.logoUrl, size = 32.dp, bgColor = account.color.copy(alpha = 0.25f))
                } else {
                    AccountLogoInitials(color = account.color, name = account.name, size = 32.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when (account.type) {
                            AccountType.BANK -> strings.accountTypeBank
                            AccountType.INVESTMENT -> strings.accountTypeInvestment
                            AccountType.CASH -> strings.accountTypeCash
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TooltipIconButton(
                        label = strings.editAccount,
                        icon = Icons.Default.Edit,
                        onClick = onEditAccount,
                        tint = MaterialTheme.joyufyColors.contentSecondary,
                        modifier = Modifier.size(32.dp),
                    )
                    if (account.type == AccountType.INVESTMENT) {
                        OutlinedButton(
                            onClick = onAddSnapshot,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Accent),
                        ) {
                            Text(strings.updateValue, color = Accent)
                        }
                    }
                    Button(
                        onClick = onAddTransaction,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.addTransaction)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Column(modifier = Modifier.padding(start = 48.dp)) {
                Text(
                    text = strings.currentBalance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                Spacer(Modifier.height(2.dp))
                val animatedBalance by animateFloatAsState(
                    targetValue = balance.toFloat(),
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                )
                Text(
                    text = animatedBalance.toDouble().formatCurrency(),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (balance >= 0) Positive else Negative,
                    modifier = if (account.type == AccountType.INVESTMENT)
                        Modifier.clickable { onAddSnapshot() }
                    else Modifier,
                )
                if (periodChange != null && periodChangePct != null) {
                    Spacer(Modifier.height(4.dp))
                    AccountPeriodChangeBadge(
                        change = periodChange,
                        changePct = periodChangePct,
                        range = chartRange,
                    )
                }
            }
        }
    }
}

// ── Account history card ───────────────────────────────────────────────────

@Composable
private fun AccountHistoryCard(
    history: List<SingleAccountPoint>,
    chartRange: ChartRange,
    account: com.aracem.joyufy.domain.model.Account,
    onRangeChange: (ChartRange) -> Unit,
) {
    val strings = LocalStrings.current
    var chartMode by remember { mutableStateOf(ChartMode.AREA) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.evolution,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TooltipIconButton(
                label = strings.changeView,
                icon = if (chartMode == ChartMode.AREA) Icons.AutoMirrored.Filled.List else Icons.Default.DateRange,
                onClick = { chartMode = if (chartMode == ChartMode.AREA) ChartMode.BARS else ChartMode.AREA },
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.joyufyColors.contentSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        ChartRangeSelector(selected = chartRange, onSelect = onRangeChange)
        Spacer(Modifier.height(8.dp))
        SingleAccountChart(
            points = history,
            account = account,
            mode = chartMode,
        )
    }
}

// ── Subcomponents ─────────────────────────────────────────────────────────

private data class TransactionMonthGroup(
    val key: Int,
    val label: String,
    val income: Double,
    val expenses: Double,
    val net: Double,
    val transactions: List<Transaction>,
)

private fun buildTransactionMonthGroups(transactions: List<Transaction>): List<TransactionMonthGroup> =
    transactions
        .groupBy { transactionMonthKey(it.date) }
        .toSortedMap(compareByDescending { it })
        .map { (key, monthTransactions) ->
            val income = monthTransactions
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount }
            val expenses = monthTransactions
                .filter { it.type != TransactionType.INCOME }
                .sumOf { it.amount }
            TransactionMonthGroup(
                key = key,
                label = monthLabel(monthTransactions.first().date),
                income = income,
                expenses = expenses,
                net = income - expenses,
                transactions = monthTransactions.sortedWith(
                    compareByDescending<Transaction> { it.date }.thenByDescending { it.id },
                ),
            )
        }

private fun buildRunningBalanceByTransactionId(transactions: List<Transaction>): Map<Long, Double> {
    var balance = 0.0
    return transactions
        .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id })
        .associate { tx ->
            balance += tx.signedAmount()
            tx.id to balance
        }
}

private fun Transaction.signedAmount(): Double =
    if (type == TransactionType.INCOME) amount else -amount

private fun transactionMonthKey(epochMillis: Long): Int {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return local.year * 100 + local.monthNumber
}

private fun monthLabel(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%04d".format(local.monthNumber, local.year)
}

@Composable
private fun TransactionMonthHeader(group: TransactionMonthGroup) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings.monthSubtotal,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = group.net.formatCurrency(),
                style = MaterialTheme.typography.labelLarge,
                color = if (group.net >= 0.0) Positive else Negative,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionFilterBar(
    searchQuery: String,
    searchFocusRequester: FocusRequester,
    onSearchChange: (String) -> Unit,
    filterType: TransactionType?,
    onTypeChange: (TransactionType) -> Unit,
    filterCategory: String?,
    onCategoryChange: (String) -> Unit,
    filterDateFrom: String,
    onDateFromChange: (String) -> Unit,
    filterDateTo: String,
    onDateToChange: (String) -> Unit,
    filterAmountMin: String,
    onAmountMinChange: (String) -> Unit,
    filterAmountMax: String,
    onAmountMaxChange: (String) -> Unit,
    transfersOnly: Boolean,
    onTransfersOnlyChange: (Boolean) -> Unit,
    availableCategories: List<String>,
) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(strings.searchDescriptionCategory, style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = MaterialTheme.joyufyColors.contentSecondary,
                    modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = strings.clearFilters,
                            tint = MaterialTheme.joyufyColors.contentSecondary,
                            modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                focusedLabelColor = Accent,
                unfocusedBorderColor = MaterialTheme.joyufyColors.border,
            ),
            textStyle = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactFilterField(
                value = filterDateFrom,
                onValueChange = onDateFromChange,
                label = strings.dateFrom,
                placeholder = "01/01/2026",
                modifier = Modifier.weight(1f),
            )
            CompactFilterField(
                value = filterDateTo,
                onValueChange = onDateToChange,
                label = strings.dateTo,
                placeholder = "31/12/2026",
                modifier = Modifier.weight(1f),
            )
            CompactFilterField(
                value = filterAmountMin,
                onValueChange = onAmountMinChange,
                label = strings.amountMin,
                placeholder = strings.placeholderAmount,
                modifier = Modifier.weight(1f),
            )
            CompactFilterField(
                value = filterAmountMax,
                onValueChange = onAmountMaxChange,
                label = strings.amountMax,
                placeholder = strings.placeholderAmount,
                modifier = Modifier.weight(1f),
            )
        }

        // Type chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = transfersOnly,
                onClick = { onTransfersOnlyChange(!transfersOnly) },
                label = { Text(strings.transfersOnly, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent.copy(alpha = 0.15f),
                    selectedLabelColor = Accent,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.joyufyColors.contentSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = transfersOnly,
                    selectedBorderColor = Accent,
                    borderColor = MaterialTheme.joyufyColors.border,
                ),
                modifier = Modifier.height(28.dp),
            )

            listOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER).forEach { type ->
                val selected = filterType == type
                FilterChip(
                    selected = selected,
                    onClick = { onTypeChange(type) },
                    label = { Text(when (type) {
                        TransactionType.INCOME -> strings.transactionIncome
                        TransactionType.EXPENSE -> strings.transactionExpense
                        TransactionType.TRANSFER -> strings.transactionTransfer
                    }, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent.copy(alpha = 0.15f),
                        selectedLabelColor = Accent,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.joyufyColors.contentSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = Accent,
                        borderColor = MaterialTheme.joyufyColors.border,
                    ),
                    modifier = Modifier.height(28.dp),
                )
            }

            // Category chips — only show if there are categories
            availableCategories.forEach { cat ->
                val selected = filterCategory == cat
                FilterChip(
                    selected = selected,
                    onClick = { onCategoryChange(cat) },
                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent.copy(alpha = 0.15f),
                        selectedLabelColor = Accent,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.joyufyColors.contentSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = Accent,
                        borderColor = MaterialTheme.joyufyColors.border,
                    ),
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            focusedLabelColor = Accent,
            unfocusedBorderColor = MaterialTheme.joyufyColors.border,
        ),
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    allAccounts: List<com.aracem.joyufy.domain.model.Account>,
    runningBalance: Double? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    indented: Boolean = false,
) {
    val strings = LocalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val actionsVisible = hovered || focused
    val relatedAccount = allAccounts.find { it.id == transaction.relatedAccountId }
    val relatedName = relatedAccount?.name
    // Transfers and investment deposits are stored as EXPENSE/INCOME with a relatedAccountId
    val isTransferOut = transaction.type == TransactionType.EXPENSE && relatedName != null
    val isTransferIn  = transaction.type == TransactionType.INCOME  && relatedName != null
    val isPositive = transaction.type == TransactionType.INCOME
    val amountColor = if (isPositive) Positive else Negative
    val prefix = if (isPositive) "+" else "-"
    val displayLabel = when {
        isTransferOut -> strings.transferOut
        isTransferIn  -> strings.transferIn
        else          -> when (transaction.type) {
            TransactionType.INCOME -> strings.transactionIncome
            TransactionType.EXPENSE -> strings.transactionExpense
            TransactionType.TRANSFER -> strings.transactionTransfer
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 16.dp else 0.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (indented) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color = if (focused) Accent.copy(alpha = 0.55f) else Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            )
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                transaction.category?.let { cat ->
                    Spacer(Modifier.width(6.dp))
                    Text("·", color = MaterialTheme.joyufyColors.contentSecondary,
                        style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    Text(cat, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.joyufyColors.contentSecondary)
                }
            }
            transaction.description?.let { desc ->
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            relatedName?.let {
                Spacer(Modifier.height(2.dp))
                Text("→ $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.joyufyColors.contentSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = transaction.date.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$prefix${transaction.amount.formatCurrency()}",
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
            )
            if (runningBalance != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${strings.runningBalance}: ${runningBalance.formatCurrency()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
            }
        }
        RowActions(
            visible = actionsVisible,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun SnapshotRow(
    snapshot: InvestmentSnapshot,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val actionsVisible = hovered || focused
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = if (focused) Accent.copy(alpha = 0.55f) else Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            )
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = snapshot.weekDate.formatWeekRange(strings.week),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
        }
        Text(
            text = snapshot.totalValue.formatCurrency(),
            style = MaterialTheme.typography.titleMedium,
            color = Positive,
        )
        RowActions(
            visible = actionsVisible,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun RowActions(
    visible: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    Spacer(Modifier.width(4.dp))
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(32.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
        ) {
            Row {
                TooltipIconButton(
                    label = strings.edit,
                    icon = Icons.Default.Edit,
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.joyufyColors.contentSecondary,
                    iconSize = 16.dp,
                )
                TooltipIconButton(
                    label = strings.delete,
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.joyufyColors.contentDisabled,
                    iconSize = 16.dp,
                )
            }
        }
    }
}

@Composable
private fun AccountPeriodChangeBadge(
    change: Double,
    changePct: Double,
    range: ChartRange,
) {
    val strings = LocalStrings.current
    val isPositive = change >= 0
    val color = if (isPositive) Positive else Negative
    val sign = if (isPositive) "+" else ""
    val rangeLabel = when (range) {
        ChartRange.ONE_WEEK     -> strings.rangeOneWeek
        ChartRange.ONE_MONTH    -> strings.rangeOneMonth
        ChartRange.THREE_MONTHS -> strings.rangeThreeMonths
        ChartRange.SIX_MONTHS  -> strings.rangeSixMonths
        ChartRange.YTD         -> strings.rangeYtd
        ChartRange.ONE_YEAR    -> strings.rangeOneYear
        ChartRange.ALL         -> strings.rangeAll
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$sign${change.formatCurrency()}  ($sign${changePct.formatPercent()})",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = rangeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
    }
}

@Composable
private fun EmptyListHint(title: String, subtitle: String, icon: ImageVector) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Negative),
            ) { Text("Eliminar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
