package com.aracem.joyufy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.ui.components.AccountLogo
import com.aracem.joyufy.ui.components.AccountLogoInitials
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.joyufyColors
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    viewModel: SettingsViewModel = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()

    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Confirm delete single account
    if (accountToDelete != null) {
        val account = accountToDelete!!
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("¿Eliminar cuenta?") },
            text = { Text("Se eliminarán permanentemente la cuenta \"${account.name}\" y todas sus transacciones y snapshots. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount(account.id); accountToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) { Text("Cancelar") }
            },
        )
    }

    // Confirm delete all data
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("¿Borrar todos los datos?") },
            text = { Text("Se eliminarán todas las cuentas, transacciones y snapshots. La aplicación quedará vacía. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        viewModel.deleteAllData {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text("Borrar todo") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("Cancelar") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── Title ─────────────────────────────────────────────────────────
        item {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── Apariencia ────────────────────────────────────────────────────
        item {
            SettingsSection(title = "Apariencia") {
                SettingsRow(
                    label = if (darkMode) "Modo oscuro" else "Modo claro",
                    description = "Cambia entre tema oscuro y claro",
                ) {
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { onToggleTheme() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.4f)),
                    )
                }
            }
        }

        // ── Datos ─────────────────────────────────────────────────────────
        item {
            SettingsSection(title = "Datos") {
                SettingsButton(label = "Exportar backup", onClick = onExport)
                HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsButton(label = "Importar backup", onClick = onImport)
            }
        }

        // ── Cuentas ───────────────────────────────────────────────────────
        item {
            SettingsSection(title = "Cuentas") {
                if (state.accounts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Sin cuentas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                    }
                } else {
                    state.accounts.forEachIndexed { index, account ->
                        AccountSettingsRow(
                            account = account,
                            onDelete = { accountToDelete = account },
                        )
                        if (index < state.accounts.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.joyufyColors.border,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── Zona de peligro ───────────────────────────────────────────────
        item {
            SettingsSection(title = "Zona de peligro") {
                SettingsButton(
                    label = "Borrar todos los datos",
                    labelColor = Negative,
                    onClick = { showDeleteAllConfirm = true },
                )
            }
        }
    }
}

// ── Section container ──────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.joyufyColors.contentSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
    }
}

// ── Row with trailing slot ─────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    label: String,
    description: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.joyufyColors.contentSecondary)
            }
        }
        trailing()
    }
}

// ── Clickable row ──────────────────────────────────────────────────────────

@Composable
private fun SettingsButton(
    label: String,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
            )
        }
    }
}

// ── Account row in settings ────────────────────────────────────────────────

@Composable
private fun AccountSettingsRow(
    account: Account,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account.logoUrl != null) {
            AccountLogo(color = account.color, logoUrl = account.logoUrl, size = 32.dp)
        } else {
            AccountLogoInitials(color = account.color, name = account.name, size = 32.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = account.type.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Eliminar cuenta",
                tint = MaterialTheme.joyufyColors.contentDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private val AccountType.label: String
    get() = when (this) {
        AccountType.BANK -> "Banco"
        AccountType.INVESTMENT -> "Inversión"
        AccountType.CASH -> "Efectivo"
    }
