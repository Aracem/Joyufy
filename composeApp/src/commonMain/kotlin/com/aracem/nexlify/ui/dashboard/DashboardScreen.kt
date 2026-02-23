package com.aracem.nexlify.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aracem.nexlify.domain.model.Account
import com.aracem.nexlify.ui.components.*
import com.aracem.nexlify.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun DashboardScreen(
    onAccountClick: (Account) -> Unit,
    viewModel: DashboardViewModel = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

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
                Text(
                    text = "Patrimonio total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nexlifyColors.contentSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.totalWealth.formatCurrency(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Gráfica
        item {
            WealthChartCard(
                points = state.wealthHistory,
                mode = state.chartMode,
                range = state.chartRange,
                accounts = state.accountSummaries,
                onToggleMode = viewModel::toggleChartMode,
                onRangeChange = viewModel::setChartRange,
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
                            color = MaterialTheme.nexlifyColors.contentSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Añade tu primera cuenta desde Ajustes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.nexlifyColors.contentSecondary.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WealthChartCard(
    points: List<WealthPoint>,
    mode: ChartMode,
    range: ChartRange,
    accounts: List<AccountSummary>,
    onToggleMode: () -> Unit,
    onRangeChange: (ChartRange) -> Unit,
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
                    tint = MaterialTheme.nexlifyColors.contentSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ChartRangeSelector(selected = range, onSelect = onRangeChange)
        Spacer(Modifier.height(12.dp))
        WealthChart(points = points, mode = mode)
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ChartLegend(accounts = accounts)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(accounts: List<AccountSummary>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        accounts.forEach { summary ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(summary.account.color)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = summary.account.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.nexlifyColors.contentSecondary,
                )
            }
        }
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
                    labelColor = MaterialTheme.nexlifyColors.contentSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.nexlifyColors.border,
                    selectedBorderColor = Accent,
                ),
                modifier = Modifier.height(28.dp),
            )
        }
    }
}
