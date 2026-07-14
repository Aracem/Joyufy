package com.aracem.joyufy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.aracem.joyufy.data.mapper.toDomain
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class InvestmentSnapshotRepository(private val db: JoyufyDatabase) {

    fun observeSnapshotsForAccount(accountId: Long): Flow<List<InvestmentSnapshot>> =
        db.joyufyDatabaseQueries
            .getSnapshotsForAccount(accountId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    fun observeAllSnapshots(): Flow<List<InvestmentSnapshot>> =
        db.joyufyDatabaseQueries
            .getAllSnapshots()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { it.toDomain() } }

    suspend fun getLatestSnapshot(accountId: Long): InvestmentSnapshot? = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getLatestSnapshotForAccount(accountId)
            .executeAsOneOrNull()
            ?.toDomain()
    }

    suspend fun getSnapshotsForAccount(accountId: Long): List<InvestmentSnapshot> = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getSnapshotsForAccount(accountId)
            .executeAsList()
            .map { it.toDomain() }
    }

    suspend fun getSnapshotsBetween(
        accountId: Long,
        from: Long,
        to: Long,
    ): List<InvestmentSnapshot> = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getSnapshotsBetween(accountId, from, to)
            .executeAsList()
            .map { it.toDomain() }
    }

    suspend fun getAllSnapshots(): List<InvestmentSnapshot> = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getAllSnapshots()
            .executeAsList()
            .map { it.toDomain() }
    }

    suspend fun getAccountsMissingThisWeek(weekDate: Long): List<Account> = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getInvestmentAccountsMissingThisWeek(weekDate)
            .executeAsList()
            .map { it.toDomain() }
    }

    suspend fun insertSnapshot(
        accountId: Long,
        totalValue: Double,
        weekDate: Long,
        deposits: Double = 0.0,
        withdrawals: Double = 0.0,
        fees: Double = 0.0,
        dividends: Double = 0.0,
        note: String? = null,
    ): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.insertSnapshot(
            account_id = accountId,
            total_value = totalValue,
            week_date = weekDate,
            deposit_amount = deposits,
            withdrawal_amount = withdrawals,
            fee_amount = fees,
            dividend_amount = dividends,
            note = note,
        )
    }

    suspend fun insertSnapshotWithId(
        id: Long,
        accountId: Long,
        totalValue: Double,
        weekDate: Long,
        deposits: Double = 0.0,
        withdrawals: Double = 0.0,
        fees: Double = 0.0,
        dividends: Double = 0.0,
        note: String? = null,
    ): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.insertSnapshotWithId(
            id = id,
            account_id = accountId,
            total_value = totalValue,
            week_date = weekDate,
            deposit_amount = deposits,
            withdrawal_amount = withdrawals,
            fee_amount = fees,
            dividend_amount = dividends,
            note = note,
        )
    }

    suspend fun updateSnapshot(
        id: Long,
        totalValue: Double,
        weekDate: Long,
        deposits: Double = 0.0,
        withdrawals: Double = 0.0,
        fees: Double = 0.0,
        dividends: Double = 0.0,
        note: String? = null,
    ): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.updateSnapshot(
            total_value = totalValue,
            week_date = weekDate,
            deposit_amount = deposits,
            withdrawal_amount = withdrawals,
            fee_amount = fees,
            dividend_amount = dividends,
            note = note,
            id = id,
        )
    }

    suspend fun deleteSnapshot(id: Long): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.deleteSnapshot(id)
    }

    suspend fun deleteAllSnapshots(): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.deleteAllSnapshots()
    }

    suspend fun deleteSnapshotsForAccount(accountId: Long): Unit = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries.deleteSnapshotsForAccount(accountId)
    }
}
