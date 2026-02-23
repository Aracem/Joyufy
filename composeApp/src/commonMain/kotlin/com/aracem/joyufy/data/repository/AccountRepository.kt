package com.aracem.joyufy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.aracem.joyufy.data.mapper.toColorHex
import com.aracem.joyufy.data.mapper.toDomain
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountRepository(private val db: JoyufyDatabase) {

    fun observeAccounts(): Flow<List<Account>> =
        db.joyufyDatabaseQueries
            .getAllAccounts()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    suspend fun getAllAccounts(): List<Account> = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.getAllAccounts().executeAsList().map { it.toDomain() }
    }

    suspend fun getAccountById(id: Long): Account? = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.getAccountById(id).executeAsOneOrNull()?.toDomain()
    }

    suspend fun insertAccount(
        name: String,
        type: AccountType,
        colorHex: String,
        logoUrl: String?,
        position: Int,
    ): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.insertAccount(
            name = name,
            type = type.name,
            color_hex = colorHex,
            logo_url = logoUrl,
            position = position.toLong(),
            created_at = System.currentTimeMillis(),
        )
    }

    suspend fun updateAccount(account: Account): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.updateAccount(
            name = account.name,
            color_hex = account.toColorHex(),
            logo_url = account.logoUrl,
            position = account.position.toLong(),
            id = account.id,
        )
    }

    suspend fun archiveAccount(id: Long): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.archiveAccount(id)
    }

    suspend fun deleteAccount(id: Long): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.deleteAccount(id)
    }
}
