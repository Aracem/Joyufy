package com.aracem.joyufy

import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aracem.joyufy.di.initKoin
import com.aracem.joyufy.ui.App
import com.aracem.joyufy.ui.drive.DriveViewModel
import java.awt.Taskbar
import java.awt.Toolkit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext

fun main() {
    initKoin()
    setAppIcon()
    application {
        Window(
            onCloseRequest = {
                val driveViewModel: DriveViewModel = GlobalContext.get().get()
                if (driveViewModel.shouldAutoSync()) {
                    runBlocking {
                        withTimeoutOrNull(5_000) { driveViewModel.syncToCloudSuspend() }
                    }
                }
                exitApplication()
            },
            title = "Joyufy",
            icon = loadWindowIcon(),
        ) {
            App()
        }
    }
}

private fun setAppIcon() {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png") ?: return
    val image = Toolkit.getDefaultToolkit().createImage(stream.readBytes())
    if (Taskbar.isTaskbarSupported()) {
        runCatching { Taskbar.getTaskbar().iconImage = image }
    }
}

private fun loadWindowIcon(): androidx.compose.ui.graphics.painter.Painter? {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("icon.png")
        ?: return null
    return androidx.compose.ui.graphics.painter.BitmapPainter(loadImageBitmap(stream))
}
