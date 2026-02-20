package com.aracem.nexlify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aracem.nexlify.ui.navigation.Screen
import com.aracem.nexlify.ui.theme.Accent
import com.aracem.nexlify.ui.theme.ContentSecondary
import com.aracem.nexlify.ui.theme.SurfaceDefault
import com.aracem.nexlify.ui.theme.SurfaceRaised

@Composable
fun Sidebar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(SurfaceDefault)
            .padding(vertical = 24.dp, horizontal = 12.dp),
    ) {
        // Logo / App name
        Text(
            text = "Nexlify",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(28.dp))

        SidebarItem(
            label = "Dashboard",
            icon = Icons.Default.Home,
            selected = currentScreen is Screen.Dashboard,
            onClick = { onScreenSelected(Screen.Dashboard) },
        )

        Spacer(Modifier.weight(1f))

        SidebarItem(
            label = "Ajustes",
            icon = Icons.Default.Settings,
            selected = currentScreen is Screen.Settings,
            onClick = { onScreenSelected(Screen.Settings) },
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Accent else ContentSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else ContentSecondary,
        )
    }
}
