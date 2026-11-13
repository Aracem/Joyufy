package com.aracem.joyufy.ui.importer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.StringsEn
import com.aracem.joyufy.ui.strings.StringsEs
import com.aracem.joyufy.ui.theme.JoyufyTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class CsvImportDialogUiTest {
    @Test
    fun narrowEnglishPreviewShowsCountsAndBlocksCommitWithoutValidRows() {
        var dismissed = false
        var committed = false
        val state = previewState(
            CsvImportDraftRow(
                id = 1,
                accountId = 7,
                dateText = "invalid",
                amountText = "12.00",
                description = "Invalid row",
                errors = listOf("Invalid date"),
            ),
            CsvImportDraftRow(
                id = 2,
                accountId = 7,
                dateText = "10/11/2026",
                amountText = "8.50",
                description = "Duplicate row",
                duplicateHint = true,
            ),
        )
        val scene = scene(
            width = 420,
            height = 700,
            darkMode = true,
            strings = StringsEn,
            state = state,
            onDismiss = { dismissed = true }, onCommit = { committed = true })
        try {
            scene.render()
            val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun hasText(text: String) = nodes.any { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            assertTrue(hasText(StringsEn.csvImportTitle))
            assertTrue(hasText(StringsEn.csvImportRows.format(2)))
            assertTrue(hasText(StringsEn.csvImportErrors.format(1)))
            assertTrue(hasText(StringsEn.csvImportDuplicates.format(1)))

            val import = nodes.first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == StringsEn.csvImportCommit.format(0) }
            }
            assertTrue(import.config.contains(SemanticsProperties.Disabled))
            val cancel = nodes.first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == StringsEn.cancel }
            }
            assertTrue(cancel.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(dismissed)
            assertTrue(!committed)
        } finally {
            scene.close()
        }
    }

    @Test
    fun narrowSpanishPreviewEnablesCommitForValidRows() {
        var committed = false
        val state = previewState(
            CsvImportDraftRow(
                id = 1,
                accountId = 7,
                dateText = "10/11/2026",
                amountText = "8.50",
                description = "Valid row",
            ),
        )
        val scene = scene(
            width = 420,
            height = 700,
            darkMode = false,
            strings = StringsEs,
            state = state,
            onDismiss = {}, onCommit = { committed = true })
        try {
            scene.render()
            val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun hasText(text: String) = nodes.any { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            assertTrue(hasText(StringsEs.csvImportTitle))
            assertTrue(hasText(StringsEs.csvImportRows.format(1)))
            assertTrue(hasText(StringsEs.csvImportValid.format(1)))
            val import = nodes.first { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == StringsEs.csvImportCommit.format(1) }
            }
            assertTrue(!import.config.contains(SemanticsProperties.Disabled))
            assertTrue(import.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            scene.render()
            assertTrue(committed)
        } finally {
            scene.close()
        }
    }

    private fun previewState(vararg rows: CsvImportDraftRow) = CsvImportUiState(
        isOpen = true,
        accounts = listOf(Account(7, "Checking", AccountType.BANK, Color.Blue, position = 0, createdAt = 0)),
        headers = listOf("Date", "Amount", "Description"),
        mapping = mapOf(
            CsvColumnRole.DATE to "Date",
            CsvColumnRole.AMOUNT to "Amount",
            CsvColumnRole.DESCRIPTION to "Description",
        ),
        defaultAccountId = 7,
        rows = rows.toList(),
    )

    private fun scene(
        width: Int,
        height: Int,
        darkMode: Boolean,
        strings: com.aracem.joyufy.ui.strings.Strings,
        state: CsvImportUiState,
        onDismiss: () -> Unit,
        onCommit: () -> Unit,
    ) = ImageComposeScene(width = width, height = height) {
        JoyufyTheme(darkMode = darkMode) {
            CompositionLocalProvider(LocalStrings provides strings) {
                CsvImportDialog(
                    state = state,
                    onDismiss = onDismiss,
                    onCommit = onCommit,
                    onDefaultAccount = {},
                    onMapping = { _, _ -> },
                    onToggleRow = {},
                    onRowAccount = { _, _ -> },
                    onRowField = { _, _, _ -> },
                )
            }
        }
    }
}
