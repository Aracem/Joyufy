package com.aracem.nexlify.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.nexlify.db.NexlifyDatabase
import java.io.File

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".nexlify").also { it.mkdirs() }
        val dbFile = File(dbDir, "nexlify.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        NexlifyDatabase.Schema.create(driver)
        return driver
    }
}
