package com.aracem.joyufy.data.repository

import com.aracem.joyufy.db.JoyufyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WealthRepository(private val db: JoyufyDatabase) {

    suspend fun getTotalWealth(): Double = withContext(Dispatchers.IO) {
        db.joyufyDatabaseQueries
            .getTotalWealth()
            .executeAsOne()
    }
}
