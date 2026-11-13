package com.aracem.joyufy.data.db

import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.TransactionType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class DatabaseDriverFactoryTest {
    @Test
    fun factoryEnablesForeignKeysForTransferAccountDeletion() {
        val originalUserHome = System.getProperty("user.home")
        val isolatedHome = Files.createTempDirectory("joyufy-driver-factory-test").toFile()
        System.setProperty("user.home", isolatedHome.absolutePath)

        try {
            val driver = DatabaseDriverFactory().createDriver()
            try {
                val database = JoyufyDatabase(driver)
                val accounts = AccountRepository(database)
                val transactions = TransactionRepository(database)
                val bankId = 1L
                val cashId = 2L
                driver.execute(
                    null,
                    "INSERT INTO Account (id, name, type, color_hex, position, created_at) VALUES ($bankId, 'Bank', '${AccountType.BANK.name}', '#345D9C', 0, 1)",
                    0,
                )
                driver.execute(
                    null,
                    "INSERT INTO Account (id, name, type, color_hex, position, created_at) VALUES ($cashId, 'Cash', '${AccountType.CASH.name}', '#34C77B', 1, 1)",
                    0,
                )
                runBlocking {
                    transactions.insertTransaction(
                        accountId = bankId,
                        type = TransactionType.EXPENSE,
                        amount = 8.5,
                        category = null,
                        description = "Transfer",
                        relatedAccountId = cashId,
                        date = 1L,
                    )
                    transactions.insertTransaction(
                        accountId = cashId,
                        type = TransactionType.INCOME,
                        amount = 8.5,
                        category = null,
                        description = "Transfer",
                        relatedAccountId = bankId,
                        date = 1L,
                    )
                    accounts.deleteAccount(cashId)
                }

                val remaining = runBlocking { transactions.getAllTransactions() }
                assertEquals(1, remaining.size)
                assertEquals(null, remaining.single().relatedAccountId)
                assertEquals(bankId, remaining.single().accountId)
            } finally {
                driver.close()
            }
        } finally {
            if (originalUserHome == null) System.clearProperty("user.home")
            else System.setProperty("user.home", originalUserHome)
            isolatedHome.deleteRecursively()
        }
    }
}
