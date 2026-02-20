package com.aracem.nexlify.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    color = ContentSecondary,
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
                onToggleMode = viewModel::toggleChartMode,
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
                            color = ContentSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Añade tu primera cuenta desde Ajustes",
                            style = MaterialTheme.typography.bodySmall,
                            color = ContentSecondary.copy(alpha = 0.6f),
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
    onToggleMode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDefault, MaterialTheme.shapes.medium)
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
                    tint = ContentSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        WealthChart(points = points, mode = mode)
    }
}
