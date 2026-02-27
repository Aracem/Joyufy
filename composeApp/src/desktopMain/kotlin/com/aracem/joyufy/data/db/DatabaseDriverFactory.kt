package com.aracem.joyufy.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.aracem.joyufy.db.JoyufyDatabase
import java.io.File

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        val dbDir = File(System.getProperty("user.home"), ".joyufy").also { it.mkdirs() }
        val dbFile = File(dbDir, "joyufy.db")
        return openOrCreate(dbDir, dbFile)
    }

    private fun openOrCreate(dbDir: File, dbFile: File): SqlDriver {
        val isNew = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (isNew) {
            JoyufyDatabase.Schema.create(driver)
        } else {
            val tablesExist = runCatching {
                driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type='table' AND name='Account'", { cursor ->
                    app.cash.sqldelight.db.QueryResult.Value(cursor.next().value)
                }, 0).value
            }.getOrDefault(false)

            if (!tablesExist) {
                // BD corrupta o incompleta — recrear
                driver.close()
                dbFile.delete()
                val freshDriver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
                JoyufyDatabase.Schema.create(freshDriver)
                return freshDriver
            }

            // Migraciones incrementales
            runCatching { driver.execute(null, "ALTER TABLE Account ADD COLUMN logo_url TEXT", 0) }
        }
        return driver
    }
}
