package com.aracem.joyufy.ui.backup

import com.aracem.joyufy.data.repository.BackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupEvent {
    data object Idle : BackupEvent
    data object Exporting : BackupEvent
    data object Importing : BackupEvent
    data class ExportReady(val json: String) : BackupEvent   // ready to show file picker
    data class ImportReady(val onConfirm: () -> Unit) : BackupEvent  // ready to show confirm dialog
    data class Success(val message: String) : BackupEvent
    data class Error(val message: String) : BackupEvent
}

class BackupViewModel(private val backupRepository: BackupRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _event = MutableStateFlow<BackupEvent>(BackupEvent.Idle)
    val event: StateFlow<BackupEvent> = _event.asStateFlow()

    fun requestExport() {
        _event.value = BackupEvent.Exporting
        scope.launch {
            runCatching { backupRepository.export() }
                .onSuccess { _event.value = BackupEvent.ExportReady(it) }
                .onFailure { _event.value = BackupEvent.Error("Error al exportar: ${it.message}") }
        }
    }

    fun importFromJson(json: String) {
        _event.value = BackupEvent.ImportReady {
            scope.launch {
                _event.value = BackupEvent.Importing
                runCatching { backupRepository.import(json) }
                    .onSuccess { _event.value = BackupEvent.Success("Datos restaurados correctamente") }
                    .onFailure { _event.value = BackupEvent.Error("Error al importar: ${it.message}") }
            }
        }
    }

    fun reset() {
        _event.value = BackupEvent.Idle
    }
}
