package com.aracem.joyufy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.AccentDim

@Composable
fun MissingSnapshotBanner(
    accounts: List<Account>,
    onAccountClick: (Account) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accounts.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(AccentDim)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Actualización semanal pendiente",
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
            )
            Spacer(Modifier.height(2.dp))
            val names = accounts.joinToString(", ") { it.name }
            Text(
                text = names,
                style = MaterialTheme.typography.bodySmall,
                color = Accent.copy(alpha = 0.75f),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Quick links per account
        accounts.forEach { account ->
            TextButton(
                onClick = { onAccountClick(account) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = account.name.take(12),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = Accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
