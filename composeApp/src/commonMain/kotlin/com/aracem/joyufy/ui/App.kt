package com.aracem.joyufy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aracem.joyufy.data.cloud.AuthState
import com.aracem.joyufy.data.repository.PreferencesRepository
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
            val dashboardViewModel: DashboardViewModel = koinInject()
            val backupViewModel: BackupViewModel = koinInject()
            val driveViewModel: DriveViewModel = koinInject()
            val scope = rememberCoroutineScope()
            val backupEvent by backupViewModel.event.collectAsState()
            val driveEvent by driveViewModel.event.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            var showImportConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }

            // Auto-sync from Drive on launch
            LaunchedEffect(Unit) {
                if (driveViewModel.shouldAutoSync()) {
                    driveViewModel.syncFromCloud(silent = true)
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
                    onAddAccount = { showCreateAccount = true },
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
                    onDismiss = { showCreateAccount = false },
                    onCreated = { showCreateAccount = false },
                )
            }
        }
    }
    } // end CompositionLocalProvider
}
