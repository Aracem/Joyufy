package com.aracem.joyufy.data.repository

import java.util.prefs.Preferences

class PreferencesRepository {
    private val prefs: Preferences = Preferences.userRoot().node("com/aracem/joyufy")

    fun getDarkMode(): Boolean = prefs.getBoolean("dark_mode", true)

    fun setDarkMode(value: Boolean) {
        prefs.putBoolean("dark_mode", value)
    }
}
