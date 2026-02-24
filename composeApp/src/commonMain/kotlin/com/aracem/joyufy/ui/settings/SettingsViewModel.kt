package com.aracem.joyufy.ui.settings

import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.BackupRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.domain.model.Account
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

class SettingsViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts)
            }
        }
    }

    fun archiveAccount(id: Long) {
        scope.launch(Dispatchers.IO) { accountRepository.archiveAccount(id) }
    }

    fun deleteAccount(id: Long) {
        scope.launch(Dispatchers.IO) { accountRepository.deleteAccount(id) }
    }

    fun deleteAllData(onDone: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            transactionRepository.deleteAllTransactions()
            snapshotRepository.deleteAllSnapshots()
            accountRepository.getAllAccounts().forEach { accountRepository.deleteAccount(it.id) }
            onDone()
        }
    }
}
