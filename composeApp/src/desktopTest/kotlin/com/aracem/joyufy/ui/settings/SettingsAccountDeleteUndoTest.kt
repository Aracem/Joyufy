package com.aracem.joyufy.ui.settings

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.TransactionReviewStatus
import com.aracem.joyufy.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAccountDeleteUndoTest {
    @Test
    fun deletingInvestmentAccountAndUndoRestoresSnapshotAndLinkedTransferRows() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val investmentId = repos.accounts.insertAccount(
                    name = "Portfolio", type = AccountType.INVESTMENT,
                    colorHex = "#7B6EF6", logoUrl = null, position = 0,
                )
                val bankId = repos.accounts.insertAccount(
                    name = "Current", type = AccountType.BANK,
                    colorHex = "#34C77B", logoUrl = null, position = 1,
                )
                val batch = "investment-transfer-batch"
                repos.transactions.insertImportedTransaction(
                    accountId = bankId, type = TransactionType.EXPENSE, amount = 250.0,
                    category = "INVESTMENT", description = "Portfolio contribution",
                    relatedAccountId = investmentId, date = 1_000L,
                    reviewStatus = TransactionReviewStatus.DRAFT, importBatch = batch,
                )
                repos.transactions.insertImportedTransaction(
                    accountId = investmentId, type = TransactionType.INCOME, amount = 250.0,
                    category = "INVESTMENT", description = "Portfolio contribution",
                    relatedAccountId = bankId, date = 1_000L,
                    reviewStatus = TransactionReviewStatus.DRAFT, importBatch = batch,
                )
                repos.snapshots.insertSnapshot(
                    accountId = investmentId, totalValue = 5_000.0, weekDate = 2_000L,
                    deposits = 250.0, withdrawals = 25.0, fees = 5.0, dividends = 10.0, note = "Quarterly review",
                )
                val originalRows = repos.transactions.getAllTransactions().sortedBy { it.id }
                val originalSnapshot = repos.snapshots.getSnapshotsForAccount(investmentId).single()
                val originalAccount = repos.accounts.getAccountById(investmentId)
                val viewModel = SettingsViewModel(repos.accounts, repos.transactions, repos.snapshots)

                viewModel.deleteAccount(investmentId)
                withTimeout(1_000) { viewModel.event.first { it == SettingsEvent.AccountDeleted("Portfolio") } }
                assertEquals(null, repos.accounts.getAccountById(investmentId))
                assertEquals(1, repos.transactions.getAllTransactions().size)
                assertEquals(0, repos.snapshots.getAllSnapshots().size)

                viewModel.undoLastAccountDelete()
                withTimeout(1_000) { viewModel.event.first { it == SettingsEvent.Restored("Portfolio") } }

                assertEquals(originalAccount, repos.accounts.getAccountById(investmentId))
                assertEquals(originalRows, repos.transactions.getAllTransactions().sortedBy { it.id })
                assertEquals(listOf(originalSnapshot), repos.snapshots.getSnapshotsForAccount(investmentId))
                val restoredRows = repos.transactions.getAllTransactions()
                assertEquals(
                    setOf(bankId to investmentId, investmentId to bankId),
                    restoredRows.map { it.accountId to it.relatedAccountId }.toSet(),
                )
                assertEquals(2, restoredRows.count { it.reviewStatus == TransactionReviewStatus.DRAFT && it.importBatch == batch })
            }
        } finally {
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }

    private fun inMemoryRepositories(): TestRepositories {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JoyufyDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
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
