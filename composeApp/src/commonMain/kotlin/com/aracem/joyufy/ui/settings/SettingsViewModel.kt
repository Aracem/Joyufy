package com.aracem.joyufy.ui.settings

import com.aracem.joyufy.data.mapper.toColorHex
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accounts: List<Account> = emptyList(),
)

sealed interface SettingsEvent {
    data object Idle : SettingsEvent
    data class AccountDeleted(val name: String) : SettingsEvent
    data class AccountArchived(val name: String) : SettingsEvent
    data class Restored(val name: String) : SettingsEvent
    data class Error(val message: String) : SettingsEvent
}

private data class DeletedAccountBackup(
    val account: Account,
    val transactions: List<Transaction>,
    val snapshots: List<InvestmentSnapshot>,
)

class SettingsViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _event = MutableStateFlow<SettingsEvent>(SettingsEvent.Idle)
    val event: StateFlow<SettingsEvent> = _event.asStateFlow()

    private var lastDeletedAccount: DeletedAccountBackup? = null
    private var lastArchivedAccount: Account? = null

    init {
        scope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts)
            }
        }
    }

    fun archiveAccount(id: Long) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val account = accountRepository.getAccountById(id) ?: return@runCatching null
                accountRepository.archiveAccount(id)
                account
            }.onSuccess { account ->
                if (account != null) {
                    lastArchivedAccount = account
                    _event.value = SettingsEvent.AccountArchived(account.name)
                }
            }.onFailure {
                _event.value = SettingsEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun deleteAccount(id: Long) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val account = accountRepository.getAccountById(id) ?: return@runCatching null
                val transactions = transactionRepository.getAllTransactions()
                    .filter { it.accountId == id || it.relatedAccountId == id }
                val snapshots = snapshotRepository.getSnapshotsForAccount(id)
                accountRepository.deleteAccount(id)
                DeletedAccountBackup(account, transactions, snapshots)
            }.onSuccess { backup ->
                if (backup != null) {
                    lastDeletedAccount = backup
                    _event.value = SettingsEvent.AccountDeleted(backup.account.name)
                }
            }.onFailure {
                _event.value = SettingsEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun undoLastAccountDelete() {
        val backup = lastDeletedAccount ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                accountRepository.insertAccountWithId(
                    id = backup.account.id,
                    name = backup.account.name,
                    type = backup.account.type,
                    colorHex = backup.account.toColorHex(),
                    logoUrl = backup.account.logoUrl,
                    position = backup.account.position,
                    createdAt = backup.account.createdAt,
                )
                backup.transactions.sortedBy { it.id }.forEach { tx ->
                    if (transactionRepository.getTransactionById(tx.id) == null) {
                        transactionRepository.insertTransactionWithId(
                            id = tx.id,
                            accountId = tx.accountId,
                            type = tx.type,
                            amount = tx.amount,
                            category = tx.category,
                            description = tx.description,
                            relatedAccountId = tx.relatedAccountId,
                            date = tx.date,
                            reviewStatus = tx.reviewStatus,
                            importBatch = tx.importBatch,
                        )
                    } else {
                        transactionRepository.updateTransaction(
                            id = tx.id,
                            type = tx.type,
                            amount = tx.amount,
                            category = tx.category,
                            description = tx.description,
                            relatedAccountId = tx.relatedAccountId,
                            date = tx.date,
                        )
                        transactionRepository.updateTransactionReviewStatus(tx.id, tx.reviewStatus)
                    }
                }
                backup.snapshots.sortedBy { it.id }.forEach { snapshot ->
                    snapshotRepository.insertSnapshotWithId(
                        id = snapshot.id,
                        accountId = snapshot.accountId,
                        totalValue = snapshot.totalValue,
                        weekDate = snapshot.weekDate,
                    )
                }
                backup.account.name
            }.onSuccess { name ->
                lastDeletedAccount = null
                _event.value = SettingsEvent.Restored(name)
            }.onFailure {
                _event.value = SettingsEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun undoLastAccountArchive() {
        val account = lastArchivedAccount ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                accountRepository.unarchiveAccount(account.id)
                account.name
            }.onSuccess { name ->
                lastArchivedAccount = null
                _event.value = SettingsEvent.Restored(name)
            }.onFailure {
                _event.value = SettingsEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun resetEvent() {
        _event.value = SettingsEvent.Idle
    }

    fun deleteAllData(onDone: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            transactionRepository.deleteAllTransactions()
            snapshotRepository.deleteAllSnapshots()
            accountRepository.deleteAllAccounts()
            onDone()
        }
    }
}
