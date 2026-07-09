package com.aracem.joyufy.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aracem.joyufy.data.mapper.toComposeColor
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.BankPreset
import com.aracem.joyufy.domain.model.BankPresets
import com.aracem.joyufy.ui.components.AccountLogo
import com.aracem.joyufy.ui.components.AccountLogoInitials
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.theme.AccountPalette
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateAccountDialog(
    existingCount: Int,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    editingAccount: Account? = null,
    initialType: AccountType? = null,
    viewModel: CreateAccountViewModel = koinInject(),
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingTypeChange by remember { mutableStateOf<TypeChangePlan?>(null) }

    // Reset / pre-populate on first composition
    LaunchedEffect(editingAccount, initialType) {
        if (editingAccount != null) viewModel.resetForEdit(editingAccount)
        else {
            viewModel.reset()
            initialType?.let(viewModel::onTypeChange)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // ── Header ────────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (editingAccount != null) strings.editAccount else strings.newAccount,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = MaterialTheme.joyufyColors.contentSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Scrollable body ───────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {

                // ── Presets ───────────────────────────────────────────────
                Text(
                    text = strings.bankOrPlatform,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val grouped = BankPresets
                        .sortedBy { it.name.lowercase() }
                        .groupBy { it.type }
                    val order = listOf(AccountType.BANK, AccountType.INVESTMENT, AccountType.CASH)
                    order.forEach { type ->
                        val group = grouped[type] ?: return@forEach
                        Text(
                            text = when (type) {
                            AccountType.BANK -> strings.accountTypeBank
                            AccountType.INVESTMENT -> strings.accountTypeInvestment
                            AccountType.CASH -> strings.accountTypeCash
                        },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.joyufyColors.contentSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            group.forEach { preset ->
                                BankPresetChip(
                                    preset = preset,
                                    selected = state.logoUrl == preset.logoRes,
                                    onClick = { viewModel.onPresetSelected(preset) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Nombre ────────────────────────────────────────────────
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(strings.name) },
                    placeholder = { Text(strings.placeholderName) },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        focusedLabelColor = Accent,
                    ),
                )

                Spacer(Modifier.height(16.dp))

                // ── Tipo ──────────────────────────────────────────────────
                Text(
                    text = strings.typeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { type ->
                        val selected = state.type == type
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onTypeChange(type) },
                            label = { Text(when (type) {
                                AccountType.BANK -> strings.accountTypeBank
                                AccountType.INVESTMENT -> strings.accountTypeInvestment
                                AccountType.CASH -> strings.accountTypeCash
                            }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent.copy(alpha = 0.15f),
                                selectedLabelColor = Accent,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                selectedBorderColor = Accent,
                                borderColor = MaterialTheme.joyufyColors.border,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Color ─────────────────────────────────────────────────
                Text(
                    text = strings.color,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
                Spacer(Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp),
                ) {
                    items(AccountPalette) { color ->
                        ColorSwatch(
                            color = color,
                            selected = state.selectedColor == color,
                            onClick = { viewModel.onColorChange(color) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Custom hex input
                var hexInput by remember { mutableStateOf("") }
                var hexError by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { value ->
                        hexInput = value
                        val clean = value.removePrefix("#")
                        hexError = clean.isNotEmpty() && clean.length != 6
                        if (clean.length == 6) {
                            runCatching { "#$clean".toComposeColor() }
                                .onSuccess {
                                    viewModel.onColorChange(it)
                                    hexError = false
                                }
                                .onFailure { hexError = true }
                        }
                    },
                    label = { Text(strings.customColor) },
                    placeholder = { Text(strings.placeholderColor) },
                    isError = hexError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(state.selectedColor)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        focusedLabelColor = Accent,
                    ),
                )

                // ── Saldo inicial (solo para BANK/CASH, no en edición) ────
                if (editingAccount == null && state.type != AccountType.INVESTMENT) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.initialBalance,
                        onValueChange = viewModel::onInitialBalanceChange,
                        label = { Text(strings.initialBalanceOptional) },
                        placeholder = { Text(strings.placeholderAmount) },
                        isError = state.initialBalanceError != null,
                        supportingText = state.initialBalanceError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            focusedLabelColor = Accent,
                        ),
                    )
                }

                Spacer(Modifier.height(24.dp))

                } // end scrollable body

                // ── Actions ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel, color = MaterialTheme.joyufyColors.contentSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (editingAccount != null) {
                                // Cross-family type changes are destructive. Ask the VM
                                // for a plan; if it's non-null, route through the
                                // confirmation dialog instead of saving directly.
                                if (state.type != editingAccount.type) {
                                    scope.launch {
                                        val plan = viewModel.planTypeChange(editingAccount, state.type)
                                        if (plan != null) {
                                            pendingTypeChange = plan
                                        } else {
                                            viewModel.saveEdit(
                                                account = editingAccount,
                                                onSuccess = { onCreated(); onDismiss() },
                                            )
                                        }
                                    }
                                } else {
                                    viewModel.saveEdit(
                                        account = editingAccount,
                                        onSuccess = { onCreated(); onDismiss() },
                                    )
                                }
                            } else {
                                viewModel.save(
                                    existingCount = existingCount,
                                    onSuccess = { onCreated(); onDismiss() },
                                )
                            }
                        },
                        enabled = !state.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(if (editingAccount != null) strings.saveChanges else strings.createAccount)
                        }
                    }
                }
            }
        }
    }

    pendingTypeChange?.let { plan ->
        val specific = when {
            plan.to == AccountType.INVESTMENT ->
                strings.confirmTypeChangeBankToInvestment.format(plan.transactionsToDelete)
            plan.from == AccountType.INVESTMENT ->
                strings.confirmTypeChangeInvestmentToBank.format(plan.snapshotsToDelete)
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { pendingTypeChange = null },
            title = { Text(strings.confirmTypeChangeTitle) },
            text = {
                Column {
                    Text(specific)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        strings.confirmTypeChangeBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingTypeChange = null
                        viewModel.saveEdit(
                            account = editingAccount!!,
                            onSuccess = { onCreated(); onDismiss() },
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Negative),
                ) { Text(strings.confirmTypeChangeContinue) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTypeChange = null }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun BankPresetChip(
    preset: BankPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) Accent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
                if (selected) Modifier.border(1.dp, Accent, MaterialTheme.shapes.small)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val color = runCatching { preset.defaultColor.toComposeColor() }.getOrElse { Color.Gray }
        if (preset.logoRes != null) {
            AccountLogo(logoUrl = preset.logoRes, size = 32.dp)
        } else {
            AccountLogoInitials(color = color, name = preset.name, size = 32.dp)
        }
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Accent else MaterialTheme.joyufyColors.contentSecondary,
        )
    }
}
