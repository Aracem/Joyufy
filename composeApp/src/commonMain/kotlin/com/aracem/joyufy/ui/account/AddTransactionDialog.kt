package com.aracem.joyufy.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.TransactionCategory
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.formatDate
import com.aracem.joyufy.ui.components.formatInputAmount
import com.aracem.joyufy.ui.components.parseDateInputToMillis
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    accountType: AccountType,
    availableAccounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (type: TransactionType, amount: Double, category: String?, description: String?, relatedAccountId: Long?, date: Long) -> Unit,
    // Custom categories already used by the user in past transactions. Merged
    // with the predefined enum labels in the autocomplete dropdown so a manually
    // typed category becomes a normal suggestion in subsequent dialogs.
    customCategories: List<String> = emptyList(),
    // If non-null, dialog is in edit mode pre-populated with this transaction
    editingTransaction: com.aracem.joyufy.domain.model.Transaction? = null,
) {
    val strings = LocalStrings.current
    val allowedTypes = when (accountType) {
        AccountType.INVESTMENT -> listOf(TransactionType.INCOME, TransactionType.TRANSFER)
        AccountType.BANK, AccountType.CASH -> listOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER)
    }
    val initialType = if (editingTransaction?.relatedAccountId != null) {
        TransactionType.TRANSFER
    } else {
        editingTransaction?.type ?: allowedTypes.first()
    }
    var selectedType by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf(editingTransaction?.amount?.formatInputAmount() ?: "") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf(editingTransaction?.category ?: "") }
    var description by remember { mutableStateOf(editingTransaction?.description ?: "") }
    var selectedRelatedAccount by remember {
        mutableStateOf(editingTransaction?.relatedAccountId?.let { id -> availableAccounts.find { it.id == id } })
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var relatedExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Date field — default today or editing transaction date
    val todayLocal = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val todayFormatted = "%02d/%02d/%04d".format(
        todayLocal.dayOfMonth, todayLocal.monthNumber, todayLocal.year
    )
    val initialDate = editingTransaction?.date?.let { ms ->
        val local = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        "%02d/%02d/%04d".format(local.dayOfMonth, local.monthNumber, local.year)
    } ?: todayFormatted
    var dateText by remember { mutableStateOf(initialDate) }
    var dateError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.width(440.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (editingTransaction != null) strings.editTransaction else strings.newTransaction,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = strings.close,
                            tint = MaterialTheme.joyufyColors.contentSecondary)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Tipo
                Text(strings.transactionType, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.joyufyColors.contentSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedTypes.forEach { type ->
                        val selected = selectedType == type
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedType = type
                                if (type != TransactionType.TRANSFER) selectedRelatedAccount = null
                            },
                            label = { Text(when (type) {
                                TransactionType.INCOME -> strings.transactionIncome
                                TransactionType.EXPENSE -> strings.transactionExpense
                                TransactionType.TRANSFER -> strings.transactionTransfer
                            }, style = MaterialTheme.typography.labelSmall) },
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

                Spacer(Modifier.height(16.dp))

                // Focus management: Enter / Tab on a field jumps to the next.
                val focusManager = LocalFocusManager.current
                val amountFocus = remember { FocusRequester() }
                val dateFocus = remember { FocusRequester() }
                val descriptionFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { amountFocus.requestFocus() }

                // All available categories: predefined enum labels + any free-text
                // values the user has used before (case-insensitive dedup, custom
                // strings get priority casing).
                val allCategoryLabels = remember(customCategories) {
                    val preset = TransactionCategory.entries.map { it.label }
                    val seen = HashSet<String>()
                    val out = ArrayList<String>(preset.size + customCategories.size)
                    customCategories.forEach { if (seen.add(it.lowercase())) out += it }
                    preset.forEach { if (seen.add(it.lowercase())) out += it }
                    out
                }

                val destinationAccounts = if (selectedType == TransactionType.TRANSFER) {
                    when (accountType) {
                        AccountType.INVESTMENT -> availableAccounts.filter {
                            it.type == AccountType.BANK || it.type == AccountType.CASH
                        }
                        AccountType.BANK, AccountType.CASH -> availableAccounts
                    }
                } else emptyList()

                LaunchedEffect(selectedType, destinationAccounts) {
                    if (selectedType == TransactionType.TRANSFER &&
                        selectedRelatedAccount?.id !in destinationAccounts.map { it.id }
                    ) {
                        selectedRelatedAccount = null
                    }
                }

                // Importe
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = { Text(strings.amountEur) },
                    placeholder = { Text(strings.placeholderAmount) },
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, focusedLabelColor = Accent),
                )

                Spacer(Modifier.height(12.dp))

                // Fecha
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; dateError = null },
                    label = { Text(strings.dateFormat) },
                    placeholder = { Text(todayFormatted) },
                    isError = dateError != null,
                    supportingText = dateError?.let { { Text(it) } },
                    trailingIcon = {
                        IconButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = strings.selectDate,
                                tint = MaterialTheme.joyufyColors.contentSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(dateFocus),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, focusedLabelColor = Accent),
                )

                Spacer(Modifier.height(12.dp))

                // Categoría — autocomplete con filtrado y soporte Enter.
                //
                // The dropdown is opened *only by user typing* (onValueChange) or
                // by clicking the trailing icon. When the user picks an item, we
                // set the text *and* lock the dropdown shut, otherwise the new
                // text matches a single suggestion and the menu pops back open
                // showing only that one item — making the user feel they have to
                // click twice.
                val categorySuggestions = remember(category, allCategoryLabels) {
                    val q = category.trim().lowercase()
                    if (q.isEmpty()) allCategoryLabels
                    else allCategoryLabels.filter { it.lowercase().contains(q) }
                }

                fun acceptFirstSuggestionOrFocusNext() {
                    val first = categorySuggestions.firstOrNull()
                    if (first != null && !category.equals(first, ignoreCase = true)) {
                        category = first
                    }
                    categoryExpanded = false
                    focusManager.moveFocus(FocusDirection.Down)
                }

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { /* manually controlled — see onClick below */ },
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {
                            category = it
                            // Reopen on typing so suggestions follow the input.
                            categoryExpanded = it.isNotBlank()
                        },
                        label = { Text(strings.categoryOptional) },
                        trailingIcon = {
                            if (category.isNotBlank()) {
                                IconButton(
                                    onClick = { category = ""; categoryExpanded = false },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = strings.clearFilters,
                                        tint = MaterialTheme.joyufyColors.contentSecondary,
                                        modifier = Modifier.size(16.dp))
                                }
                            } else {
                                // Tap arrow to open / close all suggestions.
                                IconButton(
                                    onClick = { categoryExpanded = !categoryExpanded },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { acceptFirstSuggestionOrFocusNext() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                    acceptFirstSuggestionOrFocusNext()
                                    true
                                } else false
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, focusedLabelColor = Accent),
                    )
                    if (categorySuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                        ) {
                            categorySuggestions.forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        category = label
                                        categoryExpanded = false
                                        focusManager.moveFocus(FocusDirection.Down)
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Descripción
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(strings.descriptionOptional) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocus),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, focusedLabelColor = Accent),
                )

                // Cuenta destino (solo para transferencias)
                if (selectedType == TransactionType.TRANSFER && destinationAccounts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = relatedExpanded,
                        onExpandedChange = { relatedExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedRelatedAccount?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(strings.destinationAccount) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(relatedExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, focusedLabelColor = Accent),
                        )
                        ExposedDropdownMenu(
                            expanded = relatedExpanded,
                            onDismissRequest = { relatedExpanded = false },
                        ) {
                            destinationAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = { selectedRelatedAccount = account; relatedExpanded = false },
                                )
                            }
                        }
                    }
                } else if (selectedType == TransactionType.TRANSFER) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = strings.noDestinationAccounts,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }

                if (selectedType == TransactionType.TRANSFER && selectedRelatedAccount == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.selectDestinationAccount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.joyufyColors.contentSecondary,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Actions
                val canSubmit = selectedType != TransactionType.TRANSFER || selectedRelatedAccount != null
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel, color = MaterialTheme.joyufyColors.contentSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.replace(",", ".").toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                amountError = strings.amountError
                                return@Button
                            }
                            val dateMs = parseDateInputToMillis(dateText)
                            if (dateMs == null) {
                                dateError = strings.dateError
                                return@Button
                            }
                            onConfirm(
                                selectedType,
                                amount,
                                category.ifBlank { null },
                                description.ifBlank { null },
                                selectedRelatedAccount?.id,
                                dateMs,
                            )
                            onDismiss()
                        },
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) {
                        Text(when {
                            editingTransaction != null -> strings.save
                            selectedType == TransactionType.TRANSFER -> strings.buttonTransfer
                            else -> strings.buttonAdd
                        })
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseDateInputToMillis(dateText),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            dateText = selected.formatDate()
                            dateError = null
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(strings.cancel)
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
