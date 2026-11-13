package com.aracem.joyufy.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.aracem.joyufy.data.repository.BackupDiff
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.StringsEs
import com.aracem.joyufy.ui.theme.JoyufyTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class LocalImportPreviewDialogUiTest {
    @Test
    fun previewShowsEntityChangesAndOnlyInvokesRestoreAfterConfirmation() {
        var dismissCount = 0
        var restoreCount = 0
        val diff = BackupDiff(
            cloudExportedAt = 1_794_568_000_000L,
            accountsAdded = 1,
            accountsRemoved = 0,
            accountsModified = 0,
            transactionsAdded = 0,
            transactionsRemoved = 2,
            transactionsModified = 1,
            snapshotsAdded = 1,
            snapshotsRemoved = 0,
            snapshotsModified = 0,
        )
        val scene = ImageComposeScene(width = 480, height = 720) {
            JoyufyTheme(darkMode = false) {
                CompositionLocalProvider(LocalStrings provides StringsEs) {
                    LocalImportPreviewDialog(
                        diff = diff,
                        s = StringsEs,
                        onDismiss = { dismissCount++ },
                        onRestore = { restoreCount++ },
                    )
                }
            }
        }
        try {
            scene.render()
            fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            fun hasText(text: String) = nodes().any { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
            }
            fun click(text: String) {
                val node = nodes().first { item ->
                    item.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
                }
                assertTrue(node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
                scene.render()
            }

            assertTrue(hasText(StringsEs.importPreviewTitle))
            assertTrue(hasText(StringsEs.importPreviewSubtitle))
            assertTrue(hasText(StringsEs.localDataCurrent))
            assertTrue(hasText(StringsEs.cloudDiffAccounts))
            assertTrue(hasText(StringsEs.cloudDiffTransactions))
            assertTrue(hasText(StringsEs.cloudDiffSnapshots))
            assertTrue(hasText("+1"))
            assertTrue(hasText("-2"))
            assertTrue(hasText("~1"))
            assertEquals(0, restoreCount)

            click(StringsEs.cancel)
            assertEquals(1, dismissCount)
            assertEquals(0, restoreCount)
            click(StringsEs.importPreviewRestore)
            assertEquals(1, restoreCount)
        } finally {
            scene.close()
        }
    }
}
