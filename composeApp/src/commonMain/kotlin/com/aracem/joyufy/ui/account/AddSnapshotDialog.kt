package com.aracem.joyufy.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aracem.joyufy.domain.logic.InvestmentSnapshotFlows
import com.aracem.joyufy.domain.logic.hasManualInvestmentFlowAnnotations
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.joyufyColors
import com.aracem.joyufy.ui.components.MILLIS_IN_WEEK
import com.aracem.joyufy.ui.components.currentWeekStartMillis
import com.aracem.joyufy.ui.components.formatInputAmount
import com.aracem.joyufy.ui.components.formatIsoWeekLabel
import com.aracem.joyufy.ui.components.formatMonthDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSnapshotDialog(
    accountName: String,
    currentValue: Double?,
    onDismiss: () -> Unit,
    onConfirm: (
        totalValue: Double,
        weekDate: Long,
        deposits: Double,
        withdrawals: Double,
        fees: Double,
        dividends: Double,
        note: String?,
    ) -> Unit,
    flowDefaultsByWeek: Map<Long, InvestmentSnapshotFlows> = emptyMap(),
    // If non-null, dialog is in edit mode
    editingSnapshot: com.aracem.joyufy.domain.model.InvestmentSnapshot? = null,
) {
    val strings = LocalStrings.current
    var valueText by remember {
        mutableStateOf(editingSnapshot?.totalValue?.formatInputAmount()
            ?: currentValue?.formatInputAmount() ?: "")
    }
    var depositsText by remember { mutableStateOf(editingSnapshot?.deposits?.takeIf { it != 0.0 }?.formatInputAmount().orEmpty()) }
    var withdrawalsText by remember { mutableStateOf(editingSnapshot?.withdrawals?.takeIf { it != 0.0 }?.formatInputAmount().orEmpty()) }
    var feesText by remember { mutableStateOf(editingSnapshot?.fees?.takeIf { it != 0.0 }?.formatInputAmount().orEmpty()) }
    var dividendsText by remember { mutableStateOf(editingSnapshot?.dividends?.takeIf { it != 0.0 }?.formatInputAmount().orEmpty()) }
    var noteText by remember { mutableStateOf(editingSnapshot?.note.orEmpty()) }
    var valueError by remember { mutableStateOf<String?>(null) }
    var flowFieldsTouched by remember {
        mutableStateOf(editingSnapshot?.hasManualInvestmentFlowAnnotations() == true)
    }

    // Build last 12 weeks (including current) as options
    val weekCurrentStr = strings.weekCurrent
    val weekStr = strings.week
    val weeks = remember(weekCurrentStr, weekStr) { buildRecentWeeks(12, weekCurrentStr, weekStr) }
    // Pre-select the week matching the snapshot being edited, or current week
    var selectedWeek by remember {
        mutableStateOf(
            if (editingSnapshot != null)
                weeks.firstOrNull { it.mondayMs == editingSnapshot.weekDate } ?: weeks.first()
            else
                weeks.first()
        )
    }
    var weekExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedWeek.mondayMs, editingSnapshot?.id, flowDefaultsByWeek) {
        if (!flowFieldsTouched) {
            val defaults = flowDefaultsByWeek[selectedWeek.mondayMs] ?: InvestmentSnapshotFlows()
            depositsText = defaults.deposits.takeIf { it != 0.0 }?.formatInputAmount().orEmpty()
            withdrawalsText = defaults.withdrawals.takeIf { it != 0.0 }?.formatInputAmount().orEmpty()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (editingSnapshot != null) strings.editValue else strings.updateValue,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = strings.close,
                            tint = MaterialTheme.joyufyColors.contentSecondary)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Semana
                ExposedDropdownMenuBox(
                    expanded = weekExpanded,
                    onExpandedChange = { weekExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedWeek.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(strings.week) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(weekExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, focusedLabelColor = Accent),
                    )
                    ExposedDropdownMenu(
                        expanded = weekExpanded,
                        onDismissRequest = { weekExpanded = false },
                    ) {
                        weeks.forEach { week ->
                            DropdownMenuItem(
                                text = { Text(week.label) },
                                onClick = { selectedWeek = week; weekExpanded = false },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Valor
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it; valueError = null },
                    label = { Text(strings.totalValueEur) },
                    placeholder = { Text(strings.placeholderAmount) },
                    isError = valueError != null,
                    supportingText = valueError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        focusedLabelColor = Accent,
                    ),
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SnapshotAmountField(
                        value = depositsText,
                        onValueChange = { depositsText = it; flowFieldsTouched = true; valueError = null },
                        label = strings.depositsEur,
                        modifier = Modifier.weight(1f),
                    )
                    SnapshotAmountField(
                        value = withdrawalsText,
                        onValueChange = { withdrawalsText = it; flowFieldsTouched = true; valueError = null },
                        label = strings.withdrawalsEur,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SnapshotAmountField(
                        value = feesText,
                        onValueChange = { feesText = it; flowFieldsTouched = true; valueError = null },
                        label = strings.feesEur,
                        modifier = Modifier.weight(1f),
                    )
                    SnapshotAmountField(
                        value = dividendsText,
                        onValueChange = { dividendsText = it; flowFieldsTouched = true; valueError = null },
                        label = strings.dividendsEur,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(strings.noteOptional) },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        focusedLabelColor = Accent,
                    ),
                )

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel, color = MaterialTheme.joyufyColors.contentSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val value = valueText.replace(",", ".").toDoubleOrNull()
                            val deposits = depositsText.parseOptionalAmount()
                            val withdrawals = withdrawalsText.parseOptionalAmount()
                            val fees = feesText.parseOptionalAmount()
                            val dividends = dividendsText.parseOptionalAmount()
                            if (value == null || value < 0 ||
                                deposits == null || withdrawals == null || fees == null || dividends == null
                            ) {
                                valueError = strings.valueError
                                return@Button
                            }
                            onConfirm(
                                value,
                                selectedWeek.mondayMs,
                                deposits,
                                withdrawals,
                                fees,
                                dividends,
                                noteText.trim().ifBlank { null },
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Text(if (editingSnapshot != null) strings.saveChanges else strings.save)
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(strings.placeholderAmount) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            focusedLabelColor = Accent,
        ),
    )
}

private fun String.parseOptionalAmount(): Double? {
    if (isBlank()) return 0.0
    return replace(",", ".").toDoubleOrNull()?.takeIf { it >= 0.0 }
}

private data class WeekOption(val label: String, val mondayMs: Long)

private fun buildRecentWeeks(count: Int, weekCurrentLabel: String, weekLabel: String): List<WeekOption> {
    val currentMonday = currentWeekStartMillis()
    return (0 until count).map { weeksAgo ->
        val mondayMs = currentMonday - weeksAgo * MILLIS_IN_WEEK
        val iso = mondayMs.formatIsoWeekLabel()
        val label = if (weeksAgo == 0) {
            "$weekCurrentLabel$iso"
        } else {
            "$weekLabel $iso  (${mondayMs.formatMonthDay()})"
        }
        WeekOption(label = label, mondayMs = mondayMs)
    }
}
