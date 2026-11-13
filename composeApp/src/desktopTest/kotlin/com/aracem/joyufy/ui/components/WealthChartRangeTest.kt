package com.aracem.joyufy.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WealthChartRangeTest {
    @Test
    fun negativeOnlyRangeKeepsZeroAboveAllBalances() {
        val (min, max) = smartYRange(-25.50, -25.50)

        assertTrue(min < -25.50)
        assertEquals(0.0, max)
        assertTrue(min < max)
    }

    @Test
    fun negativeHistoryHasAscendingBoundsAndHeadroom() {
        val (min, max) = smartYRange(-120.0, -15.0)

        assertTrue(min < -120.0)
        assertEquals(0.0, max)
        assertTrue(min < max)
    }

    @Test
    fun mixedAndPositiveHistoriesKeepTheirDataInsideTheRange() {
        val (mixedMin, mixedMax) = smartYRange(-40.0, 60.0)
        val (positiveMin, positiveMax) = smartYRange(100.0, 105.0)

        assertTrue(mixedMin <= -40.0 && mixedMax >= 60.0)
        assertTrue(positiveMin <= 100.0 && positiveMax >= 105.0)
        assertTrue(mixedMin < mixedMax)
        assertTrue(positiveMin < positiveMax)
    }

    @Test
    fun compactAxisLabelsPreserveNegativeMagnitude() {
        assertEquals("-25", (-25.0).formatAmountCompact())
        assertTrue((-1_250.0).formatAmountCompact().startsWith("-1"))
        assertTrue((-1_500_000.0).formatAmountCompact().startsWith("-1"))
        assertTrue((-1_500_000.0).formatAmountCompact().endsWith("M"))
    }

    @Test
    fun axisLabelsKeepSmallDifferencesVisibleAtLargeBalances() {
        val labels = (0..4).map { step -> (1_000.0 + step * 62.5).formatAxisAmount(62.5) }

        assertEquals(5, labels.distinct().size)
    }

    @Test
    fun axisLabelsStayDistinctForASinglePositiveSnapshot() {
        val (min, max) = smartYRange(1_250.50, 1_250.50)
        val tickInterval = (max - min) / 4
        val labels = (0..4).map { step -> (min + step * tickInterval).formatAxisAmount(tickInterval) }

        assertEquals(5, labels.distinct().size)
    }
}
