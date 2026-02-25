package com.aracem.joyufy.ui.components

fun Double.formatCurrency(): String {
    val abs = kotlin.math.abs(this)
    val sign = if (this < 0) "-" else ""
    val intPart = abs.toLong()
    val decPart = ((abs - intPart) * 100).toLong()
    val intStr = intPart.toString()
    val thousands = intStr.reversed().chunked(3).joinToString(".").reversed()
    return "$sign$thousands,${decPart.toString().padStart(2, '0')} €"
}
