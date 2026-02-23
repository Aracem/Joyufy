package com.aracem.joyufy.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aracem.joyufy.ui.account.AccountDetailScreen
import com.aracem.joyufy.ui.account.CreateAccountDialog
import com.aracem.joyufy.ui.backup.BackupViewModel
import com.aracem.joyufy.ui.components.Sidebar
import com.aracem.joyufy.ui.dashboard.DashboardScreen
import com.aracem.joyufy.ui.dashboard.DashboardViewModel
import com.aracem.joyufy.ui.navigation.Screen
import com.aracem.joyufy.ui.theme.JoyufyTheme
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun App() {
    var darkMode by remember { mutableStateOf(true) }

    JoyufyTheme(darkMode = darkMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
            var showCreateAccount by remember { mutableStateOf(false) }
            val dashboardViewModel: DashboardViewModel = koinInject()
            val backupViewModel: BackupViewModel = koinInject()
            val scope = rememberCoroutineScope()
            val backupEvent by backupViewModel.event.collectAsState()

            // Handle file writing when export data is ready
            LaunchedEffect(backupEvent) {
                val ev = backupEvent
                if (ev is com.aracem.joyufy.ui.backup.BackupEvent.ExportReady) {
                    withContext(Dispatchers.IO) {
                        val path = showSaveFileDialog("joyufy_backup.json")
                        if (path != null) java.io.File(path).writeText(ev.json)
                    }
                    backupViewModel.reset()
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it },
                    darkMode = darkMode,
                    onToggleTheme = { darkMode = !darkMode },
                    onAddAccount = { showCreateAccount = true },
                )

                VerticalDivider(color = MaterialTheme.joyufyColors.border)

                when (val screen = currentScreen) {
                    is Screen.Dashboard -> DashboardScreen(
                        viewModel = dashboardViewModel,
                        backupViewModel = backupViewModel,
                        onAccountClick = { account ->
                            currentScreen = Screen.AccountDetail(account.id)
                        },
                        onExport = {
                            backupViewModel.requestExport()
                            scope.launch {
                                // Wait for ExportReady via event — handled inside DashboardScreen
                                // File writing happens in the LaunchedEffect there via onExportReady
                            }
                        },
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
                    is Screen.Settings -> {
                        // TODO: SettingsScreen()
                    }
                }
            }

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
