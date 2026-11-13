package com.aracem.joyufy.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.aracem.joyufy.data.repository.PreferencesRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.CategorizationRule
import com.aracem.joyufy.domain.model.categoryFor
import com.aracem.joyufy.domain.model.TransactionCategory
import com.aracem.joyufy.ui.components.AccountLogo
import com.aracem.joyufy.ui.components.AccountLogoInitials
import com.aracem.joyufy.ui.components.ConfettiBurst
import com.aracem.joyufy.ui.components.formatCurrency
import com.aracem.joyufy.ui.components.formatWeekRange
import com.aracem.joyufy.ui.drive.DriveEvent
import com.aracem.joyufy.ui.drive.DriveUiState
import com.aracem.joyufy.ui.drive.DriveViewModel
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onOpenCategorizationRules: () -> Unit,
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
    val preferencesRepository: PreferencesRepository = koinInject()
    val transactionRepository: TransactionRepository = koinInject()
    val coroutineScope = rememberCoroutineScope()
    var categorizationRules by remember { mutableStateOf(preferencesRepository.getCategorizationRules()) }

    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showInvestmentFlowBackfillPreview by remember { mutableStateOf(false) }
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
            is SettingsEvent.InvestmentFlowsBackfilled -> {
                snackbarHostState.showSnackbar(strings.investmentFlowBackfillApplied.format(ev.count))
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(strings.restore) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreFromDriveConfirm = false }) { Text(strings.cancel) }
            },
        )
    }

    if (showInvestmentFlowBackfillPreview) {
        InvestmentFlowBackfillDialog(
            candidates = state.investmentFlowBackfillCandidates,
            onDismiss = { showInvestmentFlowBackfillPreview = false },
            onApply = {
                showInvestmentFlowBackfillPreview = false
                viewModel.backfillInvestmentFlows()
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
                            focusedBorderColor = MaterialTheme.joyufyColors.negative,
                            focusedLabelColor = MaterialTheme.joyufyColors.negative,
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
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
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 32.dp),
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
                SettingsSection(title = strings.language) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("" to strings.systemLanguage, "en" to "English", "es" to "Español").forEach { (code, label) ->
                            val selected = language == code
                            FilterChip(
                                selected = selected,
                                onClick = { onLanguageChange(code) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.joyufyColors.contentSecondary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary,
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
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
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
                    HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsButton(label = "${strings.rulesTitle} (${categorizationRules.size})", onClick = onOpenCategorizationRules)
                    HorizontalDivider(color = MaterialTheme.joyufyColors.border, modifier = Modifier.padding(horizontal = 16.dp))
                    InvestmentFlowBackfillRow(
                        candidates = state.investmentFlowBackfillCandidates,
                        onPreview = { showInvestmentFlowBackfillPreview = true },
                    )
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
                        labelColor = MaterialTheme.joyufyColors.negative,
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
private fun InvestmentFlowBackfillRow(
    candidates: List<InvestmentFlowBackfillCandidateUi>,
    onPreview: () -> Unit,
) {
    val strings = LocalStrings.current
    val accountCount = candidates.map { it.accountName }.distinct().size
    val description = if (candidates.isEmpty()) {
        strings.investmentFlowBackfillNoChanges
    } else {
        strings.investmentFlowBackfillDescription.format(candidates.size, accountCount)
    }
    SettingsRow(
        label = strings.investmentFlowBackfillTitle,
        description = description,
    ) {
        TextButton(
            onClick = onPreview,
            enabled = candidates.isNotEmpty(),
        ) {
            Text(
                strings.investmentFlowBackfillPreview,
                color = if (candidates.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.joyufyColors.contentDisabled,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvestmentFlowBackfillDialog(
    candidates: List<InvestmentFlowBackfillCandidateUi>,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val strings = LocalStrings.current
    val totalDeposits = candidates.sumOf { it.deposits }
    val totalWithdrawals = candidates.sumOf { it.withdrawals }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.investmentFlowBackfillDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.investmentFlowBackfillDialogText)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BackfillSummaryChip(strings.depositsEur, totalDeposits, MaterialTheme.joyufyColors.positive)
                    BackfillSummaryChip(strings.withdrawalsEur, totalWithdrawals, MaterialTheme.joyufyColors.negative)
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    candidates.take(12).forEach { candidate ->
                        BackfillCandidateRow(candidate)
                    }
                    val remaining = candidates.size - 12
                    if (remaining > 0) {
                        Text(
                            text = strings.investmentFlowBackfillMore.format(remaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                enabled = candidates.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(strings.investmentFlowBackfillApply)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun BackfillSummaryChip(
    label: String,
    amount: Double,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = "$label ${amount.formatCurrency()}",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun BackfillCandidateRow(candidate: InvestmentFlowBackfillCandidateUi) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${candidate.accountName} · ${candidate.weekDate.formatWeekRange(strings.week)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "${strings.depositsEur}: ${candidate.deposits.formatCurrency()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.joyufyColors.positive,
            )
            Text(
                text = "${strings.withdrawalsEur}: ${candidate.withdrawals.formatCurrency()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.joyufyColors.negative,
            )
        }
    }
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
        contentColor = MaterialTheme.colorScheme.primary,
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
                selectedContentColor = MaterialTheme.colorScheme.primary,
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
                labelColor = MaterialTheme.colorScheme.primary,
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
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
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
        cloudConflictPending || driveEvent is DriveEvent.RestorePrompt -> MaterialTheme.joyufyColors.negative
        driveEvent == DriveEvent.Uploading || driveEvent == DriveEvent.Downloading -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.joyufyColors.positive
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
                accent = if (driveState.authState is AuthState.Authenticated) MaterialTheme.joyufyColors.positive else MaterialTheme.joyufyColors.contentSecondary,
                container = if (driveState.authState is AuthState.Authenticated) MaterialTheme.joyufyColors.positiveContainer else MaterialTheme.colorScheme.surfaceVariant,
            )
            CloudStatusChip(
                label = syncLabel,
                accent = syncColor,
                container = syncColor.copy(alpha = 0.12f),
            )
            CloudStatusChip(
                label = if (driveState.autoSync) strings.autoSync else strings.manualSync,
                accent = if (driveState.autoSync) MaterialTheme.colorScheme.primary else MaterialTheme.joyufyColors.contentSecondary,
                container = if (driveState.autoSync) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
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
            color = MaterialTheme.joyufyColors.negative,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.joyufyColors.negativeContainer)
                .border(1.dp, MaterialTheme.joyufyColors.negative.copy(alpha = 0.35f), MaterialTheme.shapes.medium),
        ) {
            Text(
                text = strings.dangerZoneDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = MaterialTheme.joyufyColors.negative.copy(alpha = 0.18f), modifier = Modifier.padding(horizontal = 16.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizationRulesScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val preferences: PreferencesRepository = koinInject()
    val transactions: TransactionRepository = koinInject()
    var rules by remember { mutableStateOf(preferences.getCategorizationRules()) }
    var match by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var appliedCount by remember { mutableStateOf<Int?>(null) }
    var isApplying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(title = { Text(strings.rulesTitle) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(strings.goBack) }
        })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 960.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            strings.rulesDescription,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    isApplying = true
                                    try {
                                        appliedCount = withContext(Dispatchers.IO) {
                                            var count = 0
                                            transactions.getAllTransactions()
                                                .filter { it.category.isNullOrBlank() }
                                                .forEach { transaction ->
                                                    rules.categoryFor(
                                                        transaction.description.orEmpty(),
                                                        transaction.accountId,
                                                    )?.let { matched ->
                                                        transactions.updateTransaction(
                                                            transaction.id,
                                                            transaction.type,
                                                            transaction.amount,
                                                            matched,
                                                            transaction.description,
                                                            transaction.relatedAccountId,
                                                            transaction.date,
                                                        )
                                                        count++
                                                    }
                                                }
                                            count
                                        }
                                    } finally {
                                        isApplying = false
                                    }
                                }
                            },
                            enabled = !isApplying && rules.isNotEmpty(),
                        ) {
                            if (isApplying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(strings.rulesApply)
                        }
                        appliedCount?.let { count ->
                            Text(
                                "$count ${strings.rulesUpdated}",
                                color = MaterialTheme.joyufyColors.positive,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (rules.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                strings.rulesImportDescription,
                                modifier = Modifier.padding(20.dp),
                                color = MaterialTheme.joyufyColors.contentSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    items(rules, key = { it.id }) { rule ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    rule.contains,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "  →  ",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    rule.category,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                IconButton(
                                    onClick = {
                                        rules = rules.filterNot { it.id == rule.id }
                                        preferences.setCategorizationRules(rules)
                                    },
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.delete)
                                }
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(strings.rulesAdd, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = match,
                                onValueChange = { match = it },
                                label = { Text(strings.rulesContains) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it },
                                label = { Text(strings.category) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Button(
                                onClick = {
                                    rules = rules + CategorizationRule(
                                        (rules.maxOfOrNull { it.id } ?: 0L) + 1,
                                        match.trim(),
                                        category.trim(),
                                    )
                                    preferences.setCategorizationRules(rules)
                                    match = ""
                                    category = ""
                                    appliedCount = null
                                },
                                enabled = match.isNotBlank() && category.isNotBlank(),
                            ) { Text(strings.rulesAdd) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorizationRulesDialog(
    rules: List<CategorizationRule>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (List<CategorizationRule>) -> Unit,
    onApplyToUncategorized: () -> Unit,
) {
    val strings = LocalStrings.current
    var match by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    val categoryOptions = remember(category) {
        TransactionCategory.entries.map { it.label }.filter {
            category.isBlank() || it.contains(category, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.rulesTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.rulesImportDescription)
                if (rules.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                        items(rules, key = { it.id }) { rule ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${rule.contains} → ${rule.category}", modifier = Modifier.weight(1f))
                                IconButton(onClick = { onSave(rules.filterNot { it.id == rule.id }) }) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.delete)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(match, { match = it }, label = { Text(strings.rulesContains) }, singleLine = true)
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it; categoryExpanded = true },
                        label = { Text(strings.category) },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        categoryOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { category = option; categoryExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (match.isNotBlank() && category.isNotBlank()) {
                    onSave(rules + CategorizationRule(
                        id = (rules.maxOfOrNull { it.id } ?: 0L) + 1,
                        contains = match.trim(),
                        category = category.trim(),
                    ))
                    match = ""
                    category = ""
                }
            }) { Text(strings.rulesAdd) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onApplyToUncategorized) { Text(strings.rulesApply) }
                TextButton(onClick = onDismiss) { Text(strings.close) }
            }
        },
    )
}
