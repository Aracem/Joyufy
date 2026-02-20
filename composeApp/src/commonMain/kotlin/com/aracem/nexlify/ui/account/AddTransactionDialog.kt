package com.aracem.nexlify.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aracem.nexlify.domain.model.Account
import com.aracem.nexlify.domain.model.TransactionCategory
import com.aracem.nexlify.domain.model.TransactionType
import com.aracem.nexlify.ui.theme.Accent
import com.aracem.nexlify.ui.theme.nexlifyColors
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    availableAccounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (type: TransactionType, amount: Double, category: String?, description: String?, relatedAccountId: Long?, date: Long) -> Unit,
) {
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedRelatedAccount by remember { mutableStateOf<Account?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var relatedExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(440.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Nueva transacción",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar",
                            tint = MaterialTheme.nexlifyColors.contentSecondary)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Tipo
                Text("Tipo", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.nexlifyColors.contentSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { type ->
                        val selected = selectedType == type
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedType = type
                                if (type != TransactionType.TRANSFER && type != TransactionType.INVESTMENT_DEPOSIT) {
                                    selectedRelatedAccount = null
                                }
                            },
                            label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.15f),
                                selectedLabelColor = Accent,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                selectedBorderColor = Accent,
                                borderColor = MaterialTheme.nexlifyColors.border,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Importe
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = { Text("Importe (€)") },
                    placeholder = { Text("0,00") },
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, focusedLabelColor = Accent),
                )

                Spacer(Modifier.height(12.dp))

                // Categoría (dropdown con sugerencias)
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Categoría (opcional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, focusedLabelColor = Accent),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        TransactionCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label) },
                                onClick = { category = cat.label; categoryExpanded = false },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Descripción
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, focusedLabelColor = Accent),
                )

                // Cuenta destino (TRANSFER e INVESTMENT_DEPOSIT)
                if ((selectedType == TransactionType.TRANSFER || selectedType == TransactionType.INVESTMENT_DEPOSIT) && availableAccounts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = relatedExpanded,
                        onExpandedChange = { relatedExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedRelatedAccount?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Cuenta destino") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(relatedExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, focusedLabelColor = Accent),
                        )
                        ExposedDropdownMenu(
                            expanded = relatedExpanded,
                            onDismissRequest = { relatedExpanded = false },
                        ) {
                            availableAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = { selectedRelatedAccount = account; relatedExpanded = false },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.nexlifyColors.contentSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.replace(",", ".").toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                amountError = "Introduce un importe válido"
                                return@Button
                            }
                            onConfirm(
                                selectedType,
                                amount,
                                category.ifBlank { null },
                                description.ifBlank { null },
                                selectedRelatedAccount?.id,
                                Clock.System.now().toEpochMilliseconds(),
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Text(if (selectedType == TransactionType.TRANSFER) "Transferir" else "Añadir")
                    }
                }
            }
        }
    }
}

private val TransactionType.label: String
    get() = when (this) {
        TransactionType.INCOME -> "Ingreso"
        TransactionType.EXPENSE -> "Gasto"
        TransactionType.TRANSFER -> "Transferencia"
        TransactionType.INVESTMENT_DEPOSIT -> "Depósito"
    }
