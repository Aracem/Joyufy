package com.aracem.joyufy.ui.account

import androidx.compose.ui.graphics.Color
import com.aracem.joyufy.data.mapper.toColorHex
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.ui.theme.AccountPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateAccountUiState(
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val selectedColor: Color = AccountPalette.first(),
    val isSaving: Boolean = false,
    val nameError: String? = null,
)

class CreateAccountViewModel(
    private val accountRepository: AccountRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(CreateAccountUiState())
    val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, nameError = null)
    }

    fun onTypeChange(value: AccountType) {
        _uiState.value = _uiState.value.copy(type = value)
    }

    fun onColorChange(value: Color) {
        _uiState.value = _uiState.value.copy(selectedColor = value)
    }

    fun save(
        existingCount: Int,
        onSuccess: () -> Unit,
    ) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.value = state.copy(nameError = "El nombre no puede estar vacío")
            return
        }
        _uiState.value = state.copy(isSaving = true)
        scope.launch {
            accountRepository.insertAccount(
                name = state.name.trim(),
                type = state.type,
                colorHex = dummyAccount(state.selectedColor).toColorHex(),
                position = existingCount,
            )
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSuccess()
        }
    }

    fun reset() {
        _uiState.value = CreateAccountUiState()
    }

    // Helper to reuse existing toColorHex mapper which expects an Account
    private fun dummyAccount(color: Color) = Account(
        id = 0, name = "", type = AccountType.BANK,
        color = color, position = 0, createdAt = 0,
    )
}
