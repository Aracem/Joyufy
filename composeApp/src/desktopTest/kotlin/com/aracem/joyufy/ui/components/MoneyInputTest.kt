package com.aracem.joyufy.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyInputTest {
    @Test
    fun parsesFiniteDecimalAmounts() {
        assertEquals(42.75, parseMoneyInput("42,75"))
        assertEquals(0.0, parseMoneyInput("0"))
        assertEquals(-7.25, parseMoneyInput("-7.25"))
    }

    @Test
    fun rejectsInvalidAndNonFiniteAmounts() {
        listOf("", "abc", "NaN", "Infinity", "-Infinity", "1e9999").forEach {
            assertNull(parseMoneyInput(it), "Expected invalid money input: $it")
        }
    }
}
