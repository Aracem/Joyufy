package com.aracem.joyufy.data.repository

import com.aracem.joyufy.data.mapper.toColorHex
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Serializable DTOs ─────────────────────────────────────────────────────────

@Serializable
data class AccountBackup(
    val id: Long,
    val name: String,
    val type: String,
    val colorHex: String,
    val logoUrl: String? = null,
    val position: Int,
    val createdAt: Long,
)

@Serializable
data class TransactionBackup(
    val id: Long,
    val accountId: Long,
    val type: String,
    val amount: Double,
    val category: String? = null,
    val description: String? = null,
    val relatedAccountId: Long? = null,
    val date: Long,
)

@Serializable
data class SnapshotBackup(
    val id: Long,
    val accountId: Long,
    val totalValue: Double,
    val weekDate: Long,
)

@Serializable
data class JoyufyBackup(
    val version: Int = 1,
    val exportedAt: Long,
    val accounts: List<AccountBackup>,
    val transactions: List<TransactionBackup>,
    val snapshots: List<SnapshotBackup>,
)

// ── Repository ────────────────────────────────────────────────────────────────

class BackupRepository(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(): String = withContext(Dispatchers.IO) {
        val accounts = accountRepository.getAllAccounts()
        val transactions = transactionRepository.getAllTransactions()
        val snapshots = snapshotRepository.getAllSnapshots()

        val backup = JoyufyBackup(
            exportedAt = System.currentTimeMillis(),
            accounts = accounts.map { it.toBackup() },
            transactions = transactions.map { it.toBackup() },
            snapshots = snapshots.map { it.toBackup() },
        )
        json.encodeToString(JoyufyBackup.serializer(), backup)
    }

    suspend fun import(jsonString: String): Unit = withContext(Dispatchers.IO) {
        val backup = json.decodeFromString(JoyufyBackup.serializer(), jsonString)

        // Delete all existing data — cascade deletes transactions and snapshots
        transactionRepository.deleteAllTransactions()
        snapshotRepository.deleteAllSnapshots()
        accountRepository.getAllAccounts().forEach { accountRepository.deleteAccount(it.id) }

        // Restore accounts preserving original IDs via direct insert
        backup.accounts.forEach { a ->
            accountRepository.insertAccount(
                name = a.name,
                type = AccountType.valueOf(a.type),
                colorHex = a.colorHex,
                logoUrl = a.logoUrl,
                position = a.position,
            )
        }

        // Restore transactions
        backup.transactions.forEach { t ->
            transactionRepository.insertTransaction(
                accountId = t.accountId,
                type = TransactionType.valueOf(t.type),
                amount = t.amount,
                category = t.category,
                description = t.description,
                relatedAccountId = t.relatedAccountId,
                date = t.date,
            )
        }

        // Restore snapshots
        backup.snapshots.forEach { s ->
            snapshotRepository.insertSnapshot(
                accountId = s.accountId,
                totalValue = s.totalValue,
                weekDate = s.weekDate,
            )
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun Account.toBackup() = AccountBackup(
        id = id,
        name = name,
        type = type.name,
        colorHex = toColorHex(),
        logoUrl = logoUrl,
        position = position,
        createdAt = createdAt,
    )

    private fun Transaction.toBackup() = TransactionBackup(
        id = id,
        accountId = accountId,
        type = type.name,
        amount = amount,
        category = category,
        description = description,
        relatedAccountId = relatedAccountId,
        date = date,
    )

    private fun InvestmentSnapshot.toBackup() = SnapshotBackup(
        id = id,
        accountId = accountId,
        totalValue = totalValue,
        weekDate = weekDate,
    )
}
