package com.aracem.joyufy.ui.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.aracem.joyufy.ui.strings.LocalStrings
import com.aracem.joyufy.ui.strings.StringsEn
import com.aracem.joyufy.ui.theme.JoyufyTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class MonthlyCardUiTest {
    @Test
    fun emptyMonthShowsZerosAndOpensDetailInBothThemes() {
        for (dark in listOf(false, true)) {
            var openCount = 0
            val scene = ImageComposeScene(width = 420, height = 200) {
                JoyufyTheme(darkMode = dark) {
                    CompositionLocalProvider(LocalStrings provides StringsEn) {
                        ThisMonthCard(summary = null, isLoading = false, hasError = false, onOpen = { openCount++ })
                    }
                }
            }
            try {
                scene.render()
                val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                assertEquals(3, texts.count { it == "0,00 €" })
                val click = nodes.firstNotNullOf { it.config.getOrNull(SemanticsActions.OnClick)?.action }
                assertTrue(click())
                assertEquals(1, openCount)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun loadingAndFailureDoNotShowFabricatedAmounts() {
        for ((loading, failed) in listOf(true to false, false to true)) {
            val scene = ImageComposeScene(width = 420, height = 200) {
                JoyufyTheme {
                    CompositionLocalProvider(LocalStrings provides StringsEn) {
                        ThisMonthCard(summary = null, isLoading = loading, hasError = failed, onOpen = {})
                    }
                }
            }
            try {
                scene.render()
                val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                assertFalse("0,00 €" in texts)
                assertEquals(failed, StringsEn.monthlyLoadError in texts)
                assertEquals(loading, nodes.any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null })
            } finally {
                scene.close()
            }
        }
    }
    @Test
    fun monthDetailOffersPreviousAndDisablesFutureNavigation() {
        var previousClicks = 0
        var nextClicks = 0
        val scene = ImageComposeScene(width = 480, height = 720) {
            JoyufyTheme {
                CompositionLocalProvider(LocalStrings provides StringsEn) {
                    MonthlyAnalysisDialog(
                        analysis = MonthlyAnalysis(
                            AnalysisMonth(2026, 1), income = 100.0, expenses = 30.0,
                            categories = listOf(CategoryComparison("Food", 30.0, 20.0)),
                            previousIncome = 80.0, previousExpenses = 20.0,
                            canGoPrevious = true, canGoNext = false, isCurrentMonth = true,
                        ),
                        hasError = false,
                        onDismiss = {},
                        onPreviousMonth = { previousClicks++ },
                        onNextMonth = { nextClicks++ },
                    )
                }
            }
        }
        try {
            scene.render()
            val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            val previous = nodes.first { StringsEn.previousMonth in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            val next = nodes.first { StringsEn.nextMonth in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            assertEquals(null, previous.config.getOrNull(SemanticsProperties.Disabled))
            assertTrue(next.config.contains(SemanticsProperties.Disabled))
            assertTrue(previous.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
            assertEquals(1, previousClicks)
            assertEquals(0, nextClicks)
        } finally {
            scene.close()
        }
    }

    @Test
    fun monthlyCardCanBeOpenedWithKeyboard() {
        var opens = 0
        val scene = ImageComposeScene(width = 420, height = 200) {
            JoyufyTheme {
                CompositionLocalProvider(LocalStrings provides StringsEn) {
                    ThisMonthCard(summary = null, isLoading = false, hasError = false, onOpen = { opens++ })
                }
            }
        }
        try {
            scene.render()
            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Tab, KeyEventType.KeyUp))
            scene.render()
            val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            assertTrue(nodes.any { it.config.getOrNull(SemanticsProperties.Focused) == true })
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
            scene.render()
            assertEquals(1, opens)
        } finally {
            scene.close()
        }
    }

}
