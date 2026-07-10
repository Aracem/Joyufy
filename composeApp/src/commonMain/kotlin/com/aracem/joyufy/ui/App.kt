package com.aracem.joyufy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aracem.joyufy.data.cloud.AuthState
import com.aracem.joyufy.data.repository.BackupDiff
import com.aracem.joyufy.data.repository.PreferencesRepository
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.ui.account.AccountDetailScreen
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.StringsEn
import com.aracem.joyufy.ui.strings.StringsEs
import com.aracem.joyufy.ui.account.CreateAccountDialog
import com.aracem.joyufy.ui.backup.BackupEvent
import com.aracem.joyufy.ui.backup.BackupViewModel
import com.aracem.joyufy.ui.components.Sidebar
import com.aracem.joyufy.ui.dashboard.AccountSummary
import com.aracem.joyufy.ui.dashboard.DashboardScreen
import com.aracem.joyufy.ui.dashboard.DashboardViewModel
import com.aracem.joyufy.ui.drive.DriveEvent
import com.aracem.joyufy.ui.drive.DriveViewModel
import com.aracem.joyufy.ui.ledger.TransactionLedgerScreen
import com.aracem.joyufy.ui.navigation.LedgerInitialFilter
import com.aracem.joyufy.ui.navigation.Screen
import com.aracem.joyufy.ui.settings.SettingsScreen
import com.aracem.joyufy.ui.theme.JoyufyTheme
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

