package com.aracem.nexlify.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aracem.nexlify.domain.model.AccountType
import com.aracem.nexlify.domain.model.InvestmentSnapshot
import com.aracem.nexlify.domain.model.Transaction
import com.aracem.nexlify.domain.model.TransactionType
import com.aracem.nexlify.ui.components.formatCurrency
import com.aracem.nexlify.ui.theme.Accent
import com.aracem.nexlify.ui.theme.Negative
import com.aracem.nexlify.ui.theme.Positive
import com.aracem.nexlify.ui.theme.nexlifyColors
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
                        tint = MaterialTheme.nexlifyColors.contentSecondary,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(account.color)
                )
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
                        color = MaterialTheme.nexlifyColors.contentSecondary,
                    )
                }
                // Add button
                Button(
                    onClick = {
                        if (account.type == AccountType.INVESTMENT) showAddSnapshot = true
                        else showAddTransaction = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (account.type == AccountType.INVESTMENT) "Actualizar valor" else "Añadir transacción")
                }
            }
        }

        // ── Balance ───────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(start = 48.dp)) {
                Text(
                    text = "Balance actual",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nexlifyColors.contentSecondary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.balance.formatCurrency(),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (state.balance >= 0) Positive else Negative,
                )
            }
        }

        item { HorizontalDivider(color = MaterialTheme.nexlifyColors.border) }

        // ── List heading ──────────────────────────────────────────────────
        item {
            Text(
                text = if (account.type == AccountType.INVESTMENT) "Historial semanal" else "Transacciones",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // ── Investment snapshots ──────────────────────────────────────────
        if (account.type == AccountType.INVESTMENT) {
            if (state.snapshots.isEmpty()) {
                item { EmptyListHint("Aún no hay registros semanales") }
            } else {
                items(state.snapshots, key = { it.id }) { snapshot ->
                    SnapshotRow(
                        snapshot = snapshot,
                        onDelete = { viewModel.deleteSnapshot(snapshot.id) },
                    )
                }
            }
        }

        // ── Bank / Cash transactions ──────────────────────────────────────
        if (account.type != AccountType.INVESTMENT) {
            if (state.transactions.isEmpty()) {
                item { EmptyListHint("Aún no hay transacciones") }
            } else {
                items(state.transactions, key = { it.id }) { tx ->
                    TransactionRow(
                        transaction = tx,
                        allAccounts = state.allAccounts,
                        onDelete = { viewModel.deleteTransaction(tx.id) },
                    )
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (showAddTransaction) {
        AddTransactionDialog(
            availableAccounts = state.allAccounts,
            onDismiss = { showAddTransaction = false },
            onConfirm = { type, amount, category, desc, relatedId, date ->
                viewModel.addTransaction(type, amount, category, desc, relatedId, date)
            },
        )
    }

    if (showAddSnapshot) {
        AddSnapshotDialog(
            accountName = account.name,
            currentValue = state.snapshots.firstOrNull()?.totalValue,
            onDismiss = { showAddSnapshot = false },
            onConfirm = { viewModel.addSnapshot(it) },
        )
    }
}

// ── Subcomponents ─────────────────────────────────────────────────────────

@Composable
private fun TransactionRow(
    transaction: Transaction,
    allAccounts: List<com.aracem.nexlify.domain.model.Account>,
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
                    color = MaterialTheme.nexlifyColors.contentSecondary,
                )
                transaction.category?.let { cat ->
                    Spacer(Modifier.width(6.dp))
                    Text("·", color = MaterialTheme.nexlifyColors.contentSecondary,
                        style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    Text(cat, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.nexlifyColors.contentSecondary)
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
                    color = MaterialTheme.nexlifyColors.contentSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = transaction.date.formatDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.nexlifyColors.contentSecondary,
            )
        }

        Text(
            text = "$prefix${transaction.amount.formatCurrency()}",
            style = MaterialTheme.typography.titleMedium,
            color = amountColor,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                tint = MaterialTheme.nexlifyColors.contentDisabled,
                modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SnapshotRow(
    snapshot: InvestmentSnapshot,
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
                color = MaterialTheme.nexlifyColors.contentSecondary,
            )
        }
        Text(
            text = snapshot.totalValue.formatCurrency(),
            style = MaterialTheme.typography.titleMedium,
            color = Positive,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                tint = MaterialTheme.nexlifyColors.contentDisabled,
                modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EmptyListHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.nexlifyColors.contentSecondary)
    }
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
        TransactionType.INVESTMENT_DEPOSIT -> "Depósito inversión"
    }

private fun Long.formatDate(): String {
    val instant = Instant.fromEpochMilliseconds(this)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%04d".format(local.dayOfMonth, local.monthNumber, local.year)
}
