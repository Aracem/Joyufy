package com.aracem.joyufy.ui.account

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
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
class AccountDetailSnapshotUndoTest {
    @Test
    fun editingSnapshotAnnotationsThenDeletingAndUndoingPreservesEveryFieldAndId() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val accountId = repos.accounts.insertAccount(
                    name = "Portfolio", type = AccountType.INVESTMENT,
                    colorHex = "#7B6EF6", logoUrl = null, position = 0,
                )
                repos.snapshots.insertSnapshot(
                    accountId = accountId,
                    totalValue = 1_000.0,
                    weekDate = 1_000L,
                    deposits = 100.0,
                    withdrawals = 10.0,
                    fees = 2.0,
                    dividends = 3.0,
                    note = "Initial note",
                )
                val initial = repos.snapshots.getSnapshotsForAccount(accountId).single()
                val viewModel = AccountDetailViewModel(
                    accountId = accountId,
                    accountRepository = repos.accounts,
                    transactionRepository = repos.transactions,
                    snapshotRepository = repos.snapshots,
                )
                withTimeout(1_000) { viewModel.uiState.first { it.snapshots.any { row -> row.id == initial.id } } }

                viewModel.updateSnapshot(
                    id = initial.id,
                    totalValue = 1_250.0,
                    weekDate = 2_000L,
                    deposits = 200.0,
                    withdrawals = 20.0,
                    fees = 4.0,
                    dividends = 6.0,
                    note = "Updated note",
                )
                val edited = withTimeout(1_000) {
                    while (true) {
                        val row = repos.snapshots.getSnapshotsForAccount(accountId).singleOrNull()
                        if (row != null && row.note == "Updated note") return@withTimeout row
                        delay(10)
                    }
                    error("Unreachable")
                }
                assertEquals(initial.id, edited.id)
                assertEquals(1_250.0, edited.totalValue)
                assertEquals(2_000L, edited.weekDate)
                assertEquals(200.0, edited.deposits)
                assertEquals(20.0, edited.withdrawals)
                assertEquals(4.0, edited.fees)
                assertEquals(6.0, edited.dividends)

                withTimeout(1_000) {
                    viewModel.uiState.first { state -> state.snapshots.any { it.id == initial.id && it.note == "Updated note" } }
                }
                viewModel.deleteSnapshot(initial.id)
                withTimeout(1_000) { viewModel.event.first { it == AccountDetailEvent.SnapshotDeleted(1) } }
                withTimeout(1_000) { viewModel.uiState.first { it.snapshots.isEmpty() } }

                viewModel.undoLastSnapshotDelete()
                withTimeout(1_000) { viewModel.event.first { it == AccountDetailEvent.Restored(1) } }
                val restored = repos.snapshots.getSnapshotsForAccount(accountId).single()
                assertEquals(edited, restored)
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

    private data class TestRepositories(
        private val driver: SqlDriver,
        val accounts: AccountRepository,
        val transactions: TransactionRepository,
        val snapshots: InvestmentSnapshotRepository,
    ) : AutoCloseable {
        override fun close() = driver.close()
    }
}
