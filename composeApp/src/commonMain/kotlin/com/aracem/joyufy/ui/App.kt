package com.aracem.joyufy.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aracem.joyufy.data.repository.PreferencesRepository
import com.aracem.joyufy.ui.account.AccountDetailScreen
import com.aracem.joyufy.ui.account.CreateAccountDialog
import com.aracem.joyufy.ui.backup.BackupEvent
import com.aracem.joyufy.ui.backup.BackupViewModel
import com.aracem.joyufy.ui.components.Sidebar
import com.aracem.joyufy.ui.dashboard.DashboardScreen
import com.aracem.joyufy.ui.dashboard.DashboardViewModel
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

    JoyufyTheme(darkMode = darkMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
            var showCreateAccount by remember { mutableStateOf(false) }
            val dashboardViewModel: DashboardViewModel = koinInject()
            val backupViewModel: BackupViewModel = koinInject()
            val scope = rememberCoroutineScope()
            val backupEvent by backupViewModel.event.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            var showImportConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                AlertDialog(
                    onDismissRequest = { showImportConfirm = null; backupViewModel.reset() },
                    title = { Text("¿Restaurar backup?") },
                    text = { Text("Se borrarán todos los datos actuales y se reemplazarán con los del archivo. Esta acción no se puede deshacer.") },
                    confirmButton = {
                        Button(
                            onClick = { showImportConfirm?.invoke(); showImportConfirm = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Negative),
                        ) { Text("Restaurar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportConfirm = null; backupViewModel.reset() }) {
                            Text("Cancelar")
                        }
                    },
                )
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
            ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it },
                    onAddAccount = { showCreateAccount = true },
                )

                VerticalDivider(color = MaterialTheme.joyufyColors.border)

                when (val screen = currentScreen) {
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
            } // end Scaffold

            if (showCreateAccount) {
                val state by dashboardViewModel.uiState.collectAsState()
                CreateAccountDialog(
                    existingCount = state.accountSummaries.size,
                    onDismiss = { showCreateAccount = false },
                    onCreated = { showCreateAccount = false },
                )
            }
        }
    }
}
