package com.aracem.joyufy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.AppVersion
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.ui.components.AccountLogo
import com.aracem.joyufy.ui.components.AccountLogoInitials
import com.aracem.joyufy.ui.components.ConfettiBurst
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.joyufyColors
import joyufy.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
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
    var versionClickCount by remember { mutableStateOf(0) }
    var burstOrigin by remember { mutableStateOf<Offset?>(null) }
    var burstKey by remember { mutableStateOf(0) }

    // Confirm delete single account
    if (accountToDelete != null) {
        val account = accountToDelete!!
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text(stringResource(Res.string.confirm_delete_account)) },
            text = { Text(stringResource(Res.string.confirm_delete_account_text)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount(account.id); accountToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    // Confirm delete all data
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(Res.string.confirm_delete_all)) },
            text = { Text(stringResource(Res.string.confirm_delete_all_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        viewModel.deleteAllData {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text(stringResource(Res.string.delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
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
                text = stringResource(Res.string.settings),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── Apariencia ────────────────────────────────────────────────────
        item {
            SettingsSection(title = stringResource(Res.string.appearance)) {
                SettingsRow(
                    label = if (darkMode) stringResource(Res.string.dark_mode) else stringResource(Res.string.light_mode),
                    description = stringResource(Res.string.theme_description),
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
            SettingsSection(title = stringResource(Res.string.data)) {
                SettingsButton(label = stringResource(Res.string.export_backup), onClick = onExport)
                HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsButton(label = stringResource(Res.string.import_backup), onClick = onImport)
            }
        }

        // ── Cuentas ───────────────────────────────────────────────────────
        item {
            SettingsSection(title = stringResource(Res.string.accounts)) {
                if (state.accounts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(Res.string.no_accounts),
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
            SettingsSection(title = stringResource(Res.string.danger_zone)) {
                SettingsButton(
                    label = stringResource(Res.string.delete_all_data),
                    labelColor = Negative,
                    onClick = { showDeleteAllConfirm = true },
                )
            }
        }

        // ── Versión ───────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Joyufy v${AppVersion.NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.5f),
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            burstOrigin = Offset(
                                x = pos.x + coords.size.width / 2f,
                                y = pos.y + coords.size.height / 2f,
                            )
                        }
                        .clickable {
                            versionClickCount++
                            if (versionClickCount >= 10) {
                                burstKey++
                                versionClickCount = 0
                            }
                        },
                )
            }
        }
    }

    burstOrigin?.let { origin ->
        key(burstKey) {
            if (burstKey > 0) {
                ConfettiBurst(
                    origin = origin,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    } // end Box
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
            AccountLogo(logoUrl = account.logoUrl, size = 32.dp)
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
                text = stringResource(account.type.stringRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(Res.string.delete),
                tint = MaterialTheme.joyufyColors.contentDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private val AccountType.stringRes: StringResource
    get() = when (this) {
        AccountType.BANK -> Res.string.account_type_bank
        AccountType.INVESTMENT -> Res.string.account_type_investment
        AccountType.CASH -> Res.string.account_type_cash
    }
