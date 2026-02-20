package com.aracem.nexlify.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aracem.nexlify.ui.account.CreateAccountDialog
import com.aracem.nexlify.ui.components.Sidebar
import com.aracem.nexlify.ui.dashboard.DashboardScreen
import com.aracem.nexlify.ui.dashboard.DashboardViewModel
import com.aracem.nexlify.ui.navigation.Screen
import com.aracem.nexlify.ui.theme.NexlifyTheme
import com.aracem.nexlify.ui.theme.nexlifyColors
import org.koin.compose.koinInject

@Composable
fun App() {
    var darkMode by remember { mutableStateOf(true) }

    NexlifyTheme(darkMode = darkMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
            var showCreateAccount by remember { mutableStateOf(false) }
            val dashboardViewModel: DashboardViewModel = koinInject()

            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it },
                    darkMode = darkMode,
                    onToggleTheme = { darkMode = !darkMode },
                    onAddAccount = { showCreateAccount = true },
                )

                VerticalDivider(color = MaterialTheme.nexlifyColors.border)

                when (val screen = currentScreen) {
                    is Screen.Dashboard -> DashboardScreen(
                        viewModel = dashboardViewModel,
                        onAccountClick = { account ->
                            currentScreen = Screen.AccountDetail(account.id)
                        },
                    )
                    is Screen.AccountDetail -> {
                        // TODO: AccountDetailScreen(screen.accountId)
                    }
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
