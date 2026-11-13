package com.aracem.joyufy.ui.settings

import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.domain.model.CategorizationRule
import com.aracem.joyufy.domain.model.categoryFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun applyCategorizationRules(
    rules: List<CategorizationRule>,
    transactions: TransactionRepository,
): Int {
    return withContext(Dispatchers.IO) {
        var updatedCount = 0
        transactions.getAllTransactions()
            .filter { it.category.isNullOrBlank() }
            .forEach { transaction ->
                rules.categoryFor(transaction.description.orEmpty(), transaction.accountId)?.let { category ->
                    transactions.updateTransaction(
                        transaction.id,
                        transaction.type,
                        transaction.amount,
                        category,
                        transaction.description,
                        transaction.relatedAccountId,
                        transaction.date,
                    )
                    updatedCount++
                }
            }
        updatedCount
    }
}
