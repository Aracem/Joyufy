package com.aracem.joyufy.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.account.CreateAccountViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class JoyufyDataIntegrityTest {

    @Test
    fun backupImportPreservesIdsAndRemovesExistingRows() = runTest {
        inMemoryRepositories().use { repos ->
            repos.accounts.insertAccountWithId(
                id = 999L,
                name = "Archived legacy",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
                createdAt = 1L,
            )
            repos.accounts.archiveAccount(999L)

            val backup = JoyufyBackup(
                exportedAt = 123_456L,
                accounts = listOf(
                    AccountBackup(
                        id = 41L,
                        name = "Bank",
                        type = AccountType.BANK.name,
                        colorHex = "#7B6EF6",
                        position = 0,
                        createdAt = 10L,
                    ),
                    AccountBackup(
                        id = 42L,
                        name = "Investment",
                        type = AccountType.INVESTMENT.name,
                        colorHex = "#34C77B",
                        position = 1,
                        createdAt = 11L,
                    ),
                ),
                transactions = listOf(
                    TransactionBackup(
                        id = 91L,
                        accountId = 41L,
                        type = TransactionType.INCOME.name,
                        amount = 1_000.0,
                        category = "Salary",
                        date = 20L,
                    ),
                ),
                snapshots = listOf(
                    SnapshotBackup(
                        id = 92L,
                        accountId = 42L,
                        totalValue = 5_000.0,
                        weekDate = 30L,
                    ),
                ),
            )
            val json = Json.encodeToString(backup)

            repos.backups.import(json)

            assertNull(repos.accounts.getAccountById(999L))
            assertEquals(listOf(41L, 42L), repos.accounts.getAllAccounts().map { it.id })
            assertEquals(91L, repos.transactions.getAllTransactions().single().id)
            assertEquals(92L, repos.snapshots.getAllSnapshots().single().id)
            assertFalse(repos.backups.diffAgainstLocal(json).hasChanges)
        }
    }

    @Test
    fun bankToInvestmentTypeChangeCollapsesBalanceIntoSnapshot() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val accountId = repos.accounts.insertAccount(
                    name = "Main bank",
                    type = AccountType.BANK,
                    colorHex = "#7B6EF6",
                    logoUrl = null,
                    position = 0,
                )
                repos.transactions.insertTransaction(
                    accountId = accountId,
                    type = TransactionType.INCOME,
                    amount = 1_000.0,
                    category = null,
                    description = null,
                    relatedAccountId = null,
                    date = 10L,
                )
                repos.transactions.insertTransaction(
                    accountId = accountId,
                    type = TransactionType.EXPENSE,
                    amount = 250.0,
                    category = null,
                    description = null,
                    relatedAccountId = null,
                    date = 11L,
                )

                val account = assertNotNull(repos.accounts.getAccountById(accountId))
                val viewModel = CreateAccountViewModel(
                    accountRepository = repos.accounts,
                    transactionRepository = repos.transactions,
                    snapshotRepository = repos.snapshots,
                )
                viewModel.resetForEdit(account)
                viewModel.onTypeChange(AccountType.INVESTMENT)
                assertEquals("Main bank", viewModel.uiState.value.name)
                assertEquals(AccountType.INVESTMENT, viewModel.uiState.value.type)

                val completed = CompletableDeferred<Unit>()
                viewModel.saveEdit(account) { completed.complete(Unit) }

                withTimeout(1_000) { completed.await() }
                assertEquals(AccountType.INVESTMENT, repos.accounts.getAccountById(accountId)?.type)
                assertTrue(repos.transactions.getAllTransactions().filter { it.accountId == accountId }.isEmpty())
                assertEquals(750.0, repos.snapshots.getAllSnapshots().single().totalValue)
            }
        } finally {
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }

    @Test
    fun transferSiblingLookupUsesAmountAndOppositeType() = runTest {
        inMemoryRepositories().use { repos ->
            val originId = repos.accounts.insertAccount(
                name = "Origin",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
            )
            val destinationId = repos.accounts.insertAccount(
                name = "Destination",
                type = AccountType.CASH,
                colorHex = "#34C77B",
                logoUrl = null,
                position = 1,
            )

            repos.insertTransfer(originId, destinationId, amount = 100.0, date = 1_000L)
            repos.insertTransfer(originId, destinationId, amount = 200.0, date = 1_010L)

            val sister = repos.transactions.getRelatedTransfer(
                relatedAccountId = destinationId,
                originAccountId = originId,
                amount = 200.0,
                type = TransactionType.INCOME,
                date = 1_010L,
            )

            assertNotNull(sister)
            assertEquals(destinationId, sister.accountId)
            assertEquals(originId, sister.relatedAccountId)
            assertEquals(TransactionType.INCOME, sister.type)
            assertEquals(200.0, sister.amount)
            assertEquals(1_010L, sister.date)

            assertNull(
                repos.transactions.getRelatedTransfer(
                    relatedAccountId = destinationId,
                    originAccountId = originId,
                    amount = 150.0,
                    type = TransactionType.INCOME,
                    date = 1_010L,
                )
            )
        }
    }

    @Test
    fun totalWealthUsesActiveBankCashBalancesAndLatestInvestmentSnapshots() = runTest {
        inMemoryRepositories().use { repos ->
            val bankId = repos.accounts.insertAccount(
                name = "Bank",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
            )
            val cashId = repos.accounts.insertAccount(
                name = "Cash",
                type = AccountType.CASH,
                colorHex = "#34C77B",
                logoUrl = null,
                position = 1,
            )
            val investmentId = repos.accounts.insertAccount(
                name = "Investment",
                type = AccountType.INVESTMENT,
                colorHex = "#F25C5C",
                logoUrl = null,
                position = 2,
            )
            val archivedId = repos.accounts.insertAccount(
                name = "Archived",
                type = AccountType.BANK,
                colorHex = "#242424",
                logoUrl = null,
                position = 3,
            )

            repos.transactions.insertTransaction(bankId, TransactionType.INCOME, 1_000.0, null, null, null, 10L)
            repos.transactions.insertTransaction(bankId, TransactionType.EXPENSE, 200.0, null, null, null, 11L)
            repos.transactions.insertTransaction(cashId, TransactionType.INCOME, 100.0, null, null, null, 12L)
            repos.transactions.insertTransaction(archivedId, TransactionType.INCOME, 9_999.0, null, null, null, 13L)
            repos.snapshots.insertSnapshot(investmentId, totalValue = 4_000.0, weekDate = 20L)
            repos.snapshots.insertSnapshot(investmentId, totalValue = 5_000.0, weekDate = 30L)
            repos.accounts.archiveAccount(archivedId)

            assertEquals(5_900.0, repos.wealth.getTotalWealth())
        }
    }

    private fun inMemoryRepositories(): TestRepositories {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JoyufyDatabase.Schema.create(driver)
        val database = JoyufyDatabase(driver)
        val accounts = AccountRepository(database)
        val transactions = TransactionRepository(database)
        val snapshots = InvestmentSnapshotRepository(database)
        return TestRepositories(
            driver = driver,
            accounts = accounts,
            transactions = transactions,
            snapshots = snapshots,
            backups = BackupRepository(accounts, transactions, snapshots),
            wealth = WealthRepository(database),
        )
    }

    private suspend fun TestRepositories.insertTransfer(
        originId: Long,
        destinationId: Long,
        amount: Double,
        date: Long,
    ) {
        transactions.insertTransaction(
            accountId = originId,
            type = TransactionType.EXPENSE,
            amount = amount,
            category = null,
            description = null,
            relatedAccountId = destinationId,
            date = date,
        )
        transactions.insertTransaction(
            accountId = destinationId,
            type = TransactionType.INCOME,
            amount = amount,
            category = null,
            description = null,
            relatedAccountId = originId,
            date = date,
        )
    }

    private data class TestRepositories(
        private val driver: SqlDriver,
        val accounts: AccountRepository,
        val transactions: TransactionRepository,
        val snapshots: InvestmentSnapshotRepository,
        val backups: BackupRepository,
        val wealth: WealthRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }
}
