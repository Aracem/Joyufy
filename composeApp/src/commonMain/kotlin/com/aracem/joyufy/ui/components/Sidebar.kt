package com.aracem.joyufy.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Euro
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.ui.dashboard.AccountSummary
import com.aracem.joyufy.ui.navigation.Screen
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.joyufyColors

private val SIDEBAR_EXPANDED_WIDTH = 220.dp
private val SIDEBAR_COLLAPSED_WIDTH = 56.dp

@Composable
fun Sidebar(
    currentScreen: Screen,
    accounts: List<AccountSummary>,
    onScreenSelected: (Screen) -> Unit,
    onAddAccount: () -> Unit,
    onAccountClick: (Account) -> Unit,
    onReorderAccounts: (fromIndex: Int, toIndex: Int) -> Unit,
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    var reorderMode by remember { mutableStateOf(false) }
    val itemCenterY = remember { mutableStateMapOf<Long, Float>() }

    val sidebarWidth by animateDpAsState(
        targetValue = if (expanded) SIDEBAR_EXPANDED_WIDTH else SIDEBAR_COLLAPSED_WIDTH,
        animationSpec = tween(220),
    )

    Column(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Top: toggle expand/collapse ───────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    expanded = !expanded
                    if (!expanded) reorderMode = false
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = if (expanded) "Colapsar sidebar" else "Expandir sidebar",
                    tint = MaterialTheme.joyufyColors.contentSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Joyufy",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Dashboard nav item ────────────────────────────────────────────
        SidebarNavItem(
            label = "Dashboard",
            icon = Icons.Default.Home,
            selected = currentScreen is Screen.Dashboard,
            expanded = expanded,
            onClick = { onScreenSelected(Screen.Dashboard) },
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = if (expanded) 12.dp else 8.dp),
            color = MaterialTheme.joyufyColors.border,
        )
        Spacer(Modifier.height(8.dp))

        // ── Account list ──────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(accounts, key = { _, s -> s.account.id }) { index, summary ->
                SidebarAccountItem(
                    summary = summary,
                    expanded = expanded,
                    reorderMode = reorderMode,
                    index = index,
                    itemCenterY = itemCenterY,
                    isSelected = currentScreen is Screen.AccountDetail &&
                        currentScreen.accountId == summary.account.id,
                    onClick = { onAccountClick(summary.account) },
                    onReorder = onReorderAccounts,
                )
            }
        }

        // ── "Nueva cuenta" + reorder toggle ──────────────────────────────
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onAddAccount)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nueva cuenta",
                        tint = Accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Nueva cuenta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Accent,
                    )
                }
                if (accounts.isNotEmpty()) {
                    val reorderTint by animateFloatAsState(
                        targetValue = if (reorderMode) 1f else 0f,
                        animationSpec = tween(150),
                    )
                    IconButton(
                        onClick = { reorderMode = !reorderMode },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = if (reorderMode) "Salir del modo ordenar" else "Ordenar cuentas",
                            tint = androidx.compose.ui.graphics.lerp(
                                MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.5f),
                                Accent,
                                reorderTint,
                            ),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        } else {
            IconButton(
                onClick = onAddAccount,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nueva cuenta",
                    tint = Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ── Bottom: theme + settings ──────────────────────────────────────
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = if (expanded) 12.dp else 8.dp),
            color = MaterialTheme.joyufyColors.border,
        )
        Spacer(Modifier.height(8.dp))

        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onToggleTheme)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (darkMode) "☀" else "🌙",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (darkMode) "Modo claro" else "Modo oscuro",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
            }
        } else {
            IconButton(onClick = onToggleTheme, modifier = Modifier.size(40.dp)) {
                Text(
                    text = if (darkMode) "☀" else "🌙",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SidebarNavItem(
            label = "Ajustes",
            icon = Icons.Default.Settings,
            selected = currentScreen is Screen.Settings,
            expanded = expanded,
            onClick = { onScreenSelected(Screen.Settings) },
        )

        Spacer(Modifier.height(16.dp))
    }
}

// ── Account item in sidebar ────────────────────────────────────────────────

@Composable
private fun SidebarAccountItem(
    summary: AccountSummary,
    expanded: Boolean,
    reorderMode: Boolean,
    index: Int,
    itemCenterY: SnapshotStateMap<Long, Float>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    var cursorY by remember { mutableStateOf(0f) }
    val account = summary.account

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                when {
                    isDragging -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .then(if (!reorderMode) Modifier.clickable(onClick = onClick) else Modifier)
            .onGloballyPositioned { coords ->
                itemCenterY[account.id] = coords.positionInWindow().y + coords.size.height / 2f
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(account.color),
        )

        Spacer(Modifier.width(if (expanded) 8.dp else 4.dp))

        if (reorderMode && expanded) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Reordenar",
                tint = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(14.dp)
                    .pointerInput(account.id) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                cursorY = itemCenterY[account.id] ?: 0f
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                cursorY += dragAmount.y
                                val targetId = itemCenterY.minByOrNull { (_, cy) ->
                                    kotlin.math.abs(cy - cursorY)
                                }?.key ?: return@detectDragGestures
                                val sortedIds = itemCenterY.entries.sortedBy { it.value }.map { it.key }
                                val currentIndex = sortedIds.indexOf(account.id)
                                val targetIndex = sortedIds.indexOf(targetId)
                                if (targetIndex != -1 && currentIndex != -1 && targetIndex != currentIndex) {
                                    onReorder(currentIndex, targetIndex)
                                }
                            },
                        )
                    },
            )
            Spacer(Modifier.width(6.dp))
        }

        // Account logo — fondo con el color de la cuenta para que los logos sean visibles
        val logoSize = 34.dp
        when {
            account.logoUrl != null -> AccountLogo(
                logoUrl = account.logoUrl,
                size = logoSize,
                bgColor = account.color.copy(alpha = 0.25f),
            )
            account.type == AccountType.CASH -> SidebarCashIcon(color = account.color, size = logoSize)
            else -> AccountLogoInitials(color = account.color, name = account.name, size = logoSize)
        }

        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.joyufyColors.contentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBalance(summary.balance),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun SidebarCashIcon(color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Euro,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size((size.value * 0.55f).dp),
        )
    }
}

// ── Nav item ───────────────────────────────────────────────────────────────

@Composable
private fun SidebarNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Accent else MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.joyufyColors.contentSecondary,
            )
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Accent else MaterialTheme.joyufyColors.contentSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun formatBalance(balance: Double): String = formatDouble(kotlin.math.abs(balance))

private fun formatDouble(value: Double): String {
    val long = value.toLong()
    val dec = ((value - long) * 100).toLong()
    return "${formatThousands(long)},${dec.toString().padStart(2, '0')} €"
}

private fun formatThousands(value: Long): String {
    val str = value.toString()
    val result = StringBuilder()
    str.reversed().forEachIndexed { i, c ->
        if (i > 0 && i % 3 == 0) result.append('.')
        result.append(c)
    }
    return result.reverse().toString()
}
