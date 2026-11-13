package com.aracem.joyufy.ui.account

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAccountViewModelTest {
    @Test
    fun createsBankCashAndInvestmentAccountsWithExpectedInitialData() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            inMemoryRepositories().use { repos ->
                val types = listOf(AccountType.BANK, AccountType.CASH, AccountType.INVESTMENT)
                types.forEachIndexed { index, type ->
                    val viewModel = CreateAccountViewModel(
                        accountRepository = repos.accounts,
                        transactionRepository = repos.transactions,
                        snapshotRepository = repos.snapshots,
                    )
                    viewModel.onNameChange(type.name)
                    viewModel.onTypeChange(type)
                    if (type != AccountType.INVESTMENT) viewModel.onInitialBalanceChange("10,25")

                    val saved = CompletableDeferred<Unit>()
                    viewModel.save(existingCount = index) { saved.complete(Unit) }
                    withTimeout(1_000) { saved.await() }
                }

                val accounts = repos.accounts.getAllAccounts().sortedBy { it.position }
                assertEquals(types, accounts.map { it.type })
                assertEquals(2, repos.transactions.getAllTransactions().size)
                accounts.forEach { account ->
                    val transactions = repos.transactions.getTransactionsForAccount(account.id)
                    if (account.type == AccountType.INVESTMENT) {
                        assertEquals(emptyList(), transactions)
                    } else {
                        assertEquals(1, transactions.size)
                        assertEquals(TransactionType.INCOME, transactions.single().type)
                        assertEquals(10.25, transactions.single().amount)
                        assertEquals("Saldo inicial", transactions.single().category)
                    }
                }
                assertEquals(emptyList(), repos.snapshots.getAllSnapshots())
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
