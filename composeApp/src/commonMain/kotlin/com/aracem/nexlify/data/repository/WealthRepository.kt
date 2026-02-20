package com.aracem.nexlify.data.repository

import com.aracem.nexlify.db.NexlifyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WealthRepository(private val db: NexlifyDatabase) {

    suspend fun getTotalWealth(): Double = withContext(Dispatchers.IO) {
        db.nexlifyDatabaseQueries
            .getTotalWealth()
            .executeAsOne()
    }
}
