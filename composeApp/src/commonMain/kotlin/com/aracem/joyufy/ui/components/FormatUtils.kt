package com.aracem.joyufy.ui.components

fun Double.formatCurrency(): String {
    val abs = kotlin.math.abs(this)
    val sign = if (this < 0) "-" else ""
    val formatted = buildString {
        val intPart = abs.toLong()
        val decPart = ((abs - intPart) * 100).toLong()
        val intStr = intPart.toString()
        // Thousands separator
        intStr.reversed().chunked(3).joinTo(this, ".").reversed().also {
            clear()
            append(it)
        }
        append(",")
        append(decPart.toString().padStart(2, '0'))
    }
    return "${sign}${formatted} €"
}
