package com.aracem.joyufy.data.repository

import java.util.prefs.Preferences

class PreferencesRepository {
    private val prefs: Preferences = Preferences.userRoot().node("com/aracem/joyufy")

    fun getDarkMode(): Boolean = prefs.getBoolean("dark_mode", true)

    fun setDarkMode(value: Boolean) {
        prefs.putBoolean("dark_mode", value)
    }

    fun getAnalysisExpanded(): Boolean = prefs.getBoolean("analysis_expanded", false)

    fun setAnalysisExpanded(value: Boolean) {
        prefs.putBoolean("analysis_expanded", value)
    }

    fun getLanguage(): String = prefs.get("language", "")

    fun setLanguage(value: String) {
        prefs.put("language", value)
    }

    fun getDriveAccessToken(): String = prefs.get("drive_access_token", "")
    fun setDriveAccessToken(value: String) { prefs.put("drive_access_token", value) }

    fun getDriveRefreshToken(): String = prefs.get("drive_refresh_token", "")
    fun setDriveRefreshToken(value: String) { prefs.put("drive_refresh_token", value) }

    fun getDriveTokenExpiry(): Long = prefs.getLong("drive_token_expiry", 0L)
    fun setDriveTokenExpiry(value: Long) { prefs.putLong("drive_token_expiry", value) }

    fun getDriveUserEmail(): String = prefs.get("drive_user_email", "")
    fun setDriveUserEmail(value: String) { prefs.put("drive_user_email", value) }

    fun getDriveAutoSync(): Boolean = prefs.getBoolean("drive_auto_sync", true)
    fun setDriveAutoSync(value: Boolean) { prefs.putBoolean("drive_auto_sync", value) }

    fun getDriveLastSyncAt(): Long = prefs.getLong("drive_last_sync_at", 0L)
    fun setDriveLastSyncAt(value: Long) { prefs.putLong("drive_last_sync_at", value) }
}
