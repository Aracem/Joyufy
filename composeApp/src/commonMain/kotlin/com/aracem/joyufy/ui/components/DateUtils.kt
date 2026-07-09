package com.aracem.joyufy.ui.components

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

const val MILLIS_IN_DAY = 86_400_000L
const val MILLIS_IN_WEEK = 7 * MILLIS_IN_DAY

// ── Formatting ────────────────────────────────────────────────────────────────

fun Long.formatDate(): String {
    val local = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%04d".format(local.dayOfMonth, local.monthNumber, local.year)
}

fun parseDateInputToMillis(
    text: String,
    dayOffsetMillis: Long = 12 * 3600_000L,
): Long? {
    val parts = text.trim().split("/")
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    return runCatching<Long> {
        LocalDate(year, month, day)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds() + dayOffsetMillis
    }.getOrNull()
}

fun Long.formatShortDate(): String {
    val local = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "%d/%02d".format(local.dayOfMonth, local.monthNumber)
}

fun Long.formatMonthDay(): String {
    val local = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d".format(local.dayOfMonth, local.monthNumber)
}

fun Long.formatWeekRange(weekLabel: String): String {
    val sundayMs = this + 6 * MILLIS_IN_DAY
    return "$weekLabel ${isoWeekNumber(this)} · ${formatDate()} – ${sundayMs.formatDate()}"
}

// "S16-2026" — used in snapshot picker
fun Long.formatIsoWeekLabel(): String {
    val thursdayMs = this + 3 * MILLIS_IN_DAY
    val thursday = Instant.fromEpochMilliseconds(thursdayMs).toLocalDateTime(TimeZone.currentSystemDefault())
    return "S%02d-%d".format(isoWeekNumber(this), thursday.year)
}

// ── Week calculations ─────────────────────────────────────────────────────────

fun currentWeekStartMillis(): Long {
    val now = Clock.System.now()
    val dayOfWeek = now.toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.ordinal // Mon=0
    return (now.toEpochMilliseconds() / MILLIS_IN_DAY - dayOfWeek) * MILLIS_IN_DAY
}

fun weekStartsForRange(range: Int?, now: Long = currentWeekStartMillis()): List<Long> = when {
    range != null -> (range downTo 0).map { now - it * MILLIS_IN_WEEK }
    else -> (260 downTo 0).map { now - it * MILLIS_IN_WEEK }
}

fun weekStartsForYtd(now: Long = currentWeekStartMillis()): List<Long> {
    val tz = TimeZone.currentSystemDefault()
    val year = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).year
    val jan1Ms = LocalDate(year, 1, 1).atStartOfDayIn(tz).toEpochMilliseconds()
    return generateSequence(jan1Ms) { it + MILLIS_IN_WEEK }.takeWhile { it <= now }.toList()
}

// ── Month boundaries ──────────────────────────────────────────────────────────

fun monthStartMillis(year: Int, month: Int): Long =
    LocalDate(year, month, 1).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

fun monthEndMillis(year: Int, month: Int): Long {
    val lastDay = daysInMonth(year, month)
    return LocalDate(year, month, lastDay)
        .atStartOfDayIn(TimeZone.currentSystemDefault())
        .toEpochMilliseconds() + MILLIS_IN_DAY - 1
}

fun yearStartMillis(year: Int): Long =
    LocalDate(year, 1, 1).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

fun yearEndMillis(year: Int): Long =
    LocalDate(year, 12, 31).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + MILLIS_IN_DAY - 1

// ── Internal helpers ──────────────────────────────────────────────────────────

private fun isoWeekNumber(mondayMs: Long): Int {
    val tz = TimeZone.currentSystemDefault()
    val thursdayMs = mondayMs + 3 * MILLIS_IN_DAY
    val thursday = Instant.fromEpochMilliseconds(thursdayMs).toLocalDateTime(tz)
    val jan1Ms = LocalDate(thursday.year, 1, 1).atStartOfDayIn(tz).toEpochMilliseconds()
    val dayOfYear = ((thursdayMs - jan1Ms) / MILLIS_IN_DAY).toInt()
    return dayOfYear / 7 + 1
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 30
}
