package com.aracem.joyufy

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aracem.joyufy.di.initKoin
import com.aracem.joyufy.ui.App

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Joyufy",
        ) {
            App()
        }
    }
}
