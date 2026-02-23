package com.aracem.joyufy.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.db.JoyufyDatabase
import java.io.File

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".joyufy").also { it.mkdirs() }
        val dbFile = File(dbDir, "joyufy.db")
        val isNew = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (isNew) {
            JoyufyDatabase.Schema.create(driver)
        } else {
            // Add logo_url column if it doesn't exist yet
            runCatching { driver.execute(null, "ALTER TABLE Account ADD COLUMN logo_url TEXT", 0) }
        }
        return driver
    }
}
