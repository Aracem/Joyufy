package com.aracem.joyufy.ui.dashboard

import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.monthStartMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthlyAnalysisTest {
    private fun tx(year: Int, month: Int, type: TransactionType, amount: Double, category: String? = null,
        relatedAccountId: Long? = null) = Transaction(
        id = 0, accountId = 1, type = type, amount = amount, category = category,
        description = null, relatedAccountId = relatedAccountId, date = monthStartMillis(year, month),
    )

    @Test
    fun comparisonCrossesYearAndExcludesBothTransferLegs() {
        val transactions = listOf(
            tx(2025, 12, TransactionType.EXPENSE, 20.0, "Food"),
            tx(2026, 1, TransactionType.EXPENSE, 30.0, "Food"),
            tx(2026, 1, TransactionType.INCOME, 100.0),
            tx(2026, 1, TransactionType.EXPENSE, 500.0, "Transfer", 2),
            tx(2026, 1, TransactionType.INCOME, 500.0, "Transfer", 1),
        )
        val analysis = calculateMonthlyAnalysis(transactions, AnalysisMonth(2026, 1), AnalysisMonth(2026, 1))
        assertEquals(100.0, analysis.income)
        assertEquals(30.0, analysis.expenses)
        assertEquals(70.0, analysis.cashFlow)
        assertEquals(listOf(CategoryComparison("Food", 30.0, 20.0)), analysis.categories)
        assertTrue(analysis.canGoPrevious)
        assertFalse(analysis.canGoNext)
        assertEquals(AnalysisMonth(2025, 12), analysis.month.shift(-1))
    }

    @Test
    fun allCategoriesAndUncategorizedReconcileWithExpenses() {
        val transactions = (1..6).map { tx(2026, 3, TransactionType.EXPENSE, it.toDouble(), "Category $it") } +
            listOf(tx(2026, 3, TransactionType.EXPENSE, 8.0, "  "),
                tx(2026, 3, TransactionType.EXPENSE, 2.0, null))
        val analysis = calculateMonthlyAnalysis(transactions, AnalysisMonth(2026, 3), AnalysisMonth(2026, 3))
        assertEquals(7, analysis.categories.size)
        assertEquals(31.0, analysis.expenses)
        assertEquals(analysis.expenses, analysis.categories.sumOf { it.current })
        assertEquals(10.0, analysis.categories.single { it.category == null }.current)
        assertNull(analysis.previousExpenses)
        assertFalse(analysis.canGoPrevious)
    }

    @Test
    fun emptyMonthsAfterFirstDataAreComparableAndNavigable() {
        val transactions = listOf(tx(2025, 11, TransactionType.EXPENSE, 4.0, "Food"))
        val analysis = calculateMonthlyAnalysis(transactions, AnalysisMonth(2026, 1), AnalysisMonth(2026, 2))
        assertEquals(0.0, analysis.expenses)
        assertEquals(0.0, analysis.previousExpenses)
        assertTrue(analysis.categories.isEmpty())
        assertTrue(analysis.canGoPrevious)
        assertTrue(analysis.canGoNext)
        assertFalse(analysis.isCurrentMonth)
    }

    @Test
    fun previousOnlyCategoryRemainsVisible() {
        val transactions = listOf(tx(2026, 4, TransactionType.EXPENSE, 9.0, "Travel"))
        val analysis = calculateMonthlyAnalysis(transactions, AnalysisMonth(2026, 5), AnalysisMonth(2026, 5))
        assertEquals(listOf(CategoryComparison("Travel", 0.0, 9.0)), analysis.categories)
    }
    @Test
    fun monthBoundariesIncludeLastInstantAndExcludeNextMonth() {
        val march = tx(2026, 3, TransactionType.EXPENSE, 10.0, "Last day")
            .copy(date = monthStartMillis(2026, 4) - 1)
        val april = tx(2026, 4, TransactionType.EXPENSE, 20.0, "Next month")
        val analysis = calculateMonthlyAnalysis(listOf(march, april), AnalysisMonth(2026, 3), AnalysisMonth(2026, 4))
        assertEquals(10.0, analysis.expenses)
        assertEquals(listOf("Last day"), analysis.categories.map { it.category })
    }

}
