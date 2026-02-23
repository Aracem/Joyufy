package com.aracem.joyufy.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/** Shows a native save dialog and returns the chosen file path, or null if cancelled. */
actual fun showSaveFileDialog(suggestedName: String): String? {
    val dialog = FileDialog(null as Frame?, "Guardar backup", FileDialog.SAVE).apply {
        file = suggestedName
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json") }
        isVisible = true
    }
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    val path = if (file.endsWith(".json")) "$dir$file" else "$dir$file.json"
    return path
}

/** Shows a native open dialog and returns the content of the chosen file, or null if cancelled. */
actual fun showOpenFileDialog(): String? {
    val dialog = FileDialog(null as Frame?, "Abrir backup", FileDialog.LOAD).apply {
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json") }
        isVisible = true
    }
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File("$dir$file").readText()
}
