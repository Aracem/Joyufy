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
import com.aracem.joyufy.ui.dashboard.MissingSnapshotTask
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.AccentDim

@Composable
fun MissingSnapshotBanner(
    tasks: List<MissingSnapshotTask>,
    onUpdateValue: (MissingSnapshotTask) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return
    val strings = LocalStrings.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(AccentDim)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.missingSnapshotTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent,
                )
                Text(
                    text = strings.missingSnapshotSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Accent.copy(alpha = 0.75f),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.close,
                    tint = Accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        tasks.forEach { task ->
            MissingSnapshotTaskRow(
                task = task,
                onUpdateValue = { onUpdateValue(task) },
            )
        }
    }
}

@Composable
private fun MissingSnapshotTaskRow(
    task: MissingSnapshotTask,
    onUpdateValue: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(task.account.color),
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.account.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = strings.missingSnapshotLastValue.format(
                    task.lastSnapshotDate?.formatWeekRange(strings.week) ?: strings.missingSnapshotNever,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Accent.copy(alpha = 0.74f),
            )
        }
        Spacer(Modifier.width(10.dp))
        TextButton(
            onClick = onUpdateValue,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = strings.updateValue,
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
        }
    }
}
