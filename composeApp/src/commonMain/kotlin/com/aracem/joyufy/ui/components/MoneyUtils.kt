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

/** Formats a chart tick with enough precision to keep neighboring labels distinct. */
internal fun Double.formatAxisAmount(step: Double): String {
    val magnitude = kotlin.math.abs(this)
    val interval = kotlin.math.abs(step)
    val divisor = when {
        magnitude >= 1_000_000 -> 1_000_000.0
        magnitude >= 1_000 -> 1_000.0
        else -> 1.0
    }
    val suffix = when (divisor) {
        1_000_000.0 -> "M"
        1_000.0 -> "k"
        else -> ""
    }
    val scaledInterval = interval / divisor
    val decimals = if (scaledInterval >= 1.0) {
        0
    } else {
        kotlin.math.ceil(-kotlin.math.log10(scaledInterval))
            .toInt()
            .coerceIn(1, 6)
    }
    val sign = if (this < 0) "-" else ""
    val formatted = "%.${decimals}f".format(magnitude / divisor)
    return "$sign$formatted$suffix"
}

// Two-decimal string for amount input fields
fun Double.formatInputAmount(): String = "%.2f".format(this)

fun Double.formatPercent(): String = "%.2f".format(this) + "%"