@Composable
fun App() {
    val prefsRepo: PreferencesRepository = koinInject()
    var darkMode by remember { mutableStateOf(prefsRepo.getDarkMode()) }
    var language by remember { mutableStateOf(prefsRepo.getLanguage()) }
    val strings = when (language) {
        "es" -> StringsEs
        "en" -> StringsEn
        else -> if (java.util.Locale.getDefault().language == "es") StringsEs else StringsEn
    }

    CompositionLocalProvider(LocalStrings provides strings) {
    JoyufyTheme(darkMode = darkMode) {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
        var showCommandPalette by remember { mutableStateOf(false) }
        var accountLaunchRequestId by remember { mutableStateOf(0L) }
        fun nextAccountLaunchRequestId(): Long {
            accountLaunchRequestId += 1
            return accountLaunchRequestId
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    handleAppShortcut(
                        event = event,
                        currentScreen = currentScreen,
                        onOpenCommandPalette = { showCommandPalette = true },
                        onOpenDashboard = { currentScreen = Screen.Dashboard },
                        onOpenLedger = { currentScreen = Screen.Ledger() },
                        onOpenSettings = { currentScreen = Screen.Settings },
                        onOpenTransaction = { accountId ->
                            currentScreen = Screen.AccountDetail(
                                accountId = accountId,
                                openTransactionDialog = true,
                                launchRequestId = nextAccountLaunchRequestId(),
                            )
                        },
                        onOpenSnapshot = { accountId ->
                            currentScreen = Screen.AccountDetail(
                                accountId = accountId,
                                openSnapshotDialog = true,
                                launchRequestId = nextAccountLaunchRequestId(),
                            )
                        },
                        onFocusSearch = { accountId ->
                            currentScreen = Screen.AccountDetail(
                                accountId = accountId,
                                focusSearch = true,
                                launchRequestId = nextAccountLaunchRequestId(),
                            )
                        },
                    )
                },
        ) {
            var showCreateAccount by remember { mutableStateOf(false) }
            var createAccountInitialType by remember { mutableStateOf<AccountType?>(null) }
            val dashboardViewModel: DashboardViewModel = koinInject()
            val backupViewModel: BackupViewModel = koinInject()
            val driveViewModel: DriveViewModel = koinInject()
            val scope = rememberCoroutineScope()
            val backupEvent by backupViewModel.event.collectAsState()
            val driveEvent by driveViewModel.event.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            var localImportPrompt by remember { mutableStateOf<Pair<BackupDiff, String>?>(null) }
            var cloudRestorePrompt by remember { mutableStateOf<Pair<BackupDiff, String>?>(null) }
            val isSyncingToCloud by driveViewModel.isSyncing.collectAsState()

            // Auto-preview from Drive on launch: download + diff, prompt the user
            // only if cloud differs from local. Never silently overwrites.
            LaunchedEffect(Unit) {
                if (driveViewModel.shouldAutoSync()) {
                    driveViewModel.previewFromCloud()
                }
            }

            // Handle Drive events globally
            LaunchedEffect(driveEvent) {
                val s = strings
                when (val ev = driveEvent) {
                    is DriveEvent.Success -> {
                        val msg = when (ev.message) {
                            "drive_upload_ok" -> s.syncSuccess
                            "drive_download_ok" -> s.syncSuccess
                            else -> ev.message
                        }
                        snackbarHostState.showSnackbar(msg)
                        driveViewModel.reset()
                    }
                    is DriveEvent.Error -> {
                        snackbarHostState.showSnackbar("${s.syncError}: ${ev.message}")
                        driveViewModel.reset()
                    }
                    is DriveEvent.RestorePrompt -> {
                        cloudRestorePrompt = ev.diff to ev.rawJson
                        driveViewModel.reset()
                    }
                    else -> {}
                }
            }

            // Handle all backup events globally — works regardless of which screen is active
            LaunchedEffect(backupEvent) {
                when (val ev = backupEvent) {
                    is BackupEvent.ExportReady -> {
                        withContext(Dispatchers.IO) {
                            val path = showSaveFileDialog("joyufy_backup.json")
                            if (path != null) java.io.File(path).writeText(ev.json)
                        }
                        backupViewModel.reset()
                    }
                    is BackupEvent.ImportPreview -> localImportPrompt = ev.diff to ev.rawJson
                    is BackupEvent.Success -> { snackbarHostState.showSnackbar(ev.message); backupViewModel.reset() }
                    is BackupEvent.Error -> { snackbarHostState.showSnackbar(ev.message); backupViewModel.reset() }
                    else -> {}
                }
            }

            localImportPrompt?.let { (diff, rawJson) ->
                val s = LocalStrings.current
                LocalImportPreviewDialog(
                    diff = diff,
                    s = s,
                    onDismiss = {
                        localImportPrompt = null
                        backupViewModel.reset()
                    },
                    onRestore = {
                        backupViewModel.applyImport(rawJson)
                        localImportPrompt = null
                    },
                )
            }

            cloudRestorePrompt?.let { (diff, rawJson) ->
                val s = LocalStrings.current
                CloudRestoreDiffDialog(
                    diff = diff,
                    s = s,
                    onDismiss = { cloudRestorePrompt = null },
                    onUseCloud = {
                        driveViewModel.applyCloudBackup(rawJson)
                        cloudRestorePrompt = null
                    },
                )
            }

            val dashboardState by dashboardViewModel.uiState.collectAsState()

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
            ) {

            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    currentScreen = currentScreen,
                    accounts = dashboardState.accountSummaries,
                    onScreenSelected = { currentScreen = it },
                    onAddAccount = {
                        createAccountInitialType = null
                        showCreateAccount = true
                    },
                    onAccountClick = { account -> currentScreen = Screen.AccountDetail(account.id) },
                    onQuickAdd = { account ->
                        currentScreen = Screen.AccountDetail(
                            accountId = account.id,
                            openTransactionDialog = true,
                            launchRequestId = nextAccountLaunchRequestId(),
                        )
                    },
                    onReorderAccounts = dashboardViewModel::reorderAccounts,
                    darkMode = darkMode,
                    onToggleTheme = { darkMode = !darkMode; prefsRepo.setDarkMode(darkMode) },
                )

                VerticalDivider(color = MaterialTheme.joyufyColors.border)

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val toDetail = targetState is Screen.AccountDetail
                        val fromDetail = initialState is Screen.AccountDetail
                        val toSettings = targetState is Screen.Settings
                        val direction = when {
                            toDetail   -> AnimatedContentTransitionScope.SlideDirection.Start
                            fromDetail -> AnimatedContentTransitionScope.SlideDirection.End
                            toSettings -> AnimatedContentTransitionScope.SlideDirection.Up
                            else       -> AnimatedContentTransitionScope.SlideDirection.Down
                        }
                        slideIntoContainer(direction, tween(280)) togetherWith
                            slideOutOfContainer(direction, tween(280))
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { screen ->
                    when (screen) {
                        is Screen.Dashboard -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            onExport = { backupViewModel.requestExport() },
                            onImport = {
                                scope.launch {
                                    val json = withContext(Dispatchers.IO) { showOpenFileDialog() }
                                    if (json != null) backupViewModel.importFromJson(json)
                                }
                            },
                            onCreateAccount = { type ->
                                createAccountInitialType = type
                                showCreateAccount = true
                            },
                            onReviewUncategorized = {
                                currentScreen = Screen.Ledger(LedgerInitialFilter.UNCATEGORIZED)
                            },
                            onUpdateMissingSnapshot = { account ->
                                currentScreen = Screen.AccountDetail(
                                    accountId = account.id,
                                    openSnapshotDialog = true,
                                    launchRequestId = nextAccountLaunchRequestId(),
                                )
                            },
                        )
                        is Screen.AccountDetail -> AccountDetailScreen(
                            accountId = screen.accountId,
                            openSnapshotDialog = screen.openSnapshotDialog,
                            openTransactionDialog = screen.openTransactionDialog,
                            focusSearch = screen.focusSearch,
                            launchRequestId = screen.launchRequestId,
                            onBack = { currentScreen = Screen.Dashboard },
                        )
                        is Screen.Ledger -> TransactionLedgerScreen(
                            initialFilter = screen.initialFilter,
                        )
                        is Screen.Settings -> SettingsScreen(
                            darkMode = darkMode,
                            onToggleTheme = { darkMode = !darkMode; prefsRepo.setDarkMode(darkMode) },
                            language = language,
                            onLanguageChange = { language = it; prefsRepo.setLanguage(it) },
                            onExport = { backupViewModel.requestExport() },
                            onImport = {
                                scope.launch {
                                    val json = withContext(Dispatchers.IO) { showOpenFileDialog() }
                                    if (json != null) backupViewModel.importFromJson(json)
                                }
                            },
                            cloudConflictPending = cloudRestorePrompt != null,
                        )
                    }
                }
            }
            } // end Scaffold

            if (showCommandPalette) {
                CommandPalette(
                    accounts = dashboardState.accountSummaries,
                    currentScreen = currentScreen,
                    onDismiss = { showCommandPalette = false },
                    onOpenDashboard = {
                        currentScreen = Screen.Dashboard
                        showCommandPalette = false
                    },
                    onOpenLedger = {
                        currentScreen = Screen.Ledger()
                        showCommandPalette = false
                    },
                    onReviewUncategorized = {
                        currentScreen = Screen.Ledger(LedgerInitialFilter.UNCATEGORIZED)
                        showCommandPalette = false
                    },
                    onOpenSettings = {
                        currentScreen = Screen.Settings
                        showCommandPalette = false
                    },
                    onCreateAccount = { type ->
                        createAccountInitialType = type
                        showCreateAccount = true
                        showCommandPalette = false
                    },
                    onExport = {
                        backupViewModel.requestExport()
                        showCommandPalette = false
                    },
                    onImport = {
                        scope.launch {
                            val json = withContext(Dispatchers.IO) { showOpenFileDialog() }
                            if (json != null) backupViewModel.importFromJson(json)
                        }
                        showCommandPalette = false
                    },
                    onOpenAccount = { accountId ->
                        currentScreen = Screen.AccountDetail(accountId)
                        showCommandPalette = false
                    },
                    onOpenTransaction = { accountId ->
                        currentScreen = Screen.AccountDetail(
                            accountId = accountId,
                            openTransactionDialog = true,
                            launchRequestId = nextAccountLaunchRequestId(),
                        )
                        showCommandPalette = false
                    },
                    onOpenSnapshot = { accountId ->
                        currentScreen = Screen.AccountDetail(
                            accountId = accountId,
                            openSnapshotDialog = true,
                            launchRequestId = nextAccountLaunchRequestId(),
                        )
                        showCommandPalette = false
                    },
                    onFocusSearch = { accountId ->
                        currentScreen = Screen.AccountDetail(
                            accountId = accountId,
                            focusSearch = true,
                            launchRequestId = nextAccountLaunchRequestId(),
                        )
                        showCommandPalette = false
                    },
                )
            }

            if (showCreateAccount) {
                CreateAccountDialog(
                    existingCount = dashboardState.accountSummaries.size,
                    onDismiss = {
                        showCreateAccount = false
                        createAccountInitialType = null
                    },
                    onCreated = {
                        showCreateAccount = false
                        createAccountInitialType = null
                    },
                    initialType = createAccountInitialType,
                )
            }

            // Full-screen overlay while close-time upload is in flight. Sits on
            // top of everything so the user sees the syncing state until the
            // upload completes (or Main.kt's 5s timeout closes the app anyway).
            AnimatedVisibility(
                visible = isSyncingToCloud,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = strings.syncingToDrive,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
    } // end CompositionLocalProvider
}

