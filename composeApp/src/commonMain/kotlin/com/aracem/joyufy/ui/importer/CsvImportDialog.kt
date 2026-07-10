package com.aracem.joyufy.ui.importer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.Strings
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.Positive
import com.aracem.joyufy.ui.theme.joyufyColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CsvImportDialog(
    state: CsvImportUiState,
    onDismiss: () -> Unit,
    onCommit: () -> Unit,
    onDefaultAccount: (Long?) -> Unit,
    onMapping: (CsvColumnRole, String?) -> Unit,
    onToggleRow: (Int) -> Unit,
    onRowAccount: (Int, Long?) -> Unit,
    onRowField: (Int, CsvImportRowField, String) -> Unit,
) {
    val strings = LocalStrings.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 1320.dp).heightIn(max = 860.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.csvImportTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = strings.csvImportSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                    Button(
                        onClick = onCommit,
                        enabled = state.validCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Text(strings.csvImportCommit.format(state.validCount))
                    }
                }

                CsvImportSummary(state)

                CsvMappingPanel(
                    state = state,
                    onDefaultAccount = onDefaultAccount,
                    onMapping = onMapping,
                )

                HorizontalDivider(color = MaterialTheme.joyufyColors.border)

                Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    LazyColumn(
                        modifier = Modifier.widthIn(min = 1140.dp).heightIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        stickyHeader {
                            CsvRowHeader()
                        }
                        items(state.rows, key = { it.id }) { row ->
                            CsvDraftRow(
                                row = row,
                                accounts = state.accounts,
                                onToggle = { onToggleRow(row.id) },
                                onAccount = { onRowAccount(row.id, it) },
                                onField = { field, value -> onRowField(row.id, field, value) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CsvImportSummary(state: CsvImportUiState) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryChip(strings.csvImportRows.format(state.enabledCount), Accent)
        SummaryChip(strings.csvImportValid.format(state.validCount), Positive)
        SummaryChip(strings.csvImportErrors.format(state.errorCount), if (state.errorCount > 0) Negative else MaterialTheme.joyufyColors.contentSecondary)
        SummaryChip(strings.csvImportDuplicates.format(state.duplicateCount), if (state.duplicateCount > 0) Accent else MaterialTheme.joyufyColors.contentSecondary)
    }
}

@Composable
private fun SummaryChip(label: String, color: Color) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        border = null,
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.11f),
            labelColor = color,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsvMappingPanel(
    state: CsvImportUiState,
    onDefaultAccount: (Long?) -> Unit,
    onMapping: (CsvColumnRole, String?) -> Unit,
) {
    val strings = LocalStrings.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccountSelector(
            label = strings.csvDefaultAccount,
            accounts = state.accounts,
            selectedAccountId = state.defaultAccountId,
            onSelected = onDefaultAccount,
            modifier = Modifier.width(220.dp),
        )
        CsvColumnRole.entries.forEach { role ->
            ColumnSelector(
                label = role.label(strings),
                headers = state.headers,
                selectedHeader = state.mapping[role],
                onSelected = { onMapping(role, it) },
                modifier = Modifier.width(role.mappingWidth()),
            )
        }
    }
}

@Composable
private fun CsvRowHeader() {
    val strings = LocalStrings.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(40.dp))
            Text(strings.accounts, Modifier.width(130.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary)
            Text(strings.dateFrom, Modifier.width(96.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary)
            Text(strings.amountEur, Modifier.width(110.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary)
            Text(strings.descriptionOptional, Modifier.width(360.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary)
            Text(strings.categoryOptional, Modifier.width(230.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary)
            Text(strings.transactionType, Modifier.width(120.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary)
        }
    }
}

@Composable
private fun CsvDraftRow(
    row: CsvImportDraftRow,
    accounts: List<Account>,
    onToggle: () -> Unit,
    onAccount: (Long?) -> Unit,
    onField: (CsvImportRowField, String) -> Unit,
) {
    val strings = LocalStrings.current
    val rowColor = when {
        !row.enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        row.errors.isNotEmpty() -> Negative.copy(alpha = 0.08f)
        row.duplicateHint -> Accent.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor, MaterialTheme.shapes.small)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = row.enabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(32.dp),
            )
            AccountSelector(
                label = strings.accounts,
                accounts = accounts,
                selectedAccountId = row.accountId,
                onSelected = onAccount,
                modifier = Modifier.width(130.dp),
            )
            CompactTextField(row.dateText, { onField(CsvImportRowField.DATE, it) }, Modifier.width(96.dp))
            CompactTextField(row.amountText, { onField(CsvImportRowField.AMOUNT, it) }, Modifier.width(110.dp))
            CompactTextField(row.description, { onField(CsvImportRowField.DESCRIPTION, it) }, Modifier.width(360.dp))
            CompactTextField(row.category, { onField(CsvImportRowField.CATEGORY, it) }, Modifier.width(230.dp))
            CompactTextField(row.typeText, { onField(CsvImportRowField.TYPE, it) }, Modifier.width(120.dp))
        }
        if (row.enabled && (row.errors.isNotEmpty() || row.duplicateHint)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                row.errors.forEach { error ->
                    FilterChip(
                        selected = false,
                        onClick = {},
                        label = { Text(strings.csvImportInvalidField.format(error)) },
                    )
                }
                if (row.duplicateHint) {
                    Text(
                        text = strings.csvImportDuplicateHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTextField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.height(48.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ColumnSelector(
    label: String,
    headers: List<String>,
    selectedHeader: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedHeader ?: label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("—") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            headers.forEach { header ->
                DropdownMenuItem(
                    text = { Text(header) },
                    onClick = {
                        onSelected(header)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountSelector(
    label: String,
    accounts: List<Account>,
    selectedAccountId: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedAccountId }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.name ?: label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(label) },
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

private fun CsvColumnRole.label(strings: Strings): String = when (this) {
    CsvColumnRole.DATE -> strings.dateFormat
    CsvColumnRole.AMOUNT -> strings.amountEur
    CsvColumnRole.DESCRIPTION -> strings.descriptionOptional
    CsvColumnRole.CATEGORY -> strings.categoryOptional
    CsvColumnRole.TYPE -> strings.transactionType
    CsvColumnRole.ACCOUNT -> strings.accounts
}

private fun CsvColumnRole.mappingWidth() = when (this) {
    CsvColumnRole.DESCRIPTION -> 300.dp
    CsvColumnRole.CATEGORY -> 240.dp
    CsvColumnRole.DATE -> 180.dp
    CsvColumnRole.AMOUNT -> 160.dp
    CsvColumnRole.TYPE -> 150.dp
    CsvColumnRole.ACCOUNT -> 180.dp
}
