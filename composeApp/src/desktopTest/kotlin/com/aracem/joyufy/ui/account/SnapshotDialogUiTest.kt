package com.aracem.joyufy.ui.account

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.aracem.joyufy.domain.logic.InvestmentSnapshotFlows
import com.aracem.joyufy.ui.components.currentWeekStartMillis
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.StringsEn
import com.aracem.joyufy.ui.theme.JoyufyTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class SnapshotDialogUiTest {
    @Test
    fun inferredFlowsAreVisibleAndSurviveCollapsing() {
        var savedDeposit: Double? = null
        val scene = ImageComposeScene(width = 600, height = 800) {
            JoyufyTheme {
                CompositionLocalProvider(LocalStrings provides StringsEn) {
                    AddSnapshotDialog(
                        accountName = "Investment",
                        currentValue = 100.0,
                        flowDefaultsByWeek = mapOf(currentWeekStartMillis() to InvestmentSnapshotFlows(deposits = 25.0)),
                        onDismiss = {},
                        onConfirm = { _, _, deposits, _, _, _, _ -> savedDeposit = deposits },
                    )
                }
            }
        }
        try {
            scene.render()
            scene.render()
            fun click(label: String) {
                val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                val node = nodes.first { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label } }
                assertTrue(node.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
                scene.render()
            }
            click(StringsEn.hideSnapshotDetails)
            click(StringsEn.save)
            assertEquals(25.0, savedDeposit)
        } finally {
            scene.close()
        }
    }
}
