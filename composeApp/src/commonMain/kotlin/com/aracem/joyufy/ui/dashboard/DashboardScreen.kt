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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

        // Análisis (mensual + anual, colapsable)
        if (state.monthlySummary != null || state.annualSummary != null) {
            item {
                AnalysisCard(
                    monthlySummary = state.monthlySummary,
                    annualSummary = state.annualSummary,
                    expanded = state.analysisExpanded,
                    selectedYear = state.selectedAnalysisYear,
                    onToggleExpanded = { viewModel.setAnalysisExpanded(!state.analysisExpanded) },
                    onPreviousYear = { viewModel.navigateAnalysisYear(-1) },
                    onNextYear = { viewModel.navigateAnalysisYear(+1) },
                )
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
private fun AnalysisCard(
    monthlySummary: MonthlySummary?,
    annualSummary: AnnualSummary?,
    expanded: Boolean,
    selectedYear: Int,
    onToggleExpanded: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
) {
    val currentYear = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
    )
    // Which month bar is selected (for drill-down detail), null = none
    var selectedMonth by remember(selectedYear) { mutableStateOf<MonthBreakdown?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium),
    ) {
        // ── Header (always visible) ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Análisis",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Colapsar" else "Expandir",
                tint = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
            )
        }

        // ── Collapsible content ───────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {

                // ── Resumen mensual ───────────────────────────────────────
                if (monthlySummary != null) {
                    MonthlySection(monthlySummary)
                }

                // ── Divider entre secciones ───────────────────────────────
                if (monthlySummary != null && annualSummary != null) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.joyufyColors.border)
                    Spacer(Modifier.height(16.dp))
                }

                // ── Resumen anual ─────────────────────────────────────────
                if (annualSummary != null) {
                    AnnualSection(
                        summary = annualSummary,
                        selectedYear = selectedYear,
                        currentYear = currentYear,
                        selectedMonth = selectedMonth,
                        onPreviousYear = onPreviousYear,
                        onNextYear = onNextYear,
                        onMonthClick = { month ->
                            selectedMonth = if (selectedMonth?.monthNumber == month.monthNumber) null else month
                        },
                    )
                }
            }
        }
    }
}

// ── Sección mensual ────────────────────────────────────────────────────────────

@Composable
private fun MonthlySection(summary: MonthlySummary) {
    val monthName = remember {
        val m = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber
        listOf("","Enero","Febrero","Marzo","Abril","Mayo","Junio",
            "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")[m]
    }

    // Header row: "Este mes" label + month name
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Este mes",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = monthName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
    }
    Spacer(Modifier.height(12.dp))

    // Stats row
    Row(modifier = Modifier.fillMaxWidth()) {
        MonthlyStat("Ingresos", summary.income, Positive, Modifier.weight(1f))
        MonthlyStat("Gastos", summary.expenses, Negative, Modifier.weight(1f))
        MonthlyStat(
            label = "Neto",
            amount = summary.net,
            color = if (summary.net >= 0) Positive else Negative,
            modifier = Modifier.weight(1f),
        )
    }

    // Top categories as compact chips
    if (summary.topCategories.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Top gastos",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            summary.topCategories.take(4).forEach { cat ->
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${cat.label}  ${cat.amount.formatCurrency()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }
            }
        }
    }
}

// ── Sección anual ──────────────────────────────────────────────────────────────

