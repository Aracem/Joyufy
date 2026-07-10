package com.aracem.joyufy.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.aracem.joyufy.data.cloud.AuthState
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.ui.components.AccountLogo
import com.aracem.joyufy.ui.components.AccountLogoInitials
import com.aracem.joyufy.ui.components.ConfettiBurst
import com.aracem.joyufy.ui.drive.DriveEvent
import com.aracem.joyufy.ui.drive.DriveUiState
import com.aracem.joyufy.ui.drive.DriveViewModel
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.NegativeDim
import com.aracem.joyufy.ui.theme.Positive
import com.aracem.joyufy.ui.theme.PositiveDim
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

private enum class SettingsTab { GENERAL, ACCOUNTS, DATA, CLOUD, ABOUT }

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportCsv: () -> Unit,
    cloudConflictPending: Boolean = false,
    viewModel: SettingsViewModel = koinInject(),
    driveViewModel: DriveViewModel = koinInject(),
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsState()
    val settingsEvent by viewModel.event.collectAsState()
    val driveState by driveViewModel.uiState.collectAsState()
    val driveEvent by driveViewModel.event.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var deleteAllConfirmationInput by remember { mutableStateOf("") }
    var showRestoreFromDriveConfirm by remember { mutableStateOf(false) }
    var versionClickCount by remember { mutableStateOf(0) }
    var burstOrigin by remember { mutableStateOf<Offset?>(null) }
    var burstKey by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }

    LaunchedEffect(settingsEvent) {
        when (val ev = settingsEvent) {
            is SettingsEvent.AccountDeleted -> {
                val result = snackbarHostState.showSnackbar(
                    message = strings.accountDeleted.format(ev.name),
                    actionLabel = strings.undo,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoLastAccountDelete()
                }
                viewModel.resetEvent()
            }
            is SettingsEvent.AccountArchived -> {
                val result = snackbarHostState.showSnackbar(
                    message = strings.accountArchived.format(ev.name),
                    actionLabel = strings.undo,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoLastAccountArchive()
                }
                viewModel.resetEvent()
            }
            is SettingsEvent.Restored -> {
                snackbarHostState.showSnackbar(strings.accountRestored.format(ev.name))
                viewModel.resetEvent()
            }
            is SettingsEvent.Error -> {
                snackbarHostState.showSnackbar(ev.message.ifBlank { "Error" })
                viewModel.resetEvent()
            }
            SettingsEvent.Idle -> Unit
        }
    }

    // Confirm delete single account
    if (accountToDelete != null) {
        val account = accountToDelete!!
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text(strings.confirmDeleteAccount) },
            text = { Text(strings.confirmDeleteAccountText) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount(account.id); accountToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) { Text(strings.cancel) }
            },
        )
    }

    // Confirm restore from Drive
    if (showRestoreFromDriveConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreFromDriveConfirm = false },
            title = { Text(strings.confirmRestoreFromDrive) },
            text = { Text(strings.confirmRestoreFromDriveText) },
            confirmButton = {
                Button(
                    onClick = { showRestoreFromDriveConfirm = false; driveViewModel.syncFromCloud() },
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text(strings.restore) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreFromDriveConfirm = false }) { Text(strings.cancel) }
            },
        )
    }

    // Confirm delete all data
    if (showDeleteAllConfirm) {
        val expectedPhrase = strings.confirmDeleteAllPhrase
        AlertDialog(
            onDismissRequest = {
                showDeleteAllConfirm = false
                deleteAllConfirmationInput = ""
            },
            title = { Text(strings.confirmDeleteAll) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.confirmDeleteAllText)
                    Text(
                        text = strings.confirmDeleteAllTypedInstruction.format(expectedPhrase),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                    OutlinedTextField(
                        value = deleteAllConfirmationInput,
                        onValueChange = { deleteAllConfirmationInput = it },
                        label = { Text(strings.confirmDeleteAllInputLabel) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Negative,
                            focusedLabelColor = Negative,
                            unfocusedBorderColor = MaterialTheme.joyufyColors.border,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        deleteAllConfirmationInput = ""
                        viewModel.deleteAllData {}
                    },
                    enabled = deleteAllConfirmationInput == expectedPhrase,
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text(strings.deleteAll) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirm = false
                        deleteAllConfirmationInput = ""
                    },
                ) { Text(strings.cancel) }
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
                text = strings.settings,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        item {
            SettingsTabs(
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
        }

        // ── Idioma ────────────────────────────────────────────────────────
        if (selectedTab == SettingsTab.GENERAL) {
            item {
                SettingsSection(title = "Idioma / Language") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("" to "Sistema / System", "en" to "English", "es" to "Español").forEach { (code, label) ->
                            val selected = language == code
                            FilterChip(
                                selected = selected,
                                onClick = { onLanguageChange(code) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent.copy(alpha = 0.15f),
                                    selectedLabelColor = Accent,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.joyufyColors.contentSecondary,
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
                }
            }

            // ── Apariencia ────────────────────────────────────────────────────
            item {
                SettingsSection(title = strings.appearance) {
                    SettingsRow(
                        label = if (darkMode) strings.darkMode else strings.lightMode,
                        description = strings.themeDescription,
                    ) {
                        Switch(
                            checked = darkMode,
                            onCheckedChange = { onToggleTheme() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.4f)),
                        )
                    }
                }
            }
        }

        // ── Datos ─────────────────────────────────────────────────────────
        if (selectedTab == SettingsTab.DATA) {
            item {
                SettingsSection(title = strings.data) {
                    SettingsButton(label = strings.exportBackup, onClick = onExport)
                    HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsButton(label = strings.importBackup, onClick = onImport)
                    HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsButton(label = strings.csvImportTitle, onClick = onImportCsv)
                }
            }
        }

        // ── Cloud Sync ────────────────────────────────────────────────────
        if (selectedTab == SettingsTab.CLOUD) {
            item {
                CloudSyncSection(
                    driveState = driveState,
                    onConnect = { driveViewModel.signIn() },
                    onDisconnect = { driveViewModel.signOut() },
                    onUpload = { driveViewModel.syncToCloud() },
                    onRestore = { showRestoreFromDriveConfirm = true },
                    onAutoSyncChange = { driveViewModel.setAutoSync(it) },
                    driveEvent = driveEvent,
                    cloudConflictPending = cloudConflictPending,
                )
            }
        }

        // ── Cuentas ───────────────────────────────────────────────────────
        if (selectedTab == SettingsTab.ACCOUNTS) {
            item {
                SettingsSection(title = strings.accounts) {
                    if (state.accounts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                strings.noAccounts,
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
        }

        // ── Zona de peligro ───────────────────────────────────────────────
        if (selectedTab == SettingsTab.DATA) {
            item {
                DangerZoneSection {
                    SettingsButton(
                        label = strings.deleteAllData,
                        labelColor = Negative,
                        onClick = { showDeleteAllConfirm = true },
                    )
                }
            }
        }

        // ── Versión ───────────────────────────────────────────────────────
        if (selectedTab == SettingsTab.ABOUT) {
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
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
    )
    } // end Box
}

@Composable
private fun SettingsTabs(
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
) {
    val strings = LocalStrings.current
    val tabs = listOf(
        SettingsTab.GENERAL to strings.settingsTabGeneral,
        SettingsTab.ACCOUNTS to strings.settingsTabAccounts,
        SettingsTab.DATA to strings.settingsTabData,
        SettingsTab.CLOUD to strings.settingsTabCloud,
        SettingsTab.ABOUT to strings.settingsTabAbout,
    )
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = Accent,
        divider = {},
    ) {
        tabs.forEach { (tab, label) ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                selectedContentColor = Accent,
                unselectedContentColor = MaterialTheme.joyufyColors.contentSecondary,
            )
        }
    }
}

// ── Cloud Sync section ────────────────────────────────────────────────────────

@Composable
private fun CloudSyncSection(
    driveState: DriveUiState,
    driveEvent: DriveEvent,
    cloudConflictPending: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onUpload: () -> Unit,
    onRestore: () -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    val isAuthenticated = driveState.authState is AuthState.Authenticated
    val isAuthenticating = driveState.authState is AuthState.Authenticating

    SettingsSection(title = strings.cloudSync) {
        CloudStatusChips(
            driveState = driveState,
            driveEvent = driveEvent,
            cloudConflictPending = cloudConflictPending,
        )

        AnimatedVisibility(visible = !isAuthenticated) {
            SettingsButton(
                label = if (isAuthenticating) strings.syncing else strings.connectDrive,
                labelColor = Accent,
                onClick = { if (!isAuthenticating) onConnect() },
            )
        }

        AnimatedVisibility(visible = isAuthenticated) {
            Column {
                // Email
                val email = (driveState.authState as? AuthState.Authenticated)?.email ?: ""
                SettingsRow(
                    label = strings.driveConnected.format(email),
                    description = if (driveState.lastSyncAt > 0L) {
                        strings.lastSync.format(formatDateTime(driveState.lastSyncAt))
                    } else null,
                ) {
                    TextButton(onClick = onDisconnect) {
                        Text(strings.disconnectDrive, color = MaterialTheme.joyufyColors.contentSecondary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))

                // Auto-sync toggle
                SettingsRow(label = strings.autoSync, description = strings.autoSyncDescription) {
                    Switch(
                        checked = driveState.autoSync,
                        onCheckedChange = onAutoSyncChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.4f)),
                    )
                }

                HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))

                SettingsButton(label = strings.uploadNow, onClick = onUpload)

                HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))

                SettingsButton(label = strings.restoreFromDrive, onClick = onRestore)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudStatusChips(
    driveState: DriveUiState,
    driveEvent: DriveEvent,
    cloudConflictPending: Boolean,
) {
    val strings = LocalStrings.current
    val authLabel = when (val auth = driveState.authState) {
        is AuthState.Authenticated -> strings.cloudStatusConnected
        AuthState.Authenticating -> strings.cloudStatusConnecting
        AuthState.Unauthenticated -> strings.cloudStatusDisconnected
    }
    val authDescription = (driveState.authState as? AuthState.Authenticated)?.email
    val syncLabel = when (driveEvent) {
        DriveEvent.Uploading -> strings.cloudStatusUploading
        DriveEvent.Downloading -> strings.cloudStatusDownloading
        is DriveEvent.RestorePrompt -> strings.cloudStatusConflict
        else -> if (cloudConflictPending) strings.cloudStatusConflict else strings.cloudStatusReady
    }
    val syncColor = when {
        cloudConflictPending || driveEvent is DriveEvent.RestorePrompt -> Negative
        driveEvent == DriveEvent.Uploading || driveEvent == DriveEvent.Downloading -> Accent
        else -> Positive
    }
    val lastSync = if (driveState.lastSyncAt > 0L) {
        strings.lastSync.format(formatDateTime(driveState.lastSyncAt))
    } else {
        strings.lastSyncNever
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CloudStatusChip(
                label = authDescription ?: authLabel,
                accent = if (driveState.authState is AuthState.Authenticated) Positive else MaterialTheme.joyufyColors.contentSecondary,
                container = if (driveState.authState is AuthState.Authenticated) PositiveDim else MaterialTheme.colorScheme.surfaceVariant,
            )
            CloudStatusChip(
                label = syncLabel,
                accent = syncColor,
                container = syncColor.copy(alpha = 0.12f),
            )
            CloudStatusChip(
                label = if (driveState.autoSync) strings.autoSync else strings.manualSync,
                accent = if (driveState.autoSync) Accent else MaterialTheme.joyufyColors.contentSecondary,
                container = if (driveState.autoSync) Accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Text(
            text = lastSync,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
    }
}

@Composable
private fun CloudStatusChip(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
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

@Composable
private fun DangerZoneSection(
    content: @Composable ColumnScope.() -> Unit,
) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = strings.dangerZone,
            style = MaterialTheme.typography.labelMedium,
            color = Negative,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(NegativeDim)
                .border(1.dp, Negative.copy(alpha = 0.35f), MaterialTheme.shapes.medium),
        ) {
            Text(
                text = strings.dangerZoneDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = Negative.copy(alpha = 0.18f), modifier = Modifier.padding(horizontal = 16.dp))
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
    val strings = LocalStrings.current
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
                text = when (account.type) {
                    AccountType.BANK -> strings.accountTypeBank
                    AccountType.INVESTMENT -> strings.accountTypeInvestment
                    AccountType.CASH -> strings.accountTypeCash
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = strings.delete,
                tint = MaterialTheme.joyufyColors.contentDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%d %02d:%02d".format(
        local.dayOfMonth,
        local.monthNumber,
        local.year,
        local.hour,
        local.minute,
    )
}
