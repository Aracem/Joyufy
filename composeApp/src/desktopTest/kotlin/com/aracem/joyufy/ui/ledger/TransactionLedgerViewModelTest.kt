package com.aracem.joyufy.ui.ledger

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
class TransactionLedgerViewModelTest {
    @Test
    fun bulkDeleteRemovesSelectedTransferPairAndUndoRestoresReviewMetadata() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val (sourceId, destinationId) = createAccounts(repos)
                val batch = "import-batch"
                repos.transactions.insertImportedTransaction(
                    accountId = sourceId, type = TransactionType.EXPENSE, amount = 50.0,
                    category = null, description = "First transfer", relatedAccountId = destinationId,
                    date = 1_000L, reviewStatus = TransactionReviewStatus.DRAFT, importBatch = batch,
                )
                repos.transactions.insertImportedTransaction(
                    accountId = destinationId, type = TransactionType.INCOME, amount = 50.0,
                    category = null, description = "First transfer", relatedAccountId = sourceId,
                    date = 1_000L, reviewStatus = TransactionReviewStatus.DRAFT, importBatch = batch,
                )
                repos.transactions.insertTransaction(
                    accountId = sourceId, type = TransactionType.EXPENSE, amount = 80.0,
                    category = null, description = "Other transfer", relatedAccountId = destinationId, date = 2_000L,
                )
                repos.transactions.insertTransaction(
                    accountId = destinationId, type = TransactionType.INCOME, amount = 80.0,
                    category = null, description = "Other transfer", relatedAccountId = sourceId, date = 2_000L,
                )
                val firstPair = repos.transactions.getAllTransactions().filter { it.amount == 50.0 }
                val viewModel = TransactionLedgerViewModel(repos.accounts, repos.transactions, repos.snapshots)
                withTimeout(1_000) { viewModel.uiState.first { it.rows.size == 4 } }

                viewModel.toggleSelected(firstPair.first { it.type == TransactionType.EXPENSE }.id)
                viewModel.bulkDeleteSelected()
                withTimeout(1_000) { viewModel.event.first { it == LedgerEvent.Deleted(2) } }
                assertEquals(setOf("Other transfer"), repos.transactions.getAllTransactions().map { it.description }.toSet())

                viewModel.undoLastDelete()
                withTimeout(1_000) { viewModel.event.first { it == LedgerEvent.Restored(2) } }
                val restored = repos.transactions.getAllTransactions().filter { it.description == "First transfer" }
                assertEquals(firstPair.map { it.id }.toSet(), restored.map { it.id }.toSet())
                assertTrue(restored.all { it.reviewStatus == TransactionReviewStatus.DRAFT && it.importBatch == batch })
                assertEquals(
                    setOf(sourceId to destinationId, destinationId to sourceId),
                    restored.map { it.accountId to it.relatedAccountId }.toSet(),
                )
            }
        } finally {
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }

    @Test
    fun bulkMarkReviewedUpdatesSelectedDraftRows() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val (accountId, _) = createAccounts(repos)
                repos.transactions.insertImportedTransaction(
                    accountId = accountId, type = TransactionType.EXPENSE, amount = 15.0,
                    category = null, description = "Review me", relatedAccountId = null, date = 1_000L,
                    reviewStatus = TransactionReviewStatus.DRAFT, importBatch = "review-batch",
                )
                val transaction = repos.transactions.getTransactionsForAccount(accountId).single()
                val viewModel = TransactionLedgerViewModel(repos.accounts, repos.transactions, repos.snapshots)
                withTimeout(1_000) { viewModel.uiState.first { it.rows.size == 1 } }

                viewModel.toggleSelected(transaction.id)
                viewModel.bulkMarkReviewed()
                withTimeout(1_000) { viewModel.event.first { it == LedgerEvent.Updated(1) } }

                val reviewed = awaitTransactions(repos.transactions) { rows ->
                    rows.singleOrNull()?.reviewStatus == TransactionReviewStatus.REVIEWED
                }.single()
                assertEquals("review-batch", reviewed.importBatch)
            }
        } finally {
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }

    private suspend fun createAccounts(repos: TestRepositories): Pair<Long, Long> {
        val sourceId = repos.accounts.insertAccount(
            name = "Current", type = AccountType.BANK, colorHex = "#7B6EF6", logoUrl = null, position = 0,
        )
        val destinationId = repos.accounts.insertAccount(
            name = "Savings", type = AccountType.BANK, colorHex = "#34C77B", logoUrl = null, position = 1,
        )
        return sourceId to destinationId
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

    private data class TestRepositories(
        private val driver: SqlDriver,
        val accounts: AccountRepository,
        val transactions: TransactionRepository,
        val snapshots: InvestmentSnapshotRepository,
    ) : AutoCloseable {
        override fun close() = driver.close()
    }
}
