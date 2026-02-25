package com.aracem.joyufy.ui.account

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.aracem.joyufy.ui.dashboard.ChartMode
import com.aracem.joyufy.ui.dashboard.ChartRange
import com.aracem.joyufy.ui.dashboard.ChartRangeSelector
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.Positive
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
) {
    val viewModel: AccountDetailViewModel = koinInject { parametersOf(accountId) }
    val state by viewModel.uiState.collectAsState()

    var showAddTransaction by remember { mutableStateOf(false) }
    var showAddSnapshot by remember { mutableStateOf(false) }
    var showEditAccount by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var editingSnapshot by remember { mutableStateOf<InvestmentSnapshot?>(null) }
    var confirmDeleteTxId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteSnapshotId by remember { mutableStateOf<Long?>(null) }

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
                        contentDescription = "Volver",
                        tint = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                if (account.logoUrl != null) {
                    AccountLogo(color = account.color, logoUrl = account.logoUrl, size = 32.dp)
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
                        text = account.type.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }
                // Buttons — investment accounts have both actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showEditAccount = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar cuenta",
                            tint = MaterialTheme.joyufyColors.contentSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (account.type == AccountType.INVESTMENT) {
                        OutlinedButton(
                            onClick = { showAddSnapshot = true },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Accent),
                        ) {
                            Text("Actualizar valor", color = Accent)
                        }
                    }
                    Button(
                        onClick = { showAddTransaction = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Añadir transacción")
                    }
                }
            }
        }

        // ── Balance ───────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(start = 48.dp)) {
                Text(
                    text = "Balance actual",
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

        // ── Investment snapshots (valor de mercado semanal) ───────────────
        if (account.type == AccountType.INVESTMENT) {
            item {
                Text(
                    text = "Valor de mercado semanal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (state.snapshots.isEmpty()) {
                item { EmptyListHint("Sin registros semanales — pulsa \"Actualizar valor\" para añadir") }
            } else {
                items(state.snapshots, key = { it.id }) { snapshot ->
                    SnapshotRow(
                        snapshot = snapshot,
                        onEdit = { editingSnapshot = snapshot },
                        onDelete = { confirmDeleteSnapshotId = snapshot.id },
                    )
                }
            }
            item { HorizontalDivider(color = MaterialTheme.joyufyColors.border) }
        }

        // ── Transactions (all account types) ──────────────────────────────
        item {
            Text(
                text = "Transacciones",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (state.transactions.isEmpty()) {
            item { EmptyListHint("Aún no hay transacciones") }
        } else {
            items(state.transactions, key = { it.id }) { tx ->
                TransactionRow(
                    transaction = tx,
                    allAccounts = state.allAccounts,
                    onEdit = { editingTransaction = tx },
                    onDelete = { confirmDeleteTxId = tx.id },
                )
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
            title = "¿Eliminar transacción?",
            text = "Esta acción no se puede deshacer.",
            onConfirm = { viewModel.deleteTransaction(txId); confirmDeleteTxId = null },
            onDismiss = { confirmDeleteTxId = null },
        )
    }

    confirmDeleteSnapshotId?.let { snapId ->
        DeleteConfirmDialog(
            title = "¿Eliminar registro semanal?",
            text = "Esta acción no se puede deshacer.",
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
                text = "Evolución",
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
                    contentDescription = "Cambiar vista",
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

@Composable
private fun TransactionRow(
    transaction: Transaction,
    allAccounts: List<com.aracem.joyufy.domain.model.Account>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val relatedAccount = allAccounts.find { it.id == transaction.relatedAccountId }
    val relatedName = relatedAccount?.name
    // Transfers and investment deposits are stored as EXPENSE/INCOME with a relatedAccountId
    val isTransferOut = transaction.type == TransactionType.EXPENSE && relatedName != null
    val isTransferIn  = transaction.type == TransactionType.INCOME  && relatedName != null
    val isPositive = transaction.type == TransactionType.INCOME
    val amountColor = if (isPositive) Positive else Negative
    val prefix = if (isPositive) "+" else "-"
    val displayLabel = when {
        isTransferOut -> "Transferencia →"
        isTransferIn  -> "Transferencia ←"
        else          -> transaction.type.label
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Icon(Icons.Default.Edit, contentDescription = "Editar",
                tint = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
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
                text = "Semana del ${snapshot.weekDate.formatDate()}",
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
            Icon(Icons.Default.Edit, contentDescription = "Editar",
                tint = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
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
    val isPositive = change >= 0
    val color = if (isPositive) Positive else Negative
    val sign = if (isPositive) "+" else ""
    val rangeLabel = when (range) {
        ChartRange.ONE_MONTH    -> "en el último mes"
        ChartRange.THREE_MONTHS -> "en los últimos 3 meses"
        ChartRange.SIX_MONTHS  -> "en los últimos 6 meses"
        ChartRange.YTD         -> "en lo que va de año"
        ChartRange.ONE_YEAR    -> "en el último año"
        ChartRange.ALL         -> "desde el inicio"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$sign${change.formatCurrency()}  ($sign${"%.2f".format(changePct)}%)",
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
private fun EmptyListHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.joyufyColors.contentSecondary)
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

private val AccountType.label: String
    get() = when (this) {
        AccountType.BANK -> "Banco"
        AccountType.INVESTMENT -> "Inversión"
        AccountType.CASH -> "Efectivo"
    }

private val TransactionType.label: String
    get() = when (this) {
        TransactionType.INCOME -> "Ingreso"
        TransactionType.EXPENSE -> "Gasto"
        TransactionType.TRANSFER -> "Transferencia"
    }

private fun Long.formatDate(): String {
    val instant = Instant.fromEpochMilliseconds(this)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%04d".format(local.dayOfMonth, local.monthNumber, local.year)
}
