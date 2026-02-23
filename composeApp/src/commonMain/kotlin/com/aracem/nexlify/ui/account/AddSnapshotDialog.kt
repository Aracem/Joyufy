package com.aracem.nexlify.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aracem.nexlify.ui.theme.Accent
import com.aracem.nexlify.ui.theme.nexlifyColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSnapshotDialog(
    accountName: String,
    currentValue: Double?,
    onDismiss: () -> Unit,
    onConfirm: (totalValue: Double, weekDate: Long) -> Unit,
    // If non-null, dialog is in edit mode
    editingSnapshot: com.aracem.nexlify.domain.model.InvestmentSnapshot? = null,
) {
    var valueText by remember {
        mutableStateOf(editingSnapshot?.totalValue?.let { "%.2f".format(it) }
            ?: currentValue?.let { "%.2f".format(it) } ?: "")
    }
    var valueError by remember { mutableStateOf<String?>(null) }

    // Build last 12 weeks (including current) as options
    val weeks = remember { buildRecentWeeks(12) }
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(380.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (editingSnapshot != null) "Editar valor" else "Actualizar valor",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.nexlifyColors.contentSecondary,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar",
                            tint = MaterialTheme.nexlifyColors.contentSecondary)
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
                        label = { Text("Semana") },
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
                    label = { Text("Valor total (€)") },
                    placeholder = { Text("0,00") },
                    isError = valueError != null,
                    supportingText = valueError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        focusedLabelColor = Accent,
                    ),
                )

                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.nexlifyColors.contentSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val value = valueText.replace(",", ".").toDoubleOrNull()
                            if (value == null || value < 0) {
                                valueError = "Introduce un valor válido"
                                return@Button
                            }
                            onConfirm(value, selectedWeek.mondayMs)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Text(if (editingSnapshot != null) "Guardar cambios" else "Guardar")
                    }
                }
            }
        }
    }
}

private data class WeekOption(val label: String, val mondayMs: Long)

private fun buildRecentWeeks(count: Int): List<WeekOption> {
    val millisInWeek = 7 * 86_400_000L
    val now = Clock.System.now()
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val dayOfWeek = local.dayOfWeek.ordinal  // Mon=0
    val millisInDay = 86_400_000L
    val currentMonday = (now.toEpochMilliseconds() / millisInDay - dayOfWeek) * millisInDay

    return (0 until count).map { weeksAgo ->
        val mondayMs = currentMonday - weeksAgo * millisInWeek
        val mondayLocal = Instant.fromEpochMilliseconds(mondayMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val iso = isoWeekLabel(mondayMs)
        val label = if (weeksAgo == 0) {
            "Semana actual — $iso"
        } else {
            "Semana $iso  (${"%02d/%02d".format(mondayLocal.dayOfMonth, mondayLocal.monthNumber)})"
        }
        WeekOption(label = label, mondayMs = mondayMs)
    }
}

private fun isoWeekLabel(mondayMs: Long): String {
    val local = Instant.fromEpochMilliseconds(mondayMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    // ISO week: shift Thursday of the week to get the year
    val thursdayMs = mondayMs + 3 * 86_400_000L
    val thursdayLocal = Instant.fromEpochMilliseconds(thursdayMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    // Week number: day of year of thursday / 7 + 1
    val jan1Ms = kotlinx.datetime.LocalDate(thursdayLocal.year, 1, 1)
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
    val dayOfYear = ((thursdayMs - jan1Ms) / 86_400_000L).toInt()
    val weekNumber = dayOfYear / 7 + 1
    return "S%02d-%d".format(weekNumber, thursdayLocal.year)
}
