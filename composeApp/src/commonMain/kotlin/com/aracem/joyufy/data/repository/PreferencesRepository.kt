package com.aracem.joyufy.data.repository

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.prefs.Preferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

class PreferencesRepository {

    private val file: File = preferencesFile()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val lock = Any()
    private var data: MutableMap<String, JsonElement> = loadOrMigrate()

    fun getDarkMode(): Boolean = getBoolean("dark_mode", true)
    fun setDarkMode(value: Boolean) = putBoolean("dark_mode", value)

    fun getAnalysisExpanded(): Boolean = getBoolean("analysis_expanded", false)
    fun setAnalysisExpanded(value: Boolean) = putBoolean("analysis_expanded", value)

    fun getLanguage(): String = getString("language", "")
    fun setLanguage(value: String) = putString("language", value)

    fun getDriveAccessToken(): String = getString("drive_access_token", "")
    fun setDriveAccessToken(value: String) = putString("drive_access_token", value)

    fun getDriveRefreshToken(): String = getString("drive_refresh_token", "")
    fun setDriveRefreshToken(value: String) = putString("drive_refresh_token", value)

    fun getDriveTokenExpiry(): Long = getLong("drive_token_expiry", 0L)
    fun setDriveTokenExpiry(value: Long) = putLong("drive_token_expiry", value)

    fun getDriveUserEmail(): String = getString("drive_user_email", "")
    fun setDriveUserEmail(value: String) = putString("drive_user_email", value)

    fun getDriveAutoSync(): Boolean = getBoolean("drive_auto_sync", true)
    fun setDriveAutoSync(value: Boolean) = putBoolean("drive_auto_sync", value)

    fun getDriveLastSyncAt(): Long = getLong("drive_last_sync_at", 0L)
    fun setDriveLastSyncAt(value: Long) = putLong("drive_last_sync_at", value)

    private fun getString(key: String, default: String): String = synchronized(lock) {
        (data[key] as? JsonPrimitive)?.contentOrNull ?: default
    }

    private fun putString(key: String, value: String) = synchronized(lock) {
        data[key] = JsonPrimitive(value)
        persist()
    }

    private fun getBoolean(key: String, default: Boolean): Boolean = synchronized(lock) {
        (data[key] as? JsonPrimitive)?.booleanOrNull ?: default
    }

    private fun putBoolean(key: String, value: Boolean) = synchronized(lock) {
        data[key] = JsonPrimitive(value)
        persist()
    }

    private fun getLong(key: String, default: Long): Long = synchronized(lock) {
        (data[key] as? JsonPrimitive)?.longOrNull ?: default
    }

    private fun putLong(key: String, value: Long) = synchronized(lock) {
        data[key] = JsonPrimitive(value)
        persist()
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val obj = JsonObject(data)
        val text = json.encodeToString(JsonObject.serializer(), obj)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun loadOrMigrate(): MutableMap<String, JsonElement> {
        if (file.exists()) {
            return runCatching {
                json.parseToJsonElement(file.readText()).jsonObject.toMutableMap()
            }.getOrElse { mutableMapOf() }
        }
        // Migrate from legacy java.util.prefs.Preferences if present
        val legacy = runCatching { migrateFromLegacyPrefs() }.getOrNull()
        if (legacy != null && legacy.isNotEmpty()) {
            val map = legacy.toMutableMap()
            data = map
            persist()
            return map
        }
        return mutableMapOf()
    }

    private fun migrateFromLegacyPrefs(): Map<String, JsonElement> {
        val legacy = Preferences.userRoot().node("com/aracem/joyufy")
        val keys = legacy.keys()
        if (keys.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, JsonElement>()
        // Strings
        listOf("language", "drive_access_token", "drive_refresh_token", "drive_user_email")
            .forEach { k ->
                val v = legacy.get(k, null)
                if (v != null) out[k] = JsonPrimitive(v)
            }
        // Longs
        listOf("drive_token_expiry", "drive_last_sync_at").forEach { k ->
            if (keys.contains(k)) out[k] = JsonPrimitive(legacy.getLong(k, 0L))
        }
        // Booleans
        listOf("dark_mode" to true, "analysis_expanded" to false, "drive_auto_sync" to true)
            .forEach { (k, default) ->
                if (keys.contains(k)) out[k] = JsonPrimitive(legacy.getBoolean(k, default))
            }
        return out
    }

    private fun preferencesFile(): File {
        val dir = appDataDir()
        return File(dir, "preferences.json")
    }

    private fun appDataDir(): File {
        val os = System.getProperty("os.name").lowercase(Locale.US)
        val home = System.getProperty("user.home")
        val base = when {
            os.contains("mac") -> File(home, "Library/Application Support/Joyufy")
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) File(appData, "Joyufy") else File(home, "Joyufy")
            }
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")
                if (!xdg.isNullOrBlank()) File(xdg, "Joyufy") else File(home, ".config/Joyufy")
            }
        }
        base.mkdirs()
        return base
    }
}
