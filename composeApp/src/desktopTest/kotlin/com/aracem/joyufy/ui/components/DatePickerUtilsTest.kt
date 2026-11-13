package com.aracem.joyufy.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatePickerUtilsTest {
    @Test
    fun pickerUsesUtcCalendarDayAndRoundTrips() {
        val utcMidnight = 1767225600000L // 1 January 2026 at midnight UTC
        assertEquals(utcMidnight, dateInputToPickerMillis("01/01/2026"))
        assertEquals("01/01/2026", pickerMillisToDateInput(utcMidnight))
        assertEquals("31/03/2026", pickerMillisToDateInput(dateInputToPickerMillis("31/03/2026")!!))
    }

    @Test
    fun invalidDatesCannotInitializePicker() {
        assertNull(dateInputToPickerMillis("31/02/2026"))
        assertNull(dateInputToPickerMillis("tomorrow"))
    }
}