@Composable
private fun AnnualSection(
    summary: AnnualSummary,
    selectedYear: Int,
    currentYear: Int,
    selectedMonth: MonthBreakdown?,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onMonthClick: (MonthBreakdown) -> Unit,
) {
    val monthNames = listOf("E","F","M","A","M","J","J","A","S","O","N","D")
    val maxAbs = summary.months.maxOf { kotlin.math.abs(it.net) }.coerceAtLeast(1.0)
    // Current calendar month (1-based), used to dim future months in current year
    val nowMonth = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber
    }

    // Year navigation header
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPreviousYear, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Año anterior",
                tint = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = "${summary.year}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(
            onClick = onNextYear,
            enabled = selectedYear < currentYear,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Año siguiente",
                tint = if (selectedYear < currentYear)
                    MaterialTheme.joyufyColors.contentSecondary
                else
                    MaterialTheme.joyufyColors.contentDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    // Totals row
    Row(modifier = Modifier.fillMaxWidth()) {
        MonthlyStat("Ingresos", summary.totalIncome, Positive, Modifier.weight(1f))
        MonthlyStat("Gastos", summary.totalExpenses, Negative, Modifier.weight(1f))
        if (summary.totalInvestmentDelta != 0.0) {
            val d = summary.totalInvestmentDelta
            MonthlyStat(
                label = "Inversión",
                amount = kotlin.math.abs(d),
                color = if (d >= 0) Positive else Negative,
                modifier = Modifier.weight(1f),
                prefix = if (d >= 0) "+" else "-",
            )
        }
        MonthlyStat(
            label = "Neto",
            amount = summary.totalNet,
            color = if (summary.totalNet >= 0) Positive else Negative,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(20.dp))

    // ── Bar chart: one bar per month, green=positive net, red=negative ────
    // Chart has a center baseline. Positive bars grow up, negative bars grow down.
    val barAreaHeight = 72.dp
    val labelHeight = 18.dp
    val totalHeight = barAreaHeight + labelHeight

    Row(
        modifier = Modifier.fillMaxWidth().height(totalHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        summary.months.forEachIndexed { index, month ->
            val isFuture = selectedYear == currentYear && (index + 1) > nowMonth
            val isSelected = selectedMonth?.monthNumber == month.monthNumber
            val hasData = month.net != 0.0 || month.income != 0.0 || month.expenses != 0.0
            val barFraction = if (hasData) (kotlin.math.abs(month.net) / maxAbs).toFloat().coerceIn(0.01f, 1f) else 0f
            val isPositive = month.net >= 0
            val barColor = when {
                isFuture -> MaterialTheme.colorScheme.surfaceVariant
                isPositive -> Positive.copy(alpha = if (isSelected) 1f else 0.65f)
                else -> Negative.copy(alpha = if (isSelected) 1f else 0.65f)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .then(if (hasData && !isFuture) Modifier.clickable { onMonthClick(month) } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top half: positive bars grow down from top, negative bars leave empty
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    if (isPositive && barFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .fillMaxHeight(barFraction)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(barColor),
                        )
                    }
                }
                // Center line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.joyufyColors.border),
                )
                // Bottom half: negative bars grow down from center
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    if (!isPositive && barFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .fillMaxHeight(barFraction)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(barColor),
                        )
                    }
                }
                // Month label
                Box(modifier = Modifier.height(labelHeight), contentAlignment = Alignment.Center) {
                    Text(
                        text = monthNames[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.joyufyColors.contentSecondary.copy(alpha = if (isFuture) 0.3f else 0.7f),
                    )
                }
            }
        }
    }

    // ── Detalle del mes seleccionado ──────────────────────────────────────
    AnimatedVisibility(
        visible = selectedMonth != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        selectedMonth?.let { month ->
            val mName = listOf("","Enero","Febrero","Marzo","Abril","Mayo","Junio",
                "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")[month.monthNumber]
            Column {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.joyufyColors.border)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    val netColor = if (month.net >= 0) Positive else Negative
                    val netSign = if (month.net >= 0) "+" else ""
                    Text(
                        text = "$netSign${month.net.formatCurrency()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = netColor,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MonthlyStat("Ingresos", month.income, Positive, Modifier.weight(1f))
                    MonthlyStat("Gastos", month.expenses, Negative, Modifier.weight(1f))
                    if (month.investmentDelta != 0.0) {
                        val d = month.investmentDelta
                        MonthlyStat(
                            label = "Inversión",
                            amount = kotlin.math.abs(d),
                            color = if (d >= 0) Positive else Negative,
                            modifier = Modifier.weight(1f),
                            prefix = if (d >= 0) "+" else "-",
                        )
                    }
                }
                if (month.topCategories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Top gastos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        month.topCategories.forEach { CategoryBar(it) }
                    }
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
    prefix: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
        Spacer(Modifier.height(2.dp))
        val sign = prefix ?: when (label) {
            "Ingresos" -> "+"
            "Gastos" -> "-"
            else -> if (amount >= 0) "+" else ""
        }
        Text(
            text = "$sign${amount.formatCurrency()}",
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
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
