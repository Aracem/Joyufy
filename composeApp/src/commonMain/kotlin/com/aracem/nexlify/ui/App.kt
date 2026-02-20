package com.aracem.nexlify.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aracem.nexlify.ui.components.Sidebar
import com.aracem.nexlify.ui.dashboard.DashboardScreen
import com.aracem.nexlify.ui.navigation.Screen
import com.aracem.nexlify.ui.theme.Border
import com.aracem.nexlify.ui.theme.NexlifyTheme

@Composable
fun App() {
    NexlifyTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    currentScreen = currentScreen,
                    onScreenSelected = { currentScreen = it },
                )

                VerticalDivider(color = Border)

                when (val screen = currentScreen) {
                    is Screen.Dashboard -> DashboardScreen(
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
        }
    }
}
