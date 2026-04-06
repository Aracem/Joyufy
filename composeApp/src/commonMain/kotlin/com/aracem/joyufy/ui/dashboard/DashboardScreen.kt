package com.aracem.joyufy.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.ui.openUrl
import com.aracem.joyufy.ui.components.*
import com.aracem.joyufy.ui.strings.LocalStrings
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
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsState()

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
                        text = strings.totalWealth,
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

        // Empty state — solo cuando no hay cuentas
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
                            text = strings.noAccountsYet,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = strings.addFirstAccount,
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
    val strings = LocalStrings.current
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
                text = strings.evolution,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleMode, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (mode == ChartMode.AREA) Icons.AutoMirrored.Filled.List else Icons.Default.DateRange,
                    contentDescription = strings.changeView,
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
    val strings = LocalStrings.current
    val items = listOf(
        ChartRange.ONE_WEEK to strings.chartWeek,
        ChartRange.ONE_MONTH to strings.chartMonth,
        ChartRange.THREE_MONTHS to strings.chartThreeMonths,
        ChartRange.SIX_MONTHS to strings.chartSixMonths,
        ChartRange.YTD to strings.chartYtd,
        ChartRange.ONE_YEAR to strings.chartYear,
        ChartRange.ALL to strings.yearAll,
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
private fun AnalysisCard(
    monthlySummary: MonthlySummary?,
    annualSummary: AnnualSummary?,
    expanded: Boolean,
    selectedYear: Int,
    onToggleExpanded: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
) {
    val strings = LocalStrings.current
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
                text = strings.analysis,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // Botón "Mes actual" — solo visible cuando hay un mes seleccionado
            val showBackButton = selectedMonth != null
            val backButtonScale by animateFloatAsState(
                targetValue = if (showBackButton) 1f else 0f,
                animationSpec = tween(200),
            )
            if (showBackButton || backButtonScale > 0f) {
                OutlinedButton(
                    onClick = { selectedMonth = null },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Accent),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .graphicsLayer {
                            scaleX = backButtonScale
                            scaleY = backButtonScale
                            alpha = backButtonScale
                        },
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = strings.monthCurrent,
                        tint = Accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.monthCurrent,
                        style = MaterialTheme.typography.labelMedium,
                        color = Accent,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) strings.collapse else strings.expand,
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

                // ── Resumen mensual — muestra el mes seleccionado o el actual ──
                val displayMonth: MonthBreakdown? = selectedMonth
                    ?: monthlySummary?.let { ms ->
                        // Construir un MonthBreakdown del mes actual a partir de MonthlySummary
                        val nowMonth = Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber
                        MonthBreakdown(
                            monthNumber = nowMonth,
                            income = ms.income,
                            expenses = ms.expenses,
                            investmentDelta = ms.investmentDelta,
                            net = ms.net + ms.investmentDelta,
                            topCategories = ms.topCategories,
                        )
                    }

                if (displayMonth != null) {
                    MonthlySection(
                        month = displayMonth,
                        isCurrentMonth = selectedMonth == null,
                        onClearSelection = { selectedMonth = null },
                    )
                }

                // ── Divider entre secciones ───────────────────────────────
                if (displayMonth != null && annualSummary != null) {
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

private val monthFullNames = listOf(
    "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
)

@Composable
private fun MonthlySection(
    month: MonthBreakdown,
    isCurrentMonth: Boolean,
    onClearSelection: () -> Unit,
) {
    val strings = LocalStrings.current
    val monthName = monthFullNames[month.monthNumber]

    // Header: título sección + mes actual bien visible
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.currentMonth,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(Accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = monthName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    // Stats row — cifras animadas individualmente
    Row(modifier = Modifier.fillMaxWidth()) {
        MonthlyStat(strings.income, month.income, Positive, Modifier.weight(1f))
        MonthlyStat(strings.expenses, month.expenses, Negative, Modifier.weight(1f))
        if (month.investmentDelta != 0.0) {
            val d = month.investmentDelta
            MonthlyStat(
                label = strings.investment,
                amount = kotlin.math.abs(d),
                color = if (d >= 0) Positive else Negative,
                modifier = Modifier.weight(1f),
                prefix = if (d >= 0) "+" else "-",
            )
        }
        MonthlyStat(
            label = strings.net,
            amount = month.net,
            color = if (month.net >= 0) Positive else Negative,
            modifier = Modifier.weight(1f),
        )
    }

    // Top categories — cada barra anima su fracción, aparece/desaparece con AnimatedVisibility
    if (month.topCategories.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = strings.topExpenses,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Máximo 4 slots fijos — así Compose reutiliza el composable por posición
            // y solo anima la fracción/importe. Si el slot no tiene categoría, se colapsa.
            (0 until 4).forEach { index ->
                key(index) {
                    val cat = month.topCategories.getOrNull(index)
                    AnimatedVisibility(
                        visible = cat != null,
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
                    ) {
                        if (cat != null) AnimatedCategoryBar(cat)
                    }
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
    val strings = LocalStrings.current
    val monthNames = listOf("E","F","M","A","M","J","J","A","S","O","N","D")
    val maxAbs = summary.months.maxOf { kotlin.math.abs(it.net) }.coerceAtLeast(1.0)
    // Current calendar month (1-based), used to dim future months in current year
    val nowMonth = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).monthNumber
    }

    // Year navigation header
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.currentYear,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Pill de año — acento si es el actual, gris+navegable si es otro
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selectedYear == currentYear) Accent.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPreviousYear, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.yearPrevious,
                            tint = if (selectedYear == currentYear) Accent
                                   else MaterialTheme.joyufyColors.contentSecondary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = "${summary.year}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedYear == currentYear) Accent
                                else MaterialTheme.joyufyColors.contentSecondary,
                    )
                    IconButton(
                        onClick = onNextYear,
                        enabled = selectedYear < currentYear,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = strings.yearNext,
                            tint = if (selectedYear < currentYear)
                                (if (selectedYear == currentYear - 1) Accent else MaterialTheme.joyufyColors.contentSecondary)
                            else MaterialTheme.joyufyColors.contentDisabled,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    // Totals row
    Row(modifier = Modifier.fillMaxWidth()) {
        MonthlyStat(strings.income, summary.totalIncome, Positive, Modifier.weight(1f))
        MonthlyStat(strings.expenses, summary.totalExpenses, Negative, Modifier.weight(1f))
        if (summary.totalInvestmentDelta != 0.0) {
            val d = summary.totalInvestmentDelta
            MonthlyStat(
                label = strings.investment,
                amount = kotlin.math.abs(d),
                color = if (d >= 0) Positive else Negative,
                modifier = Modifier.weight(1f),
                prefix = if (d >= 0) "+" else "-",
            )
        }
        MonthlyStat(
            label = strings.net,
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

}

@Composable
private fun MonthlyStat(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String? = null,
) {
    val animatedAmount by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
    )
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
        Spacer(Modifier.height(2.dp))
        val sign = prefix ?: if (amount >= 0) "+" else ""
        Text(
            text = "$sign${animatedAmount.toDouble().formatCurrency()}",
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
    AnimatedCategoryBar(cat)
}

@Composable
private fun AnimatedCategoryBar(cat: CategoryBreakdown) {
    val animatedFraction by animateFloatAsState(
        targetValue = cat.fraction,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
    )
    val animatedAmount by animateFloatAsState(
        targetValue = cat.amount.toFloat(),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
    )
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
                text = animatedAmount.toDouble().formatCurrency(),
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
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.small)
                    .background(Accent.copy(alpha = 0.7f)),
            )
        }
    }
}


@Composable
private fun DashboardMenu(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = strings.moreOptions,
                tint = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(strings.exportData) },
                onClick = { expanded = false; onExport() },
            )
            DropdownMenuItem(
                text = { Text(strings.importData) },
                onClick = { expanded = false; onImport() },
            )
        }
    }
}
