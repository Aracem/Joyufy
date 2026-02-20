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

import com.aracem.nexlify.ui.theme.Accent
import com.aracem.nexlify.ui.theme.nexlifyColors

@Composable
fun AddSnapshotDialog(
    accountName: String,
    currentValue: Double?,
    onDismiss: () -> Unit,
    onConfirm: (totalValue: Double) -> Unit,
) {
    var valueText by remember { mutableStateOf(currentValue?.let { "%.2f".format(it) } ?: "") }
    var valueError by remember { mutableStateOf<String?>(null) }

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
                            text = "Actualizar valor",
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

                Text(
                    text = "Introduce el valor total actual de esta cuenta de inversión esta semana.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.nexlifyColors.contentSecondary,
                )

                Spacer(Modifier.height(16.dp))

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
                            onConfirm(value)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
