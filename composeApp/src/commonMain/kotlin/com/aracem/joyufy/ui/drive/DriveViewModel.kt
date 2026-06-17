package com.aracem.joyufy.ui.drive

import com.aracem.joyufy.data.cloud.AuthState
import com.aracem.joyufy.data.cloud.GoogleDriveRepository
import com.aracem.joyufy.data.repository.BackupDiff
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
    /**
     * Cloud backup differs from local. UI shows a dialog with [diff] counts;
     * user choice triggers [onApply] (replace local) or just dismisses.
     */
    data class RestorePrompt(val diff: BackupDiff, val rawJson: String) : DriveEvent
}

class DriveViewModel(
    private val driveRepo: GoogleDriveRepository,
    private val backupRepo: BackupRepository,
    private val prefs: PreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _event = MutableStateFlow<DriveEvent>(DriveEvent.Idle)
    val event: StateFlow<DriveEvent> = _event.asStateFlow()

    /** True while the close-time upload is in flight. Read by App.kt to render the overlay. */
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

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
        _isSyncing.value = true
        try {
            val json = runCatching { backupRepo.export() }.getOrElse { return }
            driveRepo.upload(json).onSuccess {
                prefs.setDriveLastSyncAt(System.currentTimeMillis())
            }
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Used at app launch: download the cloud backup, diff against local, and
     * — only if there are differences — emit a [DriveEvent.RestorePrompt] so
     * the UI can ask the user whether to replace local with cloud. Silent
     * otherwise (no backup file, no changes, or errors that aren't worth
     * surfacing to the user mid-launch).
     */
    fun previewFromCloud() {
        scope.launch {
            val json = driveRepo.download().getOrNull() ?: return@launch
            val diff = runCatching { backupRepo.diffAgainstLocal(json) }.getOrNull() ?: return@launch
            if (diff.hasChanges) {
                _event.value = DriveEvent.RestorePrompt(diff, json)
            }
        }
    }

    /**
     * Applies a previously-previewed cloud backup. Called after the user
     * confirms the restore dialog. Updates last-sync timestamp on success.
     */
    fun applyCloudBackup(json: String, onDone: () -> Unit = {}) {
        scope.launch {
            runCatching { backupRepo.import(json) }
                .onSuccess {
                    prefs.setDriveLastSyncAt(System.currentTimeMillis())
                    _event.value = DriveEvent.Success("drive_download_ok")
                }
                .onFailure { _event.value = DriveEvent.Error(it.message ?: "Import error") }
            onDone()
        }
    }

    fun reset() {
        _event.value = DriveEvent.Idle
    }
}
