package com.aracem.nexlify.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.aracem.nexlify.data.mapper.toColorHex
import com.aracem.nexlify.data.mapper.toDomain
import com.aracem.nexlify.db.NexlifyDatabase
import com.aracem.nexlify.domain.model.Account
import com.aracem.nexlify.domain.model.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountRepository(private val db: NexlifyDatabase) {

    fun observeAccounts(): Flow<List<Account>> =
        db.nexlifyDatabaseQueries
            .getAllAccounts()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    suspend fun getAccountById(id: Long): Account? = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries.getAccountById(id).executeAsOneOrNull()?.toDomain()
    }

    suspend fun insertAccount(
        name: String,
        type: AccountType,
        colorHex: String,
        position: Int,
    ): Unit = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries.insertAccount(
            name = name,
            type = type.name,
            color_hex = colorHex,
            position = position.toLong(),
            created_at = System.currentTimeMillis(),
        )
    }

    suspend fun updateAccount(account: Account): Unit = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries.updateAccount(
            name = account.name,
            color_hex = account.toColorHex(),
            position = account.position.toLong(),
            id = account.id,
        )
    }

    suspend fun archiveAccount(id: Long): Unit = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries.archiveAccount(id)
    }

    suspend fun deleteAccount(id: Long): Unit = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries.deleteAccount(id)
    }
}
