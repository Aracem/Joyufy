package com.aracem.joyufy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.ui.theme.Positive

private val PositiveDim = Positive.copy(alpha = 0.12f)

@Composable
fun UpdateBanner(
    version: String,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(PositiveDim)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = Positive,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nueva versión disponible — v$version",
                style = MaterialTheme.typography.labelLarge,
                color = Positive,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Descarga la actualización desde GitHub",
                style = MaterialTheme.typography.bodySmall,
                color = Positive.copy(alpha = 0.75f),
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onOpenRelease,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("Descargar", style = MaterialTheme.typography.labelSmall, color = Positive)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = Positive,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
