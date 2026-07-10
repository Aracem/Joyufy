package com.aracem.joyufy.ui.importer

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.parseDateInputToMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
class CsvImportViewModelTest {

    @Test
    fun ingStatementParsesRowsWithBankCategoryAndDescription() = runBlockingWithMain {
        inMemoryRepositories().use { repos ->
            val accountId = repos.accounts.insertAccount(
                name = "ING",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
            )
            val viewModel = CsvImportViewModel(repos.accounts, repos.transactions)
            viewModel.setDefaultAccount(accountId)

            viewModel.loadCsv(ING_SAMPLE)

            val state = viewModel.awaitRows(count = 2)
            assertEquals(2, state.validCount)
            assertEquals(0, state.duplicateCount)

            val row = state.rows.first()
            assertEquals("07/07/2026", row.dateText)
            assertEquals("-30,00", row.amountText)
            assertEquals("Recibo ASOCIACION ESPANOLA CONTRA EL CANCER", row.description)
            assertEquals("Otros gastos · ONG", row.category)
        }
    }

    @Test
    fun ingSemicolonCsvWithExcelPreambleParsesRows() = runBlockingWithMain {
        inMemoryRepositories().use { repos ->
            val accountId = repos.accounts.insertAccount(
                name = "ING",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
            )
            val viewModel = CsvImportViewModel(repos.accounts, repos.transactions)
            viewModel.setDefaultAccount(accountId)

            viewModel.loadCsv(ING_SEMICOLON_SAMPLE)

            val state = viewModel.awaitRows(count = 2)
            assertEquals(2, state.validCount)
            assertEquals("07/07/2026", state.rows.first().dateText)
            assertEquals("-30,00", state.rows.first().amountText)
            assertEquals("Recibo ASOCIACION ESPANOLA CONTRA EL CANCER", state.rows.first().description)
            assertEquals("Otros gastos · ONG", state.rows.first().category)
            assertEquals("Hogar · Teléfono, TV e internet", state.rows.last().category)
        }
    }

    @Test
    fun duplicateExistingTransactionsAreBlockedBeforeImport() = runBlockingWithMain {
        inMemoryRepositories().use { repos ->
            val accountId = repos.accounts.insertAccount(
                name = "ING",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
            )
            repos.transactions.insertTransaction(
                accountId = accountId,
                type = TransactionType.EXPENSE,
                amount = 30.0,
                category = "Otros gastos · ONG",
                description = "Recibo ASOCIACION ESPANOLA CONTRA EL CANCER",
                relatedAccountId = null,
                date = requireNotNull(parseDateInputToMillis("07/07/2026")),
            )
            val viewModel = CsvImportViewModel(repos.accounts, repos.transactions)
            viewModel.setDefaultAccount(accountId)

            viewModel.loadCsv(ING_SAMPLE)

            val state = viewModel.awaitRows(count = 2)
            assertEquals(1, state.validCount)
            assertEquals(1, state.duplicateCount)
            assertFalse(state.rows.first().canCommit)
            assertTrue(state.rows.last().canCommit)

            viewModel.commitValidRows()
            viewModel.event.first { it is CsvImportEvent.Imported }

            val transactions = repos.transactions.getAllTransactions()
            assertEquals(2, transactions.size)
            assertEquals(1, transactions.count { it.description == "Recibo ASOCIACION ESPANOLA CONTRA EL CANCER" })
        }
    }

    @Test
    fun duplicateRowsInsideSameStatementAreBlocked() = runBlockingWithMain {
        inMemoryRepositories().use { repos ->
            val accountId = repos.accounts.insertAccount(
                name = "ING",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
            )
            val viewModel = CsvImportViewModel(repos.accounts, repos.transactions)
            viewModel.setDefaultAccount(accountId)

            viewModel.loadCsv(ING_SAMPLE + "\n" + ING_SAMPLE_ROW)

            val state = viewModel.awaitRows(count = 3)
            assertEquals(1, state.validCount)
            assertEquals(2, state.duplicateCount)
            assertFalse(state.rows[0].canCommit)
            assertTrue(state.rows[1].canCommit)
            assertFalse(state.rows[2].canCommit)
        }
    }

    @Test
    fun tsvStatementUsesGenericColumnMapping() = runBlockingWithMain {
        inMemoryRepositories().use { repos ->
            val accountId = repos.accounts.insertAccount(
                name = "Bank",
                type = AccountType.BANK,
                colorHex = "#7B6EF6",
                logoUrl = null,
                position = 0,
            )
            val viewModel = CsvImportViewModel(repos.accounts, repos.transactions)
            viewModel.setDefaultAccount(accountId)

            viewModel.loadCsv(
                """
                Fecha	Importe	Descripcion	Categoria
                01/07/2026	5,50	Nomina recibida	Nomina
                """.trimIndent()
            )

            val state = viewModel.awaitRows(count = 1)
            assertEquals(1, state.validCount)
            assertEquals("Nomina recibida", state.rows.single().description)
            assertEquals("Nomina", state.rows.single().category)
        }
    }

    private fun runBlockingWithMain(block: suspend () -> Unit) = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            Dispatchers.resetMain()
            dispatcher.close()
        }
    }

    private suspend fun CsvImportViewModel.awaitRows(count: Int): CsvImportUiState =
        withTimeout(1_000) { uiState.first { it.rows.size == count } }

    private fun inMemoryRepositories(): TestRepositories {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JoyufyDatabase.Schema.create(driver)
        val database = JoyufyDatabase(driver)
        return TestRepositories(
            driver = driver,
            accounts = AccountRepository(database),
            transactions = TransactionRepository(database),
        )
    }

    private data class TestRepositories(
        private val driver: SqlDriver,
        val accounts: AccountRepository,
        val transactions: TransactionRepository,
    ) : AutoCloseable {
        override fun close() {
            driver.close()
        }
    }

    private companion object {
        private val ING_SAMPLE_ROW = """
            07/07/2026
            Otros gastos
            ONG
            Recibo ASOCIACION ESPANOLA CONTRA EL CANCER

            -30,00
            638,89
        """.trimIndent()

        private val ING_SAMPLE = """
            Movimientos de la Cuenta

            F. VALOR
            CATEGORÍA
            SUBCATEGORÍA
            DESCRIPCIÓN
            COMENTARIO
            IMPORTE (€)
            SALDO (€)

            $ING_SAMPLE_ROW

            06/07/2026
            Hogar
            Teléfono, TV e internet
            Recibo Pepemobile, S.L.

            -51,90
            669,89
        """.trimIndent()

        private val ING_SEMICOLON_SAMPLE = """
            Tabla 1
            Movimientos de la Cuenta;;  Número de cuenta:;1465 0100 9917 13407121;;;;
            ;;  Titular:;MARCOS TRUJILLO SEOANE;;;;
            ;;  Fecha exportación:;10/07/2026 13:26h;;;;
            F. VALOR;CATEGORÍA;SUBCATEGORÍA;DESCRIPCIÓN;COMENTARIO;IMPORTE (€);SALDO (€);
            07/07/2026;Otros gastos;ONG;Recibo ASOCIACION ESPANOLA CONTRA EL CANCER;;-30,00;638,89;
            06/07/2026;Hogar;Teléfono, TV e internet;Recibo Pepemobile, S.L.;;-51,90;669,89;
        """.trimIndent()
    }
}
