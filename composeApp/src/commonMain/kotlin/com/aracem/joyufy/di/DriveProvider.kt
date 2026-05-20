package com.aracem.joyufy.di

import com.aracem.joyufy.data.cloud.GoogleDriveRepository
import com.aracem.joyufy.data.repository.PreferencesRepository

expect fun provideDriveRepository(prefs: PreferencesRepository): GoogleDriveRepository
