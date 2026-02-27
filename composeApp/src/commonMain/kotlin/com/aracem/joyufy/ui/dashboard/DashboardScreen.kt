package com.aracem.joyufy.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.ui.openUrl
import com.aracem.joyufy.ui.components.*
import com.aracem.joyufy.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DashboardScreen(
    onAccountClick: (Account) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    viewModel: DashboardViewModel = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()

    // Shared map: accountId -> center Y in window coords, used for drag-to-reorder
    val itemCenterY = remember { mutableStateMapOf<Long, Float>() }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    var wealthClickCount by remember { mutableStateOf(0) }
    var showConfetti by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wealthScale = remember { Animatable(1f) }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Banner de actualización disponible
        state.updateInfo?.let { info ->
            item {
                UpdateBanner(
                    version = info.latestVersion,
                    onOpenRelease = { openUrl(info.releaseUrl) },
                    onDismiss = viewModel::dismissUpdateBanner,
                )
            }
        }

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
                val animatedWealth by animateFloatAsState(
                    targetValue = state.totalWealth.toFloat(),
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                )
                val easterEggFraction = (wealthClickCount / 20f).coerceIn(0f, 1f)
                val wealthColor = lerp(
                    start = MaterialTheme.colorScheme.onSurface,
                    stop = Positive,
                    fraction = easterEggFraction,
                )
                Text(
                    text = animatedWealth.toDouble().formatCurrency(),
                    style = MaterialTheme.typography.displayLarge,
                    color = wealthColor,
                    modifier = Modifier
                        .scale(wealthScale.value)
                        .clickable {
                            wealthClickCount++
                            scope.launch {
                                wealthScale.animateTo(
                                    targetValue = 1.05f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessHigh,
                                    ),
                                )
                                wealthScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                            if (wealthClickCount >= 20) {
                                showConfetti = true
                                scope.launch {
                                    delay(4000)
                                    showConfetti = false
                                    wealthClickCount = 0
                                }
                            }
                        },
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

        // Resumen mensual
        state.monthlySummary?.let { summary ->
            item {
                MonthlySummaryCard(summary = summary)
            }
        }

        // Sección cuentas
        item {
            Text(
                text = "Cuentas",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        itemsIndexed(
            items = state.accountSummaries,
            key = { _, summary -> summary.account.id },
        ) { index, summary ->
            DraggableAccountCard(
                summary = summary,
                index = index,
                totalCount = state.accountSummaries.size,
                itemCenterY = itemCenterY,
                onAccountClick = onAccountClick,
                onReorder = viewModel::reorderAccounts,
                modifier = Modifier.animateItem(),
            )
        }

        // Empty state
        if (state.accountSummaries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Sin cuentas todavía",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Añade tu primera cuenta desde el botón +",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }

    if (showConfetti) {
        ConfettiOverlay(modifier = Modifier.matchParentSize())
    }
    } // end Box
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
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0.35f,
        animationSpec = tween(200),
    )
    val animatedBgAlpha by animateFloatAsState(
        targetValue = if (visible) 0.12f else 0.06f,
        animationSpec = tween(200),
    )
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = animatedBgAlpha))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = animatedAlpha))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = animatedAlpha),
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
        ChartRange.ONE_WEEK     -> "en la última semana"
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
        ChartRange.ONE_WEEK to "1S",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableAccountCard(
    summary: AccountSummary,
    index: Int,
    totalCount: Int,
    itemCenterY: androidx.compose.runtime.snapshots.SnapshotStateMap<Long, Float>,
    onAccountClick: (Account) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    // Absolute Y of cursor in window during drag
    var cursorY by remember { mutableStateOf(0f) }

    AccountCard(
        account = summary.account,
        balance = summary.balance,
        onClick = { onAccountClick(summary.account) },
        isDragging = isDragging,
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Reordenar",
                tint = if (isDragging)
                    MaterialTheme.joyufyColors.contentSecondary
                else
                    MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(summary.account.id) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                isDragging = true
                                // startOffset is relative to the handle; convert to window Y
                                cursorY = (itemCenterY[summary.account.id] ?: 0f)
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                cursorY += dragAmount.y
                                // Find which item's center is closest to the cursor
                                val targetId = itemCenterY.minByOrNull { (_, cy) ->
                                    kotlin.math.abs(cy - cursorY)
                                }?.key ?: return@detectDragGestures
                                // Map targetId back to index in current list
                                // We need the current order — use itemCenterY sorted by Y
                                val sortedIds = itemCenterY.entries
                                    .sortedBy { it.value }
                                    .map { it.key }
                                val currentIndex = sortedIds.indexOf(summary.account.id)
                                val targetIndex = sortedIds.indexOf(targetId)
                                if (targetIndex != -1 && currentIndex != -1 && targetIndex != currentIndex) {
                                    onReorder(currentIndex, targetIndex)
                                }
                            },
                        )
                    },
            )
        },
        modifier = modifier.onGloballyPositioned { coords ->
            // Store center Y of this card in window coordinates
            val centerY = coords.positionInWindow().y + coords.size.height / 2f
            itemCenterY[summary.account.id] = centerY
        },
    )
}

@Composable
private fun MonthlySummaryCard(summary: MonthlySummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(20.dp),
    ) {
        Text(
            text = "Este mes",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))

        // Income / Expenses / Net row
        Row(modifier = Modifier.fillMaxWidth()) {
            MonthlyStat(
                label = "Ingresos",
                amount = summary.income,
                color = Positive,
                modifier = Modifier.weight(1f),
            )
            MonthlyStat(
                label = "Gastos",
                amount = summary.expenses,
                color = Negative,
                modifier = Modifier.weight(1f),
            )
            MonthlyStat(
                label = "Balance",
                amount = summary.net,
                color = if (summary.net >= 0) Positive else Negative,
                modifier = Modifier.weight(1f),
            )
        }

        if (summary.topCategories.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Top gastos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.topCategories.forEach { cat ->
                    CategoryBar(cat)
                }
            }
        }
    }
}

@Composable
private fun MonthlyStat(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
        Spacer(Modifier.height(2.dp))
        val sign = if (label == "Ingresos") "+" else if (label == "Gastos") "-" else if (amount >= 0) "+" else ""
        Text(
            text = "$sign${amount.formatCurrency()}",
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

@Composable
private fun CategoryBar(cat: CategoryBreakdown) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = cat.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
            Text(
                text = cat.amount.formatCurrency(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(cat.fraction)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.small)
                    .background(Negative.copy(alpha = 0.7f)),
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

