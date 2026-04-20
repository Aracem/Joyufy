package com.aracem.joyufy.ui.account

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.AccountLogo
import com.aracem.joyufy.ui.components.AccountLogoInitials
import com.aracem.joyufy.ui.components.SingleAccountChart
import com.aracem.joyufy.ui.components.formatCurrency
import com.aracem.joyufy.ui.components.formatPercent
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
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val viewModel: AccountDetailViewModel = koinInject { parametersOf(accountId) }
    val state by viewModel.uiState.collectAsState()

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

    val availableCategories by remember {
        derivedStateOf {
            state.transactions
                .mapNotNull { it.category?.ifBlank { null } }
                .distinct()
                .sorted()
        }
    }

    val filteredTransactions by remember {
        derivedStateOf {
            state.transactions
                .let { txs -> if (filterType != null) txs.filter { it.type == filterType } else txs }
                .let { txs -> if (filterCategory != null) txs.filter { it.category == filterCategory } else txs }
                .let { txs ->
                    if (searchQuery.isBlank()) txs
                    else {
                        val q = searchQuery.trim().lowercase()
                        txs.filter {
                            it.description?.lowercase()?.contains(q) == true ||
                            it.category?.lowercase()?.contains(q) == true
                        }
                    }
                }
        }
    }

    val isFiltered = searchQuery.isNotBlank() || filterType != null || filterCategory != null

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    val account = state.account ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.goBack,
                        tint = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }
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
                // Buttons — investment accounts have both actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showEditAccount = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = strings.editAccount,
                            tint = MaterialTheme.joyufyColors.contentSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (account.type == AccountType.INVESTMENT) {
                        OutlinedButton(
                            onClick = { showAddSnapshot = true },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Accent),
                        ) {
                            Text(strings.updateValue, color = Accent)
                        }
                    }
                    Button(
                        onClick = { showAddTransaction = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.addTransaction)
                    }
                }
            }
        }

        // ── Balance ───────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(start = 48.dp)) {
                Text(
                    text = strings.currentBalance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                Spacer(Modifier.height(2.dp))
                val animatedBalance by animateFloatAsState(
                    targetValue = state.balance.toFloat(),
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                )
                Text(
                    text = animatedBalance.toDouble().formatCurrency(),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (state.balance >= 0) Positive else Negative,
                    modifier = if (account.type == AccountType.INVESTMENT)
                        Modifier.clickable { showAddSnapshot = true }
                    else Modifier,
                )
                val change = state.periodChange
                val changePct = state.periodChangePct
                if (change != null && changePct != null) {
                    Spacer(Modifier.height(4.dp))
                    AccountPeriodChangeBadge(
                        change = change,
                        changePct = changePct,
                        range = state.chartRange,
                    )
                }
            }
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
                Text(
                    text = strings.weeklyValue,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (state.investmentFeed.isEmpty()) {
                item { EmptyListHint(strings.noWeeklyRecords, strings.noWeeklyRecordsHint, Icons.Default.DateRange) }
            } else {
                items(state.investmentFeed, key = { item ->
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
                            onClick = { searchQuery = ""; filterType = null; filterCategory = null },
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
                        onSearchChange = { searchQuery = it },
                        filterType = filterType,
                        onTypeChange = { filterType = if (filterType == it) null else it },
                        filterCategory = filterCategory,
                        onCategoryChange = { filterCategory = if (filterCategory == it) null else it },
                        availableCategories = availableCategories,
                    )
                }
            }

            if (state.transactions.isEmpty()) {
                item { EmptyListHint(strings.noTransactions, strings.noTransactionsHint, Icons.AutoMirrored.Filled.List) }
            } else if (filteredTransactions.isEmpty()) {
                item { EmptyListHint(strings.noSearchResults, strings.noSearchResultsHint, Icons.Default.Search) }
            } else {
                items(filteredTransactions, key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx,
                        allAccounts = state.allAccounts,
                        onEdit = { editingTransaction = tx },
                        onDelete = { confirmDeleteTxId = tx.id },
                    )
                }
            }
        }
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
            IconButton(
                onClick = { chartMode = if (chartMode == ChartMode.AREA) ChartMode.BARS else ChartMode.AREA },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (chartMode == ChartMode.AREA) Icons.AutoMirrored.Filled.List else Icons.Default.DateRange,
                    contentDescription = strings.changeView,
                    tint = MaterialTheme.joyufyColors.contentSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    filterType: TransactionType?,
    onTypeChange: (TransactionType) -> Unit,
    filterCategory: String?,
    onCategoryChange: (String) -> Unit,
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
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                focusedLabelColor = Accent,
                unfocusedBorderColor = MaterialTheme.joyufyColors.border,
            ),
            textStyle = MaterialTheme.typography.bodySmall,
        )

        // Type chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
private fun TransactionRow(
    transaction: Transaction,
    allAccounts: List<com.aracem.joyufy.domain.model.Account>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    indented: Boolean = false,
) {
    val strings = LocalStrings.current
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

        Text(
            text = "$prefix${transaction.amount.formatCurrency()}",
            style = MaterialTheme.typography.titleMedium,
            color = amountColor,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = strings.edit,
                tint = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = strings.delete,
                tint = MaterialTheme.joyufyColors.contentDisabled,
                modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SnapshotRow(
    snapshot: InvestmentSnapshot,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = strings.edit,
                tint = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = strings.delete,
                tint = MaterialTheme.joyufyColors.contentDisabled,
                modifier = Modifier.size(16.dp))
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

