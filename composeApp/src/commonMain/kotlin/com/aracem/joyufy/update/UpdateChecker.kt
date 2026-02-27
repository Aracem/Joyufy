package com.aracem.joyufy.update

/** Returns UpdateInfo if a newer version is available, null otherwise. */
expect suspend fun checkForUpdate(): UpdateInfo?
