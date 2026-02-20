package com.aracem.nexlify.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.aracem.nexlify.data.mapper.toDomain
import com.aracem.nexlify.db.NexlifyDatabase
import com.aracem.nexlify.domain.model.Transaction
import com.aracem.nexlify.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TransactionRepository(private val db: NexlifyDatabase) {

    fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>> =
        db.nexlifyDatabaseQueries
            .getTransactionsForAccount(accountId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    suspend fun getTransactionsBetween(
        accountId: Long,
        from: Long,
        to: Long,
    ): List<Transaction> = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries
            .getTransactionsBetween(accountId, from, to)
            .executeAsList()
            .map { it.toDomain() }
    }

    suspend fun getAccountBalance(accountId: Long): Double = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries
            .getAccountBalance(accountId)
            .executeAsOne()
    }

    suspend fun insertTransaction(
        accountId: Long,
        type: TransactionType,
        amount: Double,
        category: String?,
        description: String?,
        relatedAccountId: Long?,
        date: Long,
    ): Unit = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries.insertTransaction(
            account_id = accountId,
            type = type.name,
            amount = amount,
            category = category,
            description = description,
            related_account_id = relatedAccountId,
            date = date,
        )
    }

    suspend fun deleteTransaction(id: Long): Unit = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries.deleteTransaction(id)
    }
}
