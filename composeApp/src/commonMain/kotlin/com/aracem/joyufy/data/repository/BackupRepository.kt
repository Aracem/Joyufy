package com.aracem.joyufy.data.repository

import com.aracem.joyufy.data.mapper.toColorHex
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionReviewStatus
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
    val reviewStatus: String = TransactionReviewStatus.REVIEWED.name,
    val importBatch: String? = null,
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

/**
 * Per-entity delta counts when comparing a backup against the local DB.
 * `cloudExportedAt` and `localCount*` are useful for the confirmation dialog
 * so the user can sanity-check what they're about to replace.
 *
 * "Added" = present in cloud, missing locally.
 * "Removed" = present locally, missing from cloud.
 * "Modified" = present in both with the same id but different field values.
 */
data class BackupDiff(
    val cloudExportedAt: Long,
    val accountsAdded: Int,
    val accountsRemoved: Int,
    val accountsModified: Int,
    val transactionsAdded: Int,
    val transactionsRemoved: Int,
    val transactionsModified: Int,
    val snapshotsAdded: Int,
    val snapshotsRemoved: Int,
    val snapshotsModified: Int,
) {
    val hasChanges: Boolean
        get() = accountsAdded + accountsRemoved + accountsModified +
            transactionsAdded + transactionsRemoved + transactionsModified +
            snapshotsAdded + snapshotsRemoved + snapshotsModified > 0
}

// ── Repository ────────────────────────────────────────────────────────────────

class BackupRepository(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Compares a cloud backup JSON against the current local DB and returns
     * per-entity add/remove/modify counts. Does not mutate anything.
     */
    suspend fun diffAgainstLocal(jsonString: String): BackupDiff = withContext(Dispatchers.IO) {
        val cloud = json.decodeFromString(JoyufyBackup.serializer(), jsonString)

        val localAccounts = accountRepository.getAllAccounts().map { it.toBackup() }
        val localTransactions = transactionRepository.getAllTransactions().map { it.toBackup() }
        val localSnapshots = snapshotRepository.getAllSnapshots().map { it.toBackup() }

        val (aAdd, aRem, aMod) = diffById(
            cloud.accounts.associateBy { it.id },
            localAccounts.associateBy { it.id },
        )
        val (tAdd, tRem, tMod) = diffById(
            cloud.transactions.associateBy { it.id },
            localTransactions.associateBy { it.id },
        )
        val (sAdd, sRem, sMod) = diffById(
            cloud.snapshots.associateBy { it.id },
            localSnapshots.associateBy { it.id },
        )

        BackupDiff(
            cloudExportedAt = cloud.exportedAt,
            accountsAdded = aAdd, accountsRemoved = aRem, accountsModified = aMod,
            transactionsAdded = tAdd, transactionsRemoved = tRem, transactionsModified = tMod,
            snapshotsAdded = sAdd, snapshotsRemoved = sRem, snapshotsModified = sMod,
        )
    }

    private fun <T> diffById(cloud: Map<Long, T>, local: Map<Long, T>): Triple<Int, Int, Int> {
        val added = cloud.keys - local.keys
        val removed = local.keys - cloud.keys
        val common = cloud.keys intersect local.keys
        val modified = common.count { cloud[it] != local[it] }
        return Triple(added.size, removed.size, modified)
    }

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
        accountRepository.deleteAllAccounts()

        // Restore accounts preserving original IDs so foreign keys in transactions/snapshots remain valid
        backup.accounts.forEach { a ->
            accountRepository.insertAccountWithId(
                id = a.id,
                name = a.name,
                type = AccountType.valueOf(a.type),
                colorHex = a.colorHex,
                logoUrl = a.logoUrl,
                position = a.position,
                createdAt = a.createdAt,
            )
        }

        // Restore transactions
        backup.transactions.forEach { t ->
            transactionRepository.insertTransactionWithId(
                id = t.id,
                accountId = t.accountId,
                type = TransactionType.valueOf(t.type),
                amount = t.amount,
                category = t.category,
                description = t.description,
                relatedAccountId = t.relatedAccountId,
                date = t.date,
                reviewStatus = runCatching { TransactionReviewStatus.valueOf(t.reviewStatus) }
                    .getOrDefault(TransactionReviewStatus.REVIEWED),
                importBatch = t.importBatch,
            )
        }

        // Restore snapshots
        backup.snapshots.forEach { s ->
            snapshotRepository.insertSnapshotWithId(
                id = s.id,
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
        reviewStatus = reviewStatus.name,
        importBatch = importBatch,
    )

    private fun InvestmentSnapshot.toBackup() = SnapshotBackup(
        id = id,
        accountId = accountId,
        totalValue = totalValue,
        weekDate = weekDate,
    )
}
