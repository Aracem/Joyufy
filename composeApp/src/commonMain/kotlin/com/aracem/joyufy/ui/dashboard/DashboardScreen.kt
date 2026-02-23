package com.aracem.joyufy.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.ui.backup.BackupEvent
import com.aracem.joyufy.ui.backup.BackupViewModel
import com.aracem.joyufy.ui.components.*
import com.aracem.joyufy.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DashboardScreen(
    onAccountClick: (Account) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    backupViewModel: BackupViewModel = koinInject(),
    viewModel: DashboardViewModel = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val backupEvent by backupViewModel.event.collectAsState()

    // Confirm dialog before destructive import
    var showImportConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(backupEvent) {
        if (backupEvent is BackupEvent.ImportReady) {
            showImportConfirm = (backupEvent as BackupEvent.ImportReady).onConfirm
        }
    }
    if (showImportConfirm != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = null; backupViewModel.reset() },
            title = { Text("¿Restaurar backup?") },
            text = { Text("Se borrarán todos los datos actuales y se reemplazarán con los del archivo. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = { showImportConfirm?.invoke(); showImportConfirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text("Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = null; backupViewModel.reset() }) {
                    Text("Cancelar")
                }
            },
        )
    }

    // Snackbar for success/error
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(backupEvent) {
        when (val ev = backupEvent) {
            is BackupEvent.Success -> { snackbarHostState.showSnackbar(ev.message); backupViewModel.reset() }
            is BackupEvent.Error   -> { snackbarHostState.showSnackbar(ev.message); backupViewModel.reset() }
            else -> {}
        }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Banner de snapshots pendientes
        if (state.accountsMissingSnapshot.isNotEmpty()) {
            item {
                MissingSnapshotBanner(
                    accounts = state.accountsMissingSnapshot,
                    onAccountClick = onAccountClick,
                    onDismiss = viewModel::dismissMissingSnapshotBanner,
                )
            }
        }

        // Header — total patrimonio
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Patrimonio total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    DashboardMenu(
                        onExport = onExport,
                        onImport = onImport,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.totalWealth.formatCurrency(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val change = state.periodChange
                val changePct = state.periodChangePct
                if (change != null && changePct != null) {
                    Spacer(Modifier.height(4.dp))
                    PeriodChangeBadge(
                        change = change,
                        changePct = changePct,
                        range = state.chartRange,
                    )
                }
            }
        }

        // Gráfica
        item {
            WealthChartCard(
                points = state.wealthHistory,
                mode = state.chartMode,
                range = state.chartRange,
                accounts = state.accountSummaries,
                hiddenAccountIds = state.hiddenAccountIds,
                showTotal = state.showTotal,
                onToggleMode = viewModel::toggleChartMode,
                onRangeChange = viewModel::setChartRange,
                onToggleAccount = viewModel::toggleAccountVisibility,
                onToggleTotal = viewModel::toggleTotal,
            )
        }

        // Sección cuentas
        item {
            Text(
                text = "Cuentas",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        items(state.accountSummaries) { summary ->
            AccountCard(
                account = summary.account,
                balance = summary.balance,
                onClick = { onAccountClick(summary.account) },
            )
        }

        // Empty state
        if (state.accountSummaries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Sin cuentas todavía",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Añade tu primera cuenta desde Ajustes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
    } // end Scaffold
}

@Composable
private fun WealthChartCard(
    points: List<WealthPoint>,
    mode: ChartMode,
    range: ChartRange,
    accounts: List<AccountSummary>,
    hiddenAccountIds: Set<Long>,
    showTotal: Boolean,
    onToggleMode: () -> Unit,
    onRangeChange: (ChartRange) -> Unit,
    onToggleAccount: (Long) -> Unit,
    onToggleTotal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Evolución",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleMode, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (mode == ChartMode.AREA) Icons.AutoMirrored.Filled.List else Icons.Default.DateRange,
                    contentDescription = "Cambiar vista",
                    tint = MaterialTheme.joyufyColors.contentSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ChartRangeSelector(selected = range, onSelect = onRangeChange)
        Spacer(Modifier.height(12.dp))
        WealthChart(
            points = points,
            mode = mode,
            hiddenAccountIds = hiddenAccountIds,
            showTotal = showTotal,
        )
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ChartLegendSelector(
                accounts = accounts,
                hiddenAccountIds = hiddenAccountIds,
                showTotal = showTotal,
                onToggleAccount = onToggleAccount,
                onToggleTotal = onToggleTotal,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegendSelector(
    accounts: List<AccountSummary>,
    hiddenAccountIds: Set<Long>,
    showTotal: Boolean,
    onToggleAccount: (Long) -> Unit,
    onToggleTotal: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Total chip
        LegendChip(
            label = "Total",
            color = Accent,
            visible = showTotal,
            onClick = onToggleTotal,
        )
        // Per-account chips
        accounts.forEach { summary ->
            LegendChip(
                label = summary.account.name,
                color = summary.account.color,
                visible = summary.account.id !in hiddenAccountIds,
                onClick = { onToggleAccount(summary.account.id) },
            )
        }
    }
}

@Composable
private fun LegendChip(
    label: String,
    color: Color,
    visible: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (visible) 1f else 0.35f
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = if (visible) 0.12f else 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = alpha),
        )
    }
}

@Composable
private fun PeriodChangeBadge(
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
fun ChartRangeSelector(
    selected: ChartRange,
    onSelect: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        ChartRange.ONE_MONTH to "1M",
        ChartRange.THREE_MONTHS to "3M",
        ChartRange.SIX_MONTHS to "6M",
        ChartRange.YTD to "YTD",
        ChartRange.ONE_YEAR to "1A",
        ChartRange.ALL to "Todo",
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (range, label) ->
            val isSelected = range == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(range) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.joyufyColors.contentSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.joyufyColors.border,
                    selectedBorderColor = Accent,
                ),
                modifier = Modifier.height(28.dp),
            )
        }
    }
}

@Composable
private fun DashboardMenu(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Más opciones",
                tint = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Exportar datos") },
                onClick = { expanded = false; onExport() },
            )
            DropdownMenuItem(
                text = { Text("Importar datos") },
                onClick = { expanded = false; onImport() },
            )
        }
    }
}
