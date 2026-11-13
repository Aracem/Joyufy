package com.aracem.joyufy.ui.dashboard

import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.monthStartMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Calendar month in the user's local time zone. */
data class AnalysisMonth(val year: Int, val month: Int) : Comparable<AnalysisMonth> {
    init { require(year >= 1 && month in 1..12) }

    fun shift(delta: Int): AnalysisMonth {
        val index = year * 12 + month - 1 + delta
        val shiftedYear = index / 12
        return AnalysisMonth(shiftedYear, index - shiftedYear * 12 + 1)
    }

    override fun compareTo(other: AnalysisMonth): Int =
        compareValuesBy(this, other, AnalysisMonth::year, AnalysisMonth::month)
}

data class CategoryComparison(
    /** Null denotes spending without a category. */
    val category: String?,
    val current: Double,
    val previous: Double?,
)

data class MonthlyAnalysis(
    val month: AnalysisMonth,
    val income: Double,
    val expenses: Double,
    val categories: List<CategoryComparison>,
    val previousIncome: Double?,
    val previousExpenses: Double?,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
    val isCurrentMonth: Boolean,
) {
    val cashFlow: Double get() = income - expenses
}

/** Only bank/cash transactions are supplied; transfer legs are excluded from cash flow. */
internal fun calculateMonthlyAnalysis(
    transactions: List<Transaction>,
    month: AnalysisMonth,
    currentMonth: AnalysisMonth,
): MonthlyAnalysis {
    val cashTransactions = transactions.filter {
        it.relatedAccountId == null && (it.type == TransactionType.INCOME || it.type == TransactionType.EXPENSE)
    }
    val earliestMonth = cashTransactions.minOfOrNull { transaction ->
        val date = Instant.fromEpochMilliseconds(transaction.date).toLocalDateTime(TimeZone.currentSystemDefault())
        AnalysisMonth(date.year, date.monthNumber)
    }
    fun forMonth(period: AnalysisMonth): List<Transaction> {
        val start = monthStartMillis(period.year, period.month)
        val next = period.shift(1)
        val endExclusive = monthStartMillis(next.year, next.month)
        return cashTransactions.filter { it.date >= start && it.date < endExclusive }
    }
    fun categoryTotals(items: List<Transaction>): Map<String?, Double> = items
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category?.trim()?.takeIf(String::isNotEmpty) }
        .mapValues { (_, entries) -> entries.sumOf { it.amount } }

    val current = forMonth(month)
    val previousMonth = month.shift(-1)
    val previousAvailable = earliestMonth != null && previousMonth >= earliestMonth
    val previous = if (previousAvailable) forMonth(previousMonth) else emptyList()
    val currentCategories = categoryTotals(current)
    val previousCategories = categoryTotals(previous)
    val categories = (currentCategories.keys + previousCategories.keys).map { category ->
        CategoryComparison(category, currentCategories[category] ?: 0.0,
            if (previousAvailable) previousCategories[category] ?: 0.0 else null)
    }.sortedWith(compareByDescending<CategoryComparison> { it.current }.thenBy { it.category ?: "" })
    fun total(items: List<Transaction>, type: TransactionType) = items.filter { it.type == type }.sumOf { it.amount }
    return MonthlyAnalysis(
        month = month,
        income = total(current, TransactionType.INCOME),
        expenses = total(current, TransactionType.EXPENSE),
        categories = categories,
        previousIncome = if (previousAvailable) total(previous, TransactionType.INCOME) else null,
        previousExpenses = if (previousAvailable) total(previous, TransactionType.EXPENSE) else null,
        canGoPrevious = earliestMonth != null && month > earliestMonth,
        canGoNext = month < currentMonth,
        isCurrentMonth = month == currentMonth,
    )
}
