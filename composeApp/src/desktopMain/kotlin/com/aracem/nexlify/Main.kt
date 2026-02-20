package com.aracem.nexlify

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aracem.nexlify.di.initKoin
import com.aracem.nexlify.ui.App

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Nexlify",
        ) {
            App()
        }
    }
}
