package com.aracem.joyufy.ui.dashboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide shared chart range preference.
 * Any screen that changes the range updates it for all charts simultaneously.
 */
object ChartRangePreference {
    private val _range = MutableStateFlow(ChartRange.ONE_YEAR)
    val range: StateFlow<ChartRange> = _range.asStateFlow()

    fun set(range: ChartRange) {
        _range.value = range
    }
}
