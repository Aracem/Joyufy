package com.aracem.joyufy.ui.components

/** Parses a decimal amount while excluding values that cannot represent money. */
fun parseMoneyInput(text: String): Double? =
    text.replace(",", ".").toDoubleOrNull()?.takeIf { it.isFinite() }

fun Double.formatCurrency(): String {
    val sign = if (this < 0) "-" else ""
    val totalCents = kotlin.math.round(kotlin.math.abs(this) * 100).toLong()
    val intPart = totalCents / 100
    val decPart = totalCents % 100
    val thousands = intPart.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$sign$thousands,${decPart.toString().padStart(2, '0')} €"
}

// Compact format for chart Y-axis labels where space is limited
fun Double.formatAmountCompact(): String {
    val sign = if (this < 0) "-" else ""
    val magnitude = kotlin.math.abs(this)
    return when {
        magnitude >= 1_000_000 -> "$sign${"%.1f".format(magnitude / 1_000_000)}M"
        magnitude >= 1_000     -> "$sign${"%.0f".format(magnitude / 1_000)}k"
        else                   -> "$sign${"%.0f".format(magnitude)}"
    }
}

// Two-decimal string for amount input fields
fun Double.formatInputAmount(): String = "%.2f".format(this)

fun Double.formatPercent(): String = "%.2f".format(this) + "%"
