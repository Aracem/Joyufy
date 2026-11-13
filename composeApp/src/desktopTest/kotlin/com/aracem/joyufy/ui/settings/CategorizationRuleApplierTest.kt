package com.aracem.joyufy.ui.settings

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.CategorizationRule
import com.aracem.joyufy.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class CategorizationRuleApplierTest {
    @Test
    fun onlyMatchingUncategorizedTransactionsAreUpdated() = runBlocking {
        inMemoryRepositories().use { repos ->
            val accountId = repos.accounts.insertAccount(
                name = "Bank",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
            )
            repos.transactions.insertTransaction(
                accountId, TransactionType.EXPENSE, 12.0, null, "Corner grocery store", null, 1L,
            )
            repos.transactions.insertTransaction(
                accountId, TransactionType.EXPENSE, 15.0, null, "Coffee shop", null, 2L,
            )
            repos.transactions.insertTransaction(
                accountId, TransactionType.EXPENSE, 22.0, "Food", "Grocery delivery", null, 3L,
            )

            val updated = applyCategorizationRules(
                listOf(
                    CategorizationRule(1L, "grocery", "Groceries"),
                    CategorizationRule(2L, "grocery", "Other"),
                ),
                repos.transactions,
            )

            assertEquals(1, updated)
            val categories = repos.transactions.getAllTransactions().associate { it.description to it.category }
            assertEquals("Groceries", categories["Corner grocery store"])
            assertEquals(null, categories["Coffee shop"])
            assertEquals("Food", categories["Grocery delivery"])
        }
    }

    private fun inMemoryRepositories(): TestRepositories {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JoyufyDatabase.Schema.create(driver)
        val database = JoyufyDatabase(driver)
        return TestRepositories(driver, AccountRepository(database), TransactionRepository(database))
    }

    private data class TestRepositories(
        private val driver: SqlDriver,
        val accounts: AccountRepository,
        val transactions: TransactionRepository,
    ) : AutoCloseable {
        override fun close() = driver.close()
    }
}
