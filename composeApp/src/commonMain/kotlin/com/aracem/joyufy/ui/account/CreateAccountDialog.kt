package com.aracem.joyufy.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aracem.joyufy.data.mapper.toComposeColor
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.BankPreset
import com.aracem.joyufy.domain.model.BankPresets
import com.aracem.joyufy.ui.components.AccountLogo
import com.aracem.joyufy.ui.theme.AccountPalette
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.joyufyColors
import org.koin.compose.koinInject

@Composable
fun CreateAccountDialog(
    existingCount: Int,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    editingAccount: Account? = null,
    viewModel: CreateAccountViewModel = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()

    // Reset / pre-populate on first composition
    LaunchedEffect(Unit) {
        if (editingAccount != null) viewModel.resetForEdit(editingAccount)
        else viewModel.reset()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // ── Header ────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (editingAccount != null) "Editar cuenta" else "Nueva cuenta",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.joyufyColors.contentSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Presets ───────────────────────────────────────────────
                Text(
                    text = "Banco o plataforma",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BankPresets) { preset ->
                        BankPresetChip(
                            preset = preset,
                            selected = state.logoUrl == preset.logoUrl,
                            onClick = { viewModel.onPresetSelected(preset) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Nombre ────────────────────────────────────────────────
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej: Banco Santander") },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        focusedLabelColor = Accent,
                    ),
                )

                Spacer(Modifier.height(16.dp))

                // ── Tipo ──────────────────────────────────────────────────
                Text(
                    text = "Tipo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { type ->
                        val selected = state.type == type
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onTypeChange(type) },
                            label = { Text(type.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.15f),
                                selectedLabelColor = Accent,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                selectedBorderColor = Accent,
                                borderColor = MaterialTheme.joyufyColors.border,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Color ─────────────────────────────────────────────────
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                Spacer(Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                ) {
                    items(AccountPalette) { color ->
                        ColorSwatch(
                            color = color,
                            selected = state.selectedColor == color,
                            onClick = { viewModel.onColorChange(color) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Custom hex input
                var hexInput by remember { mutableStateOf("") }
                var hexError by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { value ->
                        hexInput = value
                        val clean = value.removePrefix("#")
                        hexError = clean.isNotEmpty() && clean.length != 6
                        if (clean.length == 6) {
                            runCatching { "#$clean".toComposeColor() }
                                .onSuccess {
                                    viewModel.onColorChange(it)
                                    hexError = false
                                }
                                .onFailure { hexError = true }
                        }
                    },
                    label = { Text("Color personalizado") },
                    placeholder = { Text("#7B6EF6") },
                    isError = hexError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(state.selectedColor)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        focusedLabelColor = Accent,
                    ),
                )

                Spacer(Modifier.height(24.dp))

                // ── Actions ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.joyufyColors.contentSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (editingAccount != null) {
                                viewModel.saveEdit(
                                    account = editingAccount,
                                    onSuccess = { onCreated(); onDismiss() },
                                )
                            } else {
                                viewModel.save(
                                    existingCount = existingCount,
                                    onSuccess = { onCreated(); onDismiss() },
                                )
                            }
                        },
                        enabled = !state.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(if (editingAccount != null) "Guardar cambios" else "Crear cuenta")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun BankPresetChip(
    preset: BankPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) Accent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (selected) Modifier.border(1.dp, Accent, MaterialTheme.shapes.small)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val color = runCatching { preset.defaultColor.toComposeColor() }.getOrElse { Color.Gray }
        AccountLogo(color = color, logoUrl = preset.logoUrl, size = 32.dp)
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Accent else MaterialTheme.joyufyColors.contentSecondary,
        )
    }
}

private val AccountType.label: String
    get() = when (this) {
        AccountType.BANK -> "Banco"
        AccountType.INVESTMENT -> "Inversión"
        AccountType.CASH -> "Efectivo"
    }
