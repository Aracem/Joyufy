package com.aracem.joyufy.ui.components

fun Double.formatCurrency(): String {
    val sign = if (this < 0) "-" else ""
    val totalCents = kotlin.math.round(kotlin.math.abs(this) * 100).toLong()
    val intPart = totalCents / 100
    val decPart = totalCents % 100
    val thousands = intPart.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$sign$thousands,${decPart.toString().padStart(2, '0')} €"
}

// Compact format for chart Y-axis labels where space is limited
fun Double.formatAmountCompact(): String = when {
    this >= 1_000_000 -> "${"%.1f".format(this / 1_000_000)}M"
    this >= 1_000     -> "${"%.0f".format(this / 1_000)}k"
    else              -> "%.0f".format(this)
}

// Two-decimal string for amount input fields
fun Double.formatInputAmount(): String = "%.2f".format(this)

fun Double.formatPercent(): String = "%.2f".format(this) + "%"
