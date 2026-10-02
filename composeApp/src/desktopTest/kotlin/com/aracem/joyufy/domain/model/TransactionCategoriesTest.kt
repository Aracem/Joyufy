package com.aracem.joyufy.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionCategoriesTest {

    private var nextId = 1L

    private fun tx(category: String?, date: Long): Transaction = Transaction(
        id = nextId++,
        accountId = 1L,
        type = TransactionType.EXPENSE,
        amount = 10.0,
        category = category,
        description = null,
        relatedAccountId = null,
        date = date,
    )

    @Test
    fun ranksMoreUsedCategoriesFirst() {
        val transactions = listOf(
            tx("Gimnasio", 1_000L),
            tx("Supermercado", 2_000L),
            tx("Supermercado", 3_000L),
            tx("Supermercado", 4_000L),
            tx("Gimnasio", 5_000L),
        )

        assertEquals(listOf("Supermercado", "Gimnasio"), rankCategoriesByUsage(transactions))
    }

    @Test
    fun breaksUsageTiesByMostRecentUse() {
        val transactions = listOf(
            tx("Viejo", 1_000L),
            tx("Reciente", 9_000L),
        )

        assertEquals(listOf("Reciente", "Viejo"), rankCategoriesByUsage(transactions))
    }

    @Test
    fun foldsCasingAndKeepsMostRecentSpelling() {
        val transactions = listOf(
            tx("supermercado", 1_000L),
            tx("SUPERMERCADO", 2_000L),
            tx("Supermercado", 3_000L),
        )

        // One entry, cased as the latest use — and it counts as 3 uses, not 1.
        assertEquals(listOf("Supermercado"), rankCategoriesByUsage(transactions))
    }

    @Test
    fun foldsAccentedCasingThatSqliteLowerWouldMiss() {
        val transactions = listOf(
            tx("INVERSIÓN", 1_000L),
            tx("Inversión", 2_000L),
        )

        assertEquals(listOf("Inversión"), rankCategoriesByUsage(transactions))
    }

    @Test
    fun ignoresBlankAndMissingCategories() {
        val transactions = listOf(
            tx(null, 1_000L),
            tx("", 2_000L),
            tx("   ", 3_000L),
            tx("  Salud  ", 4_000L),
        )

        assertEquals(listOf("Salud"), rankCategoriesByUsage(transactions))
    }

    @Test
    fun appliesLimitToRankedCategories() {
        val transactions = listOf(
            tx("A", 3_000L),
            tx("A", 4_000L),
            tx("B", 2_000L),
            tx("C", 1_000L),
        )

        assertEquals(listOf("A", "B"), rankCategoriesByUsage(transactions, limit = 2))
    }

    @Test
    fun suggestionsPutUsedCategoriesAheadOfPresets() {
        val used = listOf("Gimnasio", "Supermercado")

        val suggestions = categorySuggestions(used)

        assertEquals(listOf("Gimnasio", "Supermercado"), suggestions.take(2))
        assertTrue(suggestions.containsAll(TransactionCategory.entries.map { it.label }))
    }

    @Test
    fun suggestionsDoNotDuplicateAPresetAlreadyUsed() {
        // "Supermercado" is also a preset label — it must appear once, up front.
        val suggestions = categorySuggestions(listOf("Supermercado"))

        assertEquals("Supermercado", suggestions.first())
        assertEquals(1, suggestions.count { it.equals("Supermercado", ignoreCase = true) })
        assertEquals(TransactionCategory.entries.size, suggestions.size)
    }

    @Test
    fun suggestionsFallBackToPresetsWhenNothingUsedYet() {
        assertEquals(TransactionCategory.entries.map { it.label }, categorySuggestions(emptyList()))
    }
}
