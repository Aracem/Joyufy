package com.aracem.joyufy.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.formatCurrency
import com.aracem.joyufy.ui.components.formatDate
import com.aracem.joyufy.ui.navigation.LedgerInitialFilter
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.Strings
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.Positive
import com.aracem.joyufy.ui.theme.joyufyColors
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionLedgerScreen(
    initialFilter: LedgerInitialFilter,
    viewModel: TransactionLedgerViewModel = koinInject(),
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsState()
    val event by viewModel.event.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialFilter) {
        viewModel.applyInitialFilter(initialFilter)
    }

    LaunchedEffect(event) {
        when (val ev = event) {
            is LedgerEvent.Deleted -> {
                val result = snackbarHostState.showSnackbar(
                    message = strings.ledgerDeletedTransactions.format(ev.count),
                    actionLabel = strings.undo,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    viewModel.undoLastDelete()
                }
                viewModel.resetEvent()
            }
            is LedgerEvent.Restored -> {
                snackbarHostState.showSnackbar(strings.ledgerRestoredTransactions.format(ev.count))
                viewModel.resetEvent()
            }
            is LedgerEvent.Error -> {
                snackbarHostState.showSnackbar(ev.message.ifBlank { "Error" })
                viewModel.resetEvent()
            }
            LedgerEvent.Idle -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Accent)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LedgerHeader(state = state)
            }

            item {
                ReviewInboxCard(
                    state = state,
                    onPreset = viewModel::setPreset,
                )
            }

            item {
                LedgerFilterBar(
                    state = state,
                    onPreset = viewModel::setPreset,
                    onSearch = viewModel::setSearchQuery,
                    onAccount = viewModel::setSelectedAccountId,
                    onType = viewModel::setSelectedType,
                )
            }

            item {
                AnimatedVisibility(
                    visible = state.selectedCount > 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    BulkToolbar(
                        state = state,
                        onApplyCategory = viewModel::bulkSetCategory,
                        onDelete = viewModel::bulkDeleteSelected,
                        onClearSelection = viewModel::clearSelection,
                        onSelectAll = viewModel::selectAllVisible,
                    )
                }
            }

            item {
                DataQualityPanel(
                    metrics = state.qualityMetrics,
                    onMetricClick = { metric ->
                        when (metric.type) {
                            LedgerQualityType.MISSING_CATEGORIES -> viewModel.setPreset(LedgerPreset.UNCATEGORIZED)
                            LedgerQualityType.POSSIBLE_DUPLICATES -> viewModel.setPreset(LedgerPreset.DUPLICATES)
                            else -> viewModel.setPreset(LedgerPreset.DATA_QUALITY)
                        }
                    },
                )
            }

            if (state.rows.isEmpty()) {
                item {
                    EmptyLedgerState()
                }
            } else {
                stickyHeader {
                    LedgerTableHeader()
                }
                items(state.rows, key = { it.transaction.id }) { row ->
                    LedgerRow(
                        row = row,
                        onToggleSelected = { viewModel.toggleSelected(row.transaction.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerHeader(state: LedgerUiState) {
    val strings = LocalStrings.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = strings.transactionLedger,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.ledgerSubtitle.format(state.totalTransactionCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewInboxCard(
    state: LedgerUiState,
    onPreset: (LedgerPreset) -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = strings.ledgerReviewInbox,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReviewMetricPill(
                    label = strings.uncategorized,
                    value = state.uncategorizedCount,
                    onClick = { onPreset(LedgerPreset.UNCATEGORIZED) },
                )
                ReviewMetricPill(
                    label = strings.ledgerPossibleDuplicates,
                    value = state.duplicateCount,
                    onClick = { onPreset(LedgerPreset.DUPLICATES) },
                )
                ReviewMetricPill(
                    label = strings.ledgerStaleSnapshots,
                    value = state.staleSnapshotCount,
                    onClick = { onPreset(LedgerPreset.DATA_QUALITY) },
                )
                ReviewMetricPill(
                    label = strings.ledgerImportedDrafts,
                    value = state.importedDraftCount,
                    onClick = { onPreset(LedgerPreset.DATA_QUALITY) },
                )
            }
        }
    }
}

@Composable
private fun ReviewMetricPill(
    label: String,
    value: Int,
    onClick: () -> Unit,
) {
    val color = if (value > 0) Accent else MaterialTheme.joyufyColors.contentSecondary
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = if (value > 0) 0.12f else 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerFilterBar(
    state: LedgerUiState,
    onPreset: (LedgerPreset) -> Unit,
    onSearch: (String) -> Unit,
    onAccount: (Long?) -> Unit,
    onType: (TransactionType?) -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.selectedPreset == preset,
                        onClick = { onPreset(preset) },
                        label = { Text(preset.label(strings)) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.joyufyColors.contentSecondary,
                        )
                    },
                    placeholder = { Text(strings.ledgerSearchPlaceholder) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
                AccountDropdown(
                    accounts = state.accounts,
                    selectedAccountId = state.selectedAccountId,
                    onSelected = onAccount,
                )
                TypeDropdown(
                    selectedType = state.selectedType,
                    onSelected = onType,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onSelected: (Long?) -> Unit,
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val label = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: strings.ledgerAllAccounts
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(strings.ledgerAllAccounts) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelected(account.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TypeDropdown(
    selectedType: TransactionType?,
    onSelected: (TransactionType?) -> Unit,
) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val label = selectedType?.label(strings) ?: strings.ledgerAllTypes
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(strings.ledgerAllTypes) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            TransactionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label(strings)) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkToolbar(
    state: LedgerUiState,
    onApplyCategory: (String?) -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val strings = LocalStrings.current
    var category by remember(state.selectedCount) { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Accent.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.ledgerSelectedCount.format(state.selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll) { Text(strings.selectAll) }
                TextButton(onClick = onClearSelection) { Text(strings.clearSelection) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(strings.ledgerBulkCategory) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
                Button(
                    onClick = { onApplyCategory(category) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text(strings.ledgerApplyCategory)
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.ledgerDeleteSelected)
                }
            }
            if (state.customCategories.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.customCategories.take(8).forEach { suggestion ->
                        AssistChip(
                            onClick = { category = suggestion },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataQualityPanel(
    metrics: List<LedgerQualityMetric>,
    onMetricClick: (LedgerQualityMetric) -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = strings.ledgerDataQualityTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                metrics.forEach { metric ->
                    val active = metric.count > 0
                    val color = if (active) Accent else MaterialTheme.joyufyColors.contentSecondary
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(color.copy(alpha = if (active) 0.11f else 0.06f))
                            .clickable { if (active) onMetricClick(metric) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = metric.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = metric.type.label(strings),
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerTableHeader() {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(44.dp))
            Text(
                text = strings.dateFrom,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.width(84.dp),
            )
            Text(
                text = strings.accounts,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.width(150.dp),
            )
            Text(
                text = strings.transactions,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings.amountEur,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.width(120.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerRow(
    row: LedgerTransactionRow,
    onToggleSelected: () -> Unit,
) {
    val strings = LocalStrings.current
    val tx = row.transaction
    val signedAmount = when (tx.type) {
        TransactionType.EXPENSE -> -tx.amount
        else -> tx.amount
    }
    val amountColor = when {
        signedAmount < 0 -> Negative
        signedAmount > 0 -> Positive
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onToggleSelected),
        color = if (row.selected) Accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = row.selected,
                    onCheckedChange = { onToggleSelected() },
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = tx.date.formatDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                    modifier = Modifier.width(84.dp),
                )
                AccountCell(row = row, modifier = Modifier.width(150.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.primaryText(row, strings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tx.secondaryText(row, strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = signedAmount.formatCurrency(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = amountColor,
                    modifier = Modifier.width(120.dp),
                )
            }
            if (row.warnings.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(start = 54.dp, end = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.warnings.forEach { warning ->
                        WarningChip(label = warning.label(strings, row.duplicateGroupSize))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCell(
    row: LedgerTransactionRow,
    modifier: Modifier = Modifier,
) {
    val account = row.account
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(account?.color ?: MaterialTheme.joyufyColors.border),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = account?.name.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WarningChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Accent,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(Accent.copy(alpha = 0.11f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyLedgerState() {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.55f),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.ledgerNoTransactions,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.noSearchResultsHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.7f),
            )
        }
    }
}

private fun LedgerPreset.label(strings: Strings): String = when (this) {
    LedgerPreset.ALL -> strings.ledgerAll
    LedgerPreset.UNCATEGORIZED -> strings.uncategorized
    LedgerPreset.DUPLICATES -> strings.ledgerPossibleDuplicates
    LedgerPreset.TRANSFERS -> strings.transactionTransfer
    LedgerPreset.DATA_QUALITY -> strings.ledgerDataQuality
}

private fun TransactionType.label(strings: Strings): String = when (this) {
    TransactionType.INCOME -> strings.transactionIncome
    TransactionType.EXPENSE -> strings.transactionExpense
    TransactionType.TRANSFER -> strings.transactionTransfer
}

private fun LedgerQualityType.label(strings: Strings): String = when (this) {
    LedgerQualityType.MISSING_CATEGORIES -> strings.ledgerMissingCategories
    LedgerQualityType.EMPTY_DESCRIPTIONS -> strings.ledgerEmptyDescriptions
    LedgerQualityType.POSSIBLE_DUPLICATES -> strings.ledgerPossibleDuplicates
    LedgerQualityType.BROKEN_TRANSFERS -> strings.ledgerBrokenTransfers
    LedgerQualityType.UNUSUAL_AMOUNTS -> strings.ledgerUnusualAmounts
    LedgerQualityType.STALE_ACCOUNTS -> strings.ledgerStaleAccounts
    LedgerQualityType.STALE_SNAPSHOTS -> strings.ledgerStaleSnapshots
}

private fun LedgerWarning.label(strings: Strings, duplicateGroupSize: Int): String = when (this) {
    LedgerWarning.MISSING_CATEGORY -> strings.ledgerMissingCategory
    LedgerWarning.EMPTY_DESCRIPTION -> strings.ledgerEmptyDescription
    LedgerWarning.POSSIBLE_DUPLICATE -> strings.ledgerDuplicateHint.format(duplicateGroupSize)
    LedgerWarning.BROKEN_TRANSFER -> strings.ledgerBrokenTransfer
    LedgerWarning.UNUSUAL_AMOUNT -> strings.ledgerUnusualAmount
}

private fun Transaction.primaryText(row: LedgerTransactionRow, strings: Strings): String =
    description?.takeIf { it.isNotBlank() }
        ?: if (isTransfer()) {
            "${row.account?.name.orEmpty()} -> ${row.relatedAccount?.name.orEmpty()}"
        } else {
            category?.takeIf { it.isNotBlank() } ?: strings.ledgerNoDescription
        }

private fun Transaction.secondaryText(row: LedgerTransactionRow, strings: Strings): String {
    if (isTransfer()) {
        return strings.transactionTransfer
    }
    val categoryText = category?.takeIf { it.isNotBlank() } ?: strings.uncategorized
    return "${type.label(strings)} · $categoryText"
}

private fun Transaction.isTransfer(): Boolean =
    relatedAccountId != null || type == TransactionType.TRANSFER
