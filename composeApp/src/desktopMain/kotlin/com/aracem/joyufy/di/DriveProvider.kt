package com.aracem.joyufy.di

import com.aracem.joyufy.data.cloud.GoogleDriveConfig
import com.aracem.joyufy.data.cloud.GoogleDriveRepository
import com.aracem.joyufy.data.cloud.GoogleDriveRepositoryImpl
import com.aracem.joyufy.data.repository.PreferencesRepository

actual fun provideDriveRepository(prefs: PreferencesRepository): GoogleDriveRepository =
    GoogleDriveRepositoryImpl(
        prefs = prefs,
        clientId = GoogleDriveConfig.CLIENT_ID,
        clientSecret = GoogleDriveConfig.CLIENT_SECRET,
    )
