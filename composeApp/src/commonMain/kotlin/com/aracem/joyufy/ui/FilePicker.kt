package com.aracem.joyufy.ui

expect fun showSaveFileDialog(suggestedName: String): String?
expect fun showOpenFileDialog(): String?
expect fun openUrl(url: String)
