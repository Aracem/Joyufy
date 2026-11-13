package com.aracem.joyufy.ui.account

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionReviewStatus
import com.aracem.joyufy.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailTransferUndoTest {
    @Test
    fun editingTransferFromDestinationKeepsDirectionAndUpdatesBothLegs() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val sourceId = repos.accounts.insertAccount(
                    name = "Current", type = AccountType.BANK, colorHex = "#7B6EF6", logoUrl = null, position = 0,
                )
                val destinationId = repos.accounts.insertAccount(
                    name = "Savings", type = AccountType.BANK, colorHex = "#34C77B", logoUrl = null, position = 1,
                )
                repos.transactions.insertTransaction(
                    accountId = sourceId,
                    type = TransactionType.EXPENSE,
                    amount = 100.0,
                    category = "OLD",
                    description = "Old transfer",
                    relatedAccountId = destinationId,
                    date = 1_000L,
                )
                repos.transactions.insertTransaction(
                    accountId = destinationId,
                    type = TransactionType.INCOME,
                    amount = 100.0,
                    category = "OLD",
                    description = "Old transfer",
                    relatedAccountId = sourceId,
                    date = 1_000L,
                )
                val destinationLeg = repos.transactions.getTransactionsForAccount(destinationId).single()
                val viewModel = AccountDetailViewModel(
                    accountId = destinationId,
                    accountRepository = repos.accounts,
                    transactionRepository = repos.transactions,
                    snapshotRepository = repos.snapshots,
                )

                viewModel.updateTransaction(
                    id = destinationLeg.id,
                    type = TransactionType.TRANSFER,
                    amount = 75.5,
                    category = "GROCERIES",
                    description = "Updated transfer",
                    relatedAccountId = sourceId,
                    date = 2_000L,
                )

                val editedRows = awaitTransactions(repos.transactions) { rows ->
                    rows.size == 2 && rows.all { it.description == "Updated transfer" }
                }
                assertEquals(
                    setOf(sourceId to TransactionType.EXPENSE, destinationId to TransactionType.INCOME),
                    editedRows.map { it.accountId to it.type }.toSet(),
                )
                assertEquals(
                    setOf(sourceId to destinationId, destinationId to sourceId),
                    editedRows.map { it.accountId to it.relatedAccountId }.toSet(),
                )
                assertTrue(editedRows.all {
                    it.amount == 75.5 && it.category == "GROCERIES" && it.date == 2_000L
                })
            }
        } finally {
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }

    @Test
    fun convertingTransferToExpenseRemovesTheSiblingLeg() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val sourceId = repos.accounts.insertAccount(
                    name = "Current", type = AccountType.BANK, colorHex = "#7B6EF6", logoUrl = null, position = 0,
                )
                val destinationId = repos.accounts.insertAccount(
                    name = "Savings", type = AccountType.BANK, colorHex = "#34C77B", logoUrl = null, position = 1,
                )
                repos.transactions.insertTransaction(
                    accountId = sourceId, type = TransactionType.EXPENSE, amount = 100.0,
                    category = null, description = "Transfer", relatedAccountId = destinationId, date = 1_000L,
                )
                repos.transactions.insertTransaction(
                    accountId = destinationId, type = TransactionType.INCOME, amount = 100.0,
                    category = null, description = "Transfer", relatedAccountId = sourceId, date = 1_000L,
                )
                val destinationLeg = repos.transactions.getTransactionsForAccount(destinationId).single()
                val viewModel = AccountDetailViewModel(
                    accountId = destinationId,
                    accountRepository = repos.accounts,
                    transactionRepository = repos.transactions,
                    snapshotRepository = repos.snapshots,
                )

                viewModel.updateTransaction(
                    id = destinationLeg.id,
                    type = TransactionType.EXPENSE,
                    amount = 12.0,
                    category = "DINING",
                    description = "Dinner",
                    relatedAccountId = null,
                    date = 2_000L,
                )

                val convertedRows = awaitTransactions(repos.transactions) { rows ->
                    rows.size == 1 && rows.single().description == "Dinner"
                }
                val expense = convertedRows.single()
                assertEquals(destinationId, expense.accountId)
                assertEquals(TransactionType.EXPENSE, expense.type)
                assertEquals(12.0, expense.amount)
                assertEquals("DINING", expense.category)
                assertEquals(2_000L, expense.date)
                assertNull(expense.relatedAccountId)
            }
        } finally {
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }

    @Test
    fun deletingTransferRemovesItsPairAndUndoRestoresOriginalRowsAndReviewMetadata() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val sourceId = repos.accounts.insertAccount(
                    name = "Current", type = AccountType.BANK, colorHex = "#7B6EF6", logoUrl = null, position = 0,
                )
                val destinationId = repos.accounts.insertAccount(
                    name = "Savings", type = AccountType.BANK, colorHex = "#34C77B", logoUrl = null, position = 1,
                )
                repos.transactions.insertImportedTransaction(
                    accountId = sourceId,
                    type = TransactionType.EXPENSE,
                    amount = 100.0,
                    category = null,
                    description = "First transfer",
                    relatedAccountId = destinationId,
                    date = 1_000L,
                    reviewStatus = TransactionReviewStatus.DRAFT,
                    importBatch = "batch-1",
                )
                repos.transactions.insertImportedTransaction(
                    accountId = destinationId,
                    type = TransactionType.INCOME,
                    amount = 100.0,
                    category = null,
                    description = "First transfer",
                    relatedAccountId = sourceId,
                    date = 1_000L,
                    reviewStatus = TransactionReviewStatus.DRAFT,
                    importBatch = "batch-1",
                )
                repos.transactions.insertTransaction(
                    accountId = sourceId, type = TransactionType.EXPENSE, amount = 200.0,
                    category = null, description = "Second transfer", relatedAccountId = destinationId, date = 1_010L,
                )
                repos.transactions.insertTransaction(
                    accountId = destinationId, type = TransactionType.INCOME, amount = 200.0,
                    category = null, description = "Second transfer", relatedAccountId = sourceId, date = 1_010L,
                )
                val initialRows = repos.transactions.getAllTransactions().sortedBy { it.id }
                val deletedIds = initialRows.filter { it.amount == 100.0 }.map { it.id }
                val viewModel = AccountDetailViewModel(
                    accountId = sourceId,
                    accountRepository = repos.accounts,
                    transactionRepository = repos.transactions,
                    snapshotRepository = repos.snapshots,
                )

                viewModel.deleteTransaction(deletedIds.first())
                withTimeout(1_000) { viewModel.event.first { it == AccountDetailEvent.TransactionsDeleted(2) } }
                assertTrue(repos.transactions.getAllTransactions().all { it.amount == 200.0 })

                viewModel.undoLastTransactionDelete()
                withTimeout(1_000) { viewModel.event.first { it == AccountDetailEvent.Restored(2) } }

                val restoredRows = repos.transactions.getAllTransactions().sortedBy { it.id }
                assertEquals(initialRows.map { it.id }, restoredRows.map { it.id })
                val restoredDrafts = restoredRows.filter { it.description == "First transfer" }
                assertEquals(2, restoredDrafts.size)
                assertTrue(restoredDrafts.all { it.reviewStatus == TransactionReviewStatus.DRAFT })
                assertTrue(restoredDrafts.all { it.importBatch == "batch-1" })
                assertEquals(
                    setOf(sourceId to destinationId, destinationId to sourceId),
                    restoredDrafts.map { it.accountId to it.relatedAccountId }.toSet(),
                )
            }
        } finally {
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }

    private fun inMemoryRepositories(): TestRepositories {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JoyufyDatabase.Schema.create(driver)
        val database = JoyufyDatabase(driver)
        return TestRepositories(
            driver = driver,
            accounts = AccountRepository(database),
            transactions = TransactionRepository(database),
            snapshots = InvestmentSnapshotRepository(database),
        )
    }

    private suspend fun awaitTransactions(
        repository: TransactionRepository,
        predicate: (List<Transaction>) -> Boolean,
    ): List<Transaction> = withTimeout(1_000) {
        while (true) {
            val rows = repository.getAllTransactions()
            if (predicate(rows)) return@withTimeout rows
            delay(10)
        }
        error("Unreachable")
    }

    private data class TestRepositories(
        private val driver: SqlDriver,
        val accounts: AccountRepository,
        val transactions: TransactionRepository,
        val snapshots: InvestmentSnapshotRepository,
    ) : AutoCloseable {
        override fun close() = driver.close()
    }
}
