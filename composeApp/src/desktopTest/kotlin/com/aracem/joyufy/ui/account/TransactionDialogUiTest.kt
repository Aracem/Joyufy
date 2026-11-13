package com.aracem.joyufy.ui.account

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.CategorizationRule
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.parseDateInputToMillis
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.StringsEn
import com.aracem.joyufy.ui.theme.JoyufyTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class TransactionDialogUiTest {
    @Test
    fun accountSpecificRuleAndTypedDateAreSubmitted() {
        var submitted: List<Any?>? = null
        val scene = ImageComposeScene(width = 560, height = 1000) {
            JoyufyTheme(darkMode = false) {
                CompositionLocalProvider(LocalStrings provides StringsEn) {
                    AddTransactionDialog(
                        accountType = AccountType.BANK,
                        accountId = 12,
                        accountName = "Checking",
                        availableAccounts = emptyList(),
                        onDismiss = {},
                        onConfirm = { type, amount, category, description, relatedId, date ->
                            submitted = listOf(type, amount, category, description, relatedId, date)
                        },
                        rulesProvider = { listOf(CategorizationRule(1, "bakery", "Food", 12)) },
                    )
                }
            }
        }
        try {
            scene.render()
            fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun nodeWithText(text: String) = nodes().first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            fun type(label: String, value: String) {
                val node = nodeWithText(label)
                assertTrue(node.config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString(value)))
                scene.render()
            }
            assertTrue(nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Account: Checking" } })
            type(StringsEn.amountEur, "12,50")
            type(StringsEn.dateFormat, "14/02/2026")
            val pickerButton = nodes().first { node ->
                StringsEn.selectDate in node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            assertTrue(pickerButton.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(nodeWithText(StringsEn.save).config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            type(StringsEn.descriptionOptional, "Bakery")
            assertTrue(nodeWithText(StringsEn.buttonAdd).config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            assertEquals(TransactionType.EXPENSE, submitted?.get(0))
            assertEquals(12.5, submitted?.get(1))
            assertEquals("Food", submitted?.get(2))
            assertEquals("Bakery", submitted?.get(3))
            assertEquals(parseDateInputToMillis("14/02/2026"), submitted?.get(5))
        } finally {
            scene.close()
        }
    }
    @Test
    fun transferCannotSelectTheSourceAccountAsDestination() {
        var submissions = 0
        val source = Account(12, "Checking", AccountType.BANK, Color.Blue, position = 0, createdAt = 0)
        val scene = ImageComposeScene(width = 560, height = 1000) {
            JoyufyTheme {
                CompositionLocalProvider(LocalStrings provides StringsEn) {
                    AddTransactionDialog(
                        accountType = AccountType.BANK, accountId = source.id, accountName = source.name,
                        availableAccounts = listOf(source), onDismiss = {},
                        onConfirm = { _, _, _, _, _, _ -> submissions++ }, rulesProvider = { emptyList() },
                    )
                }
            }
        }
        try {
            scene.render()
            fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun hasText(node: androidx.compose.ui.semantics.SemanticsNode, text: String) =
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            val transferType = nodes().first { hasText(it, StringsEn.transactionTransfer) }
            assertTrue(transferType.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(nodes().any { hasText(it, StringsEn.noDestinationAccounts) })
            val transferControls = nodes().filter { hasText(it, StringsEn.buttonTransfer) }
            assertTrue(transferControls.any { it.config.contains(SemanticsProperties.Disabled) })
            assertEquals(0, submissions)
        } finally {
            scene.close()
        }
    }

    @Test
    fun transferRequiresAndSubmitsASeparateDestination() {
        var submittedType: TransactionType? = null
        var submittedDestination: Long? = null
        val source = Account(12, "Checking", AccountType.BANK, Color.Blue, position = 0, createdAt = 0)
        val target = Account(13, "Savings", AccountType.BANK, Color.Red, position = 1, createdAt = 0)
        val scene = ImageComposeScene(width = 560, height = 1000) {
            JoyufyTheme {
                CompositionLocalProvider(LocalStrings provides StringsEn) {
                    AddTransactionDialog(
                        accountType = AccountType.BANK, accountId = source.id, accountName = source.name,
                        availableAccounts = listOf(source, target), onDismiss = {},
                        onConfirm = { type, _, _, _, relatedId, _ -> submittedType = type; submittedDestination = relatedId },
                        rulesProvider = { emptyList() },
                    )
                }
            }
        }
        try {
            scene.render()
            fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun named(text: String) = nodes().filter { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            assertTrue(named(StringsEn.transactionTransfer).first().config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(named(StringsEn.buttonTransfer).any { it.config.contains(SemanticsProperties.Disabled) })
            assertTrue(named(StringsEn.amountEur).first().config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString("15")))
            scene.render()
            assertTrue(named(StringsEn.destinationAccount).first().config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(named("Savings").first().config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            val submit = named(StringsEn.buttonTransfer).last()
            assertTrue(!submit.config.contains(SemanticsProperties.Disabled))
            assertTrue(submit.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            assertEquals(TransactionType.TRANSFER, submittedType)
            assertEquals(13L, submittedDestination)
        } finally {
            scene.close()
        }
    }

    @Test
    fun categorySuggestionsAndCustomTextAreAvailable() {
        var savedCategory: String? = null
        val scene = ImageComposeScene(width = 560, height = 1000) {
            JoyufyTheme {
                CompositionLocalProvider(LocalStrings provides StringsEn) {
                    AddTransactionDialog(
                        accountType = AccountType.BANK, accountId = 12, accountName = "Checking",
                        availableAccounts = emptyList(), onDismiss = {},
                        onConfirm = { _, _, category, _, _, _ -> savedCategory = category },
                        rulesProvider = { emptyList() },
                    )
                }
            }
        }
        try {
            scene.render()
            fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun named(text: String) = nodes().first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            val suggestions = nodes().first { node ->
                StringsEn.showCategorySuggestions in node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            assertTrue(suggestions.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(named(StringsEn.categoryGroceries).config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(named(StringsEn.categoryOptional).config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString("Bicycle")))
            scene.render()
            assertTrue(named(StringsEn.amountEur).config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString("5")))
            scene.render()
            assertTrue(named(StringsEn.buttonAdd).config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            assertEquals("Bicycle", savedCategory)
        } finally {
            scene.close()
        }
    }

    @Test
    fun localizedBuiltInCategoryKeepsItsStoredValue() {
        var savedCategory: String? = null
        val scene = ImageComposeScene(width = 560, height = 1000) {
            JoyufyTheme {
                CompositionLocalProvider(LocalStrings provides StringsEn) {
                    AddTransactionDialog(
                        accountType = AccountType.BANK, accountId = 12, accountName = "Checking",
                        availableAccounts = emptyList(), onDismiss = {},
                        onConfirm = { _, _, category, _, _, _ -> savedCategory = category },
                        rulesProvider = { emptyList() },
                    )
                }
            }
        }
        try {
            scene.render()
            fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun named(text: String) = nodes().first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            val suggestions = nodes().first { node ->
                StringsEn.showCategorySuggestions in node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            }
            assertTrue(suggestions.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(named(StringsEn.categoryGroceries).config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(named(StringsEn.amountEur).config.getOrNull(SemanticsActions.SetText)!!.action!!.invoke(AnnotatedString("5")))
            scene.render()
            assertTrue(named(StringsEn.buttonAdd).config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            assertEquals("Supermercado", savedCategory)
        } finally {
            scene.close()
        }
    }

}
