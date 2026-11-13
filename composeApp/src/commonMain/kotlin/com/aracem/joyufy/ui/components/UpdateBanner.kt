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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.ui.theme.joyufyColors
import com.aracem.joyufy.ui.strings.LocalStrings


@Composable
fun UpdateBanner(
    version: String,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.joyufyColors.positiveContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.joyufyColors.positive,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).widthIn(min = 0.dp)) {
            Text(
                text = "${strings.newVersion} — v$version",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.joyufyColors.positive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = strings.updateAvailable,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.positive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onOpenRelease,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(strings.download, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.joyufyColors.positive)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = strings.close,
                tint = MaterialTheme.joyufyColors.positive,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
