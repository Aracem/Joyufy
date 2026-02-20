package com.aracem.nexlify.ui.account

import com.aracem.nexlify.data.repository.AccountRepository
import com.aracem.nexlify.data.repository.InvestmentSnapshotRepository
import com.aracem.nexlify.data.repository.TransactionRepository
import com.aracem.nexlify.domain.model.Account
import com.aracem.nexlify.domain.model.AccountType
import com.aracem.nexlify.domain.model.InvestmentSnapshot
import com.aracem.nexlify.domain.model.Transaction
import com.aracem.nexlify.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    val account: Account? = null,
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
    val snapshots: List<InvestmentSnapshot> = emptyList(),
    val allAccounts: List<Account> = emptyList(),
)

class AccountDetailViewModel(
    private val accountId: Long,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        scope.launch {
            val account = accountRepository.getAccountById(accountId) ?: return@launch
            _uiState.value = _uiState.value.copy(account = account)

            // Always observe all other accounts (for destination picker)
            launch {
                accountRepository.observeAccounts().collect { accounts ->
                    _uiState.value = _uiState.value.copy(
                        allAccounts = accounts.filter { it.id != accountId }
                    )
                }
            }

            // All account types observe transactions
            launch {
                transactionRepository.observeTransactionsForAccount(accountId).collect { txns ->
                    val balance = calculateBalance(account.type)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        balance = balance,
                        transactions = txns,
                    )
                }
            }

            // Investment accounts also observe snapshots
            if (account.type == AccountType.INVESTMENT) {
                snapshotRepository.observeSnapshotsForAccount(accountId).collect { snapshots ->
                    // Balance = latest snapshot if exists, otherwise sum of transactions
                    val balance = snapshots.firstOrNull()?.totalValue
                        ?: transactionRepository.getAccountBalance(accountId)
                    _uiState.value = _uiState.value.copy(
                        balance = balance,
                        snapshots = snapshots,
                    )
                }
            }
        }
    }

    private suspend fun calculateBalance(type: AccountType): Double =
        transactionRepository.getAccountBalance(accountId)

    fun addTransaction(
        type: TransactionType,
        amount: Double,
        category: String?,
        description: String?,
        relatedAccountId: Long?,
        date: Long,
    ) {
        scope.launch {
            when {
                // TRANSFER: EXPENSE on origin, INCOME on destination
                type == TransactionType.TRANSFER && relatedAccountId != null -> {
                    transactionRepository.insertTransaction(
                        accountId = accountId,
                        type = TransactionType.EXPENSE,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = relatedAccountId,
                        date = date,
                    )
                    transactionRepository.insertTransaction(
                        accountId = relatedAccountId,
                        type = TransactionType.INCOME,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = accountId,
                        date = date,
                    )
                }
                // INVESTMENT_DEPOSIT: EXPENSE on bank origin, INCOME on investment destination
                type == TransactionType.INVESTMENT_DEPOSIT && relatedAccountId != null -> {
                    transactionRepository.insertTransaction(
                        accountId = accountId,
                        type = TransactionType.EXPENSE,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = relatedAccountId,
                        date = date,
                    )
                    transactionRepository.insertTransaction(
                        accountId = relatedAccountId,
                        type = TransactionType.INCOME,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = accountId,
                        date = date,
                    )
                }
                else -> {
                    transactionRepository.insertTransaction(
                        accountId = accountId,
                        type = type,
                        amount = amount,
                        category = category,
                        description = description,
                        relatedAccountId = relatedAccountId,
                        date = date,
                    )
                }
            }
        }
    }

    fun addSnapshot(totalValue: Double) {
        scope.launch {
            snapshotRepository.insertSnapshot(
                accountId = accountId,
                totalValue = totalValue,
                weekDate = currentWeekStartMillis(),
            )
        }
    }

    fun deleteTransaction(id: Long) {
        scope.launch { transactionRepository.deleteTransaction(id) }
    }

    fun deleteSnapshot(id: Long) {
        scope.launch { snapshotRepository.deleteSnapshot(id) }
    }

    private fun currentWeekStartMillis(): Long {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayOfWeek = local.dayOfWeek.ordinal
        val millisInDay = 86_400_000L
        return (now.toEpochMilliseconds() / millisInDay - dayOfWeek) * millisInDay
    }
}
