package com.aracem.joyufy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.aracem.joyufy.ui.dashboard.DashboardScreen
import com.aracem.joyufy.ui.dashboard.DashboardViewModel
import com.aracem.joyufy.ui.drive.DriveEvent
import com.aracem.joyufy.ui.drive.DriveViewModel
import com.aracem.joyufy.ui.navigation.Screen
import com.aracem.joyufy.ui.settings.SettingsScreen
import com.aracem.joyufy.ui.theme.JoyufyTheme
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        Surface(modifier = Modifier.fillMaxSize()) {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
            var showCreateAccount by remember { mutableStateOf(false) }
            var createAccountInitialType by remember { mutableStateOf<AccountType?>(null) }
            val dashboardViewModel: DashboardViewModel = koinInject()
            val backupViewModel: BackupViewModel = koinInject()
            val driveViewModel: DriveViewModel = koinInject()
            val scope = rememberCoroutineScope()
            val backupEvent by backupViewModel.event.collectAsState()
            val driveEvent by driveViewModel.event.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            var showImportConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
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
                    is BackupEvent.ImportReady -> showImportConfirm = ev.onConfirm
                    is BackupEvent.Success -> { snackbarHostState.showSnackbar(ev.message); backupViewModel.reset() }
                    is BackupEvent.Error -> { snackbarHostState.showSnackbar(ev.message); backupViewModel.reset() }
                    else -> {}
                }
            }

            if (showImportConfirm != null) {
                val s = LocalStrings.current
                AlertDialog(
                    onDismissRequest = { showImportConfirm = null; backupViewModel.reset() },
                    title = { Text(s.confirmRestoreBackup) },
                    text = { Text(s.confirmRestoreBackupText) },
                    confirmButton = {
                        Button(
                            onClick = { showImportConfirm?.invoke(); showImportConfirm = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Negative),
                        ) { Text(s.restore) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportConfirm = null; backupViewModel.reset() }) {
                            Text(s.cancel)
                        }
                    },
                )
            }

            cloudRestorePrompt?.let { (diff, rawJson) ->
                val s = LocalStrings.current
                AlertDialog(
                    onDismissRequest = { cloudRestorePrompt = null },
                    title = { Text(s.cloudDiffTitle) },
                    text = {
                        Column {
                            Text(s.cloudDiffSubtitle, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            DiffRow(s.cloudDiffAccounts, diff.accountsAdded, diff.accountsRemoved, diff.accountsModified, s)
                            DiffRow(s.cloudDiffTransactions, diff.transactionsAdded, diff.transactionsRemoved, diff.transactionsModified, s)
                            DiffRow(s.cloudDiffSnapshots, diff.snapshotsAdded, diff.snapshotsRemoved, diff.snapshotsModified, s)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                driveViewModel.applyCloudBackup(rawJson)
                                cloudRestorePrompt = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Negative),
                        ) { Text(s.useCloud) }
                    },
                    dismissButton = {
                        TextButton(onClick = { cloudRestorePrompt = null }) { Text(s.keepLocal) }
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
                            onAccountClick = { account ->
                                currentScreen = Screen.AccountDetail(account.id)
                            },
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
                        )
                        is Screen.AccountDetail -> AccountDetailScreen(
                            accountId = screen.accountId,
                            onBack = { currentScreen = Screen.Dashboard },
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
                        )
                    }
                }
            }
            } // end Scaffold

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

@Composable
private fun DiffRow(
    label: String,
    added: Int,
    removed: Int,
    modified: Int,
    s: com.aracem.joyufy.ui.strings.Strings,
) {
    if (added == 0 && removed == 0 && modified == 0) return
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 12.dp),
        )
        val parts = buildList {
            if (added > 0) add("+$added ${s.cloudDiffAdded}")
            if (removed > 0) add("-$removed ${s.cloudDiffRemoved}")
            if (modified > 0) add("~$modified ${s.cloudDiffModified}")
        }
        Text(
            text = parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
        )
    }
}