private data class CommandPaletteItem(
    val title: String,
    val subtitle: String? = null,
    val keywords: String = title,
    val onClick: () -> Unit,
)

@Composable
private fun CommandPalette(
    accounts: List<AccountSummary>,
    currentScreen: Screen,
    onDismiss: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenLedger: () -> Unit,
    onReviewUncategorized: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateAccount: (AccountType) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenSnapshot: (Long) -> Unit,
    onFocusSearch: (Long) -> Unit,
) {
    val strings = LocalStrings.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val currentAccountId = (currentScreen as? Screen.AccountDetail)?.accountId
    val currentAccount = accounts.firstOrNull { it.account.id == currentAccountId }?.account
    val actions = buildList {
        add(CommandPaletteItem(strings.sidebarDashboard, onClick = onOpenDashboard))
        add(CommandPaletteItem(strings.sidebarTransactions, subtitle = strings.data, onClick = onOpenLedger))
        add(CommandPaletteItem(strings.commandReviewUncategorized, subtitle = strings.transactionLedger, onClick = onReviewUncategorized))
        add(CommandPaletteItem(strings.sidebarSettings, onClick = onOpenSettings))
        add(CommandPaletteItem(strings.createBankAccount, subtitle = strings.newAccount, onClick = { onCreateAccount(AccountType.BANK) }))
        add(CommandPaletteItem(strings.createInvestmentAccount, subtitle = strings.newAccount, onClick = { onCreateAccount(AccountType.INVESTMENT) }))
        add(CommandPaletteItem(strings.createCashAccount, subtitle = strings.newAccount, onClick = { onCreateAccount(AccountType.CASH) }))
        add(CommandPaletteItem(strings.exportBackup, subtitle = strings.data, onClick = onExport))
        add(CommandPaletteItem(strings.importBackup, subtitle = strings.data, onClick = onImport))
        if (currentAccount != null) {
            add(
                CommandPaletteItem(
                    title = strings.commandNewTransaction,
                    subtitle = currentAccount.name,
                    keywords = "${strings.commandNewTransaction} ${strings.addTransaction} ${currentAccount.name}",
                    onClick = { onOpenTransaction(currentAccount.id) },
                ),
            )
            add(
                CommandPaletteItem(
                    title = strings.commandFocusSearch,
                    subtitle = currentAccount.name,
                    keywords = "${strings.commandFocusSearch} ${strings.searchDescriptionCategory} ${currentAccount.name}",
                    onClick = { onFocusSearch(currentAccount.id) },
                ),
            )
            if (currentAccount.type == AccountType.INVESTMENT) {
                add(
                    CommandPaletteItem(
                        title = strings.commandUpdateSnapshot,
                        subtitle = currentAccount.name,
                        keywords = "${strings.commandUpdateSnapshot} ${strings.updateValue} ${currentAccount.name}",
                        onClick = { onOpenSnapshot(currentAccount.id) },
                    ),
                )
            }
        }
        accounts.forEach { summary ->
            add(
                CommandPaletteItem(
                    title = strings.commandOpenAccount.format(summary.account.name),
                    subtitle = when (summary.account.type) {
                        AccountType.BANK -> strings.accountTypeBank
                        AccountType.INVESTMENT -> strings.accountTypeInvestment
                        AccountType.CASH -> strings.accountTypeCash
                    },
                    keywords = summary.account.name,
                    onClick = { onOpenAccount(summary.account.id) },
                ),
            )
        }
    }
    val filtered = remember(query, actions) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            actions
        } else {
            actions.filter { item ->
                item.title.lowercase().contains(q) ||
                    item.subtitle?.lowercase()?.contains(q) == true ||
                    item.keywords.lowercase().contains(q)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(620.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = strings.commandPalette,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.commandPalettePlaceholder) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.joyufyColors.border,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = strings.commandPaletteNoResults,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filtered, key = { it.title + (it.subtitle ?: "") }) { item ->
                            CommandPaletteRow(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandPaletteRow(item: CommandPaletteItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = item.onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.subtitle?.let { subtitle ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun handleAppShortcut(
    event: KeyEvent,
    currentScreen: Screen,
    onOpenCommandPalette: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenSnapshot: (Long) -> Unit,
    onFocusSearch: (Long) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown || (!event.isMetaPressed && !event.isCtrlPressed)) return false
    return when (event.key) {
        Key.One -> {
            onOpenDashboard()
            true
        }
        Key.Two -> {
            onOpenLedger()
            true
        }
        Key.Comma -> {
            onOpenSettings()
            true
        }
        Key.K -> {
            onOpenCommandPalette()
            true
        }
        Key.N -> {
            val screen = currentScreen as? Screen.AccountDetail ?: return false
            onOpenTransaction(screen.accountId)
            true
        }
        Key.U -> {
            val screen = currentScreen as? Screen.AccountDetail ?: return false
            onOpenSnapshot(screen.accountId)
            true
        }
        Key.F -> {
            val screen = currentScreen as? Screen.AccountDetail ?: return false
            onFocusSearch(screen.accountId)
            true
        }
        else -> false
    }
}

@Composable
private fun LocalImportPreviewDialog(
    diff: BackupDiff,
    s: com.aracem.joyufy.ui.strings.Strings,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.importPreviewTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.importPreviewSubtitle, style = MaterialTheme.typography.bodyMedium)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = s.importPreviewBackupTimestamp.format(formatDateTime(diff.cloudExportedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (diff.hasChanges) s.localDataCurrent else s.importPreviewNoChanges,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }
                CloudDiffTableHeader(s)
                CloudDiffTableRow(s.cloudDiffAccounts, diff.accountsAdded, diff.accountsRemoved, diff.accountsModified)
                CloudDiffTableRow(s.cloudDiffTransactions, diff.transactionsAdded, diff.transactionsRemoved, diff.transactionsModified)
                CloudDiffTableRow(s.cloudDiffSnapshots, diff.snapshotsAdded, diff.snapshotsRemoved, diff.snapshotsModified)
            }
        },
        confirmButton = {
            Button(
                onClick = onRestore,
                colors = ButtonDefaults.buttonColors(containerColor = Negative),
            ) { Text(s.importPreviewRestore) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
    )
}

@Composable
private fun CloudRestoreDiffDialog(
    diff: BackupDiff,
    s: com.aracem.joyufy.ui.strings.Strings,
    onDismiss: () -> Unit,
    onUseCloud: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.cloudDiffTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.cloudDiffSubtitle, style = MaterialTheme.typography.bodyMedium)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = s.cloudBackupTimestamp.format(formatDateTime(diff.cloudExportedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = s.localDataCurrent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }
                CloudDiffTableHeader(s)
                CloudDiffTableRow(s.cloudDiffAccounts, diff.accountsAdded, diff.accountsRemoved, diff.accountsModified)
                CloudDiffTableRow(s.cloudDiffTransactions, diff.transactionsAdded, diff.transactionsRemoved, diff.transactionsModified)
                CloudDiffTableRow(s.cloudDiffSnapshots, diff.snapshotsAdded, diff.snapshotsRemoved, diff.snapshotsModified)
            }
        },
        confirmButton = {
            Button(
                onClick = onUseCloud,
                colors = ButtonDefaults.buttonColors(containerColor = Negative),
            ) { Text(s.useCloud) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.keepLocal) }
        },
    )
}

@Composable
private fun CloudDiffTableHeader(s: com.aracem.joyufy.ui.strings.Strings) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = s.cloudDiffEntity,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(s.cloudDiffAdded, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary, modifier = Modifier.width(70.dp))
        Text(s.cloudDiffRemoved, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary, modifier = Modifier.width(78.dp))
        Text(s.cloudDiffModified, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.contentSecondary, modifier = Modifier.width(78.dp))
    }
}

@Composable
private fun CloudDiffTableRow(
    label: String,
    added: Int,
    removed: Int,
    modified: Int,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text("+$added", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(70.dp))
        Text("-$removed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(78.dp))
        Text("~$modified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(78.dp))
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
    val local = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "%02d/%02d/%d %02d:%02d".format(
        local.dayOfMonth,
        local.monthNumber,
        local.year,
        local.hour,
        local.minute,
    )
}
