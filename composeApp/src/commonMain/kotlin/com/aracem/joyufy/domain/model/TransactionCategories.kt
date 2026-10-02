package com.aracem.joyufy.domain.model

/**
 * Categories the user has actually used, most relevant first: by number of uses,
 * then by most recent use, then alphabetically. The returned label keeps the
 * casing of the most recent transaction that used it.
 *
 * Comparison is case-insensitive and done in Kotlin on purpose — SQLite's LOWER()
 * is ASCII-only and would not fold accented labels like "Inversión"/"INVERSIÓN".
 */
fun rankCategoriesByUsage(transactions: List<Transaction>, limit: Int? = null): List<String> {
    val ranked = transactions
        .asSequence()
        .mapNotNull { tx -> tx.category?.trim()?.takeIf { it.isNotEmpty() }?.let { it to tx.date } }
        .groupBy { (label, _) -> label.lowercase() }
        .map { (_, entries) ->
            Triple(
                entries.maxBy { it.second }.first,
                entries.size,
                entries.maxOf { it.second },
            )
        }
        .sortedWith(
            compareByDescending<Triple<String, Int, Long>> { it.second }
                .thenByDescending { it.third }
                .thenBy { it.first.lowercase() },
        )
        .map { it.first }
    return if (limit != null) ranked.take(limit) else ranked
}

/**
 * Autocomplete suggestions: the user's real categories first (already ranked by
 * [rankCategoriesByUsage]), with the predefined catalog only filling the tail so
 * a fresh install still has something to offer.
 */
fun categorySuggestions(used: List<String>, limit: Int? = null): List<String> {
    val seen = HashSet<String>()
    val out = ArrayList<String>(used.size + TransactionCategory.entries.size)
    used.forEach { if (seen.add(it.lowercase())) out += it }
    TransactionCategory.entries.forEach { if (seen.add(it.label.lowercase())) out += it.label }
    return if (limit != null) out.take(limit) else out
}
