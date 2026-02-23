package com.aracem.joyufy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.aracem.joyufy.data.mapper.toDomain
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TransactionRepository(private val db: JoyufyDatabase) {

    fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>> =
        db.joyufyDatabaseQueries
            .getTransactionsForAccount(accountId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    fun observeAllBankCashTransactions(): Flow<List<Transaction>> =
        db.joyufyDatabaseQueries
            .getAllBankCashTransactions()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    suspend fun getTransactionsBetween(
        accountId: Long,
        from: Long,
        to: Long,
    ): List<Transaction> = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getTransactionsBetween(accountId, from, to)
            .executeAsList()
            .map { it.toDomain() }
    }

    suspend fun getAllBankCashTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getAllBankCashTransactions()
            .executeAsList()
            .map { it.toDomain() }
    }

    suspend fun getAllTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getAllTransactions()
            .executeAsList()
            .map { it.toDomain() }
    }

    suspend fun deleteAllTransactions(): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.deleteAllTransactions()
    }

    suspend fun getAccountBalance(accountId: Long): Double = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
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
        db.joyufyDatabaseQueries.insertTransaction(
            account_id = accountId,
            type = type.name,
            amount = amount,
            category = category,
            description = description,
            related_account_id = relatedAccountId,
            date = date,
        )
    }

    suspend fun updateTransaction(
        id: Long,
        type: TransactionType,
        amount: Double,
        category: String?,
        description: String?,
        relatedAccountId: Long?,
        date: Long,
    ): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.updateTransaction(
            type = type.name,
            amount = amount,
            category = category,
            description = description,
            related_account_id = relatedAccountId,
            date = date,
            id = id,
        )
    }

    suspend fun deleteTransaction(id: Long): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.deleteTransaction(id)
    }
}
