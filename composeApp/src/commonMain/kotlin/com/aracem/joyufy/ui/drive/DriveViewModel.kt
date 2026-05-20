package com.aracem.joyufy.ui.drive

import com.aracem.joyufy.data.cloud.AuthState
import com.aracem.joyufy.data.cloud.GoogleDriveRepository
import com.aracem.joyufy.data.repository.BackupRepository
import com.aracem.joyufy.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DriveUiState(
    val authState: AuthState = AuthState.Unauthenticated,
    val autoSync: Boolean = true,
    val lastSyncAt: Long = 0L,
)

sealed interface DriveEvent {
    data object Idle : DriveEvent
    data object Uploading : DriveEvent
    data object Downloading : DriveEvent
    data class Success(val message: String) : DriveEvent
    data class Error(val message: String) : DriveEvent
}

class DriveViewModel(
    private val driveRepo: GoogleDriveRepository,
    private val backupRepo: BackupRepository,
    private val prefs: PreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _event = MutableStateFlow<DriveEvent>(DriveEvent.Idle)
    val event: StateFlow<DriveEvent> = _event.asStateFlow()

    val uiState: StateFlow<DriveUiState> = driveRepo.authState
        .map { authState ->
            DriveUiState(
                authState = authState,
                autoSync = prefs.getDriveAutoSync(),
                lastSyncAt = prefs.getDriveLastSyncAt(),
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, DriveUiState(
            authState = driveRepo.authState.value,
            autoSync = prefs.getDriveAutoSync(),
            lastSyncAt = prefs.getDriveLastSyncAt(),
        ))

    fun signIn() {
        scope.launch { driveRepo.signIn() }
    }

    fun signOut() {
        scope.launch { driveRepo.signOut() }
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.setDriveAutoSync(enabled)
        // Trigger recomposition by emitting a neutral event
        _event.value = DriveEvent.Idle
    }

    fun syncToCloud(silent: Boolean = false) {
        scope.launch {
            _event.value = DriveEvent.Uploading
            val json = runCatching { backupRepo.export() }.getOrElse {
                _event.value = DriveEvent.Error(it.message ?: "Export error")
                return@launch
            }
            driveRepo.upload(json).fold(
                onSuccess = {
                    prefs.setDriveLastSyncAt(System.currentTimeMillis())
                    _event.value = if (silent) DriveEvent.Idle else DriveEvent.Success("drive_upload_ok")
                },
                onFailure = {
                    _event.value = if (silent) DriveEvent.Idle else DriveEvent.Error(it.message ?: "Upload error")
                },
            )
        }
    }

    fun syncFromCloud(silent: Boolean = false) {
        scope.launch {
            _event.value = DriveEvent.Downloading
            driveRepo.download().fold(
                onSuccess = { json ->
                    runCatching { backupRepo.import(json) }.fold(
                        onSuccess = {
                            prefs.setDriveLastSyncAt(System.currentTimeMillis())
                            _event.value = if (silent) DriveEvent.Idle else DriveEvent.Success("drive_download_ok")
                        },
                        onFailure = {
                            _event.value = if (silent) DriveEvent.Idle else DriveEvent.Error(it.message ?: "Import error")
                        },
                    )
                },
                onFailure = {
                    // "No backup file found" on first use is not an error worth surfacing
                    _event.value = DriveEvent.Idle
                },
            )
        }
    }

    fun shouldAutoSync(): Boolean =
        prefs.getDriveAutoSync() && driveRepo.authState.value is AuthState.Authenticated

    suspend fun syncToCloudSuspend() {
        val json = runCatching { backupRepo.export() }.getOrElse { return }
        driveRepo.upload(json).onSuccess {
            prefs.setDriveLastSyncAt(System.currentTimeMillis())
        }
    }

    fun reset() {
        _event.value = DriveEvent.Idle
    }
}
