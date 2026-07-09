package com.aracem.joyufy

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aracem.joyufy.di.initKoin
import com.aracem.joyufy.ui.App
import com.aracem.joyufy.ui.drive.DriveViewModel
import java.awt.Taskbar
import java.awt.Toolkit
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import org.jetbrains.skia.Image

fun main() {
    initKoin()
    setAppIcon()
    application {
        val driveViewModel: DriveViewModel = remember { GlobalContext.get().get() }
        // The close lifecycle is non-trivial:
        //   1. User clicks the close button → onCloseRequest fires.
        //   2. If we need to upload, we set closeRequested=true rather than
        //      exiting immediately. That keeps the window open so Compose can
        //      render the syncing overlay.
        //   3. DriveViewModel.syncToCloudSuspend() runs on the Koin scope and
        //      flips isSyncing. A LaunchedEffect inside the composition kicks
        //      off the upload once closeRequested becomes true.
        //   4. When isSyncing turns false (success or 5s timeout), we call
        //      exitApplication().
        var closeRequested by remember { mutableStateOf(false) }

        // Kick off the upload exactly once when the close is requested.
        LaunchedEffect(closeRequested) {
            if (!closeRequested) return@LaunchedEffect
            if (driveViewModel.shouldAutoSync()) {
                withTimeoutOrNull(5_000) { driveViewModel.syncToCloudSuspend() }
                exitApplication()
            } else {
                exitApplication()
            }
        }

        Window(
            onCloseRequest = {
                if (driveViewModel.shouldAutoSync() && !closeRequested) {
                    closeRequested = true
                } else if (!closeRequested) {
                    exitApplication()
                }
            },
            title = "Joyufy",
            icon = loadWindowIcon(),
        ) {
            App()
        }
    }
}

private fun setAppIcon() {
    val bytes = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("icon.png")
        ?.use { it.readBytes() }
        ?: return
    val image = Toolkit.getDefaultToolkit().createImage(bytes)
    if (Taskbar.isTaskbarSupported()) {
        runCatching { Taskbar.getTaskbar().iconImage = image }
    }
}

private fun loadWindowIcon(): Painter? {
    val bytes = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("icon.png")
        ?.use { it.readBytes() }
        ?: return null
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
