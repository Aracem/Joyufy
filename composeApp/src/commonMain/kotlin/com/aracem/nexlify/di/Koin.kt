package com.aracem.nexlify.di

import com.aracem.nexlify.data.db.DatabaseDriverFactory
import com.aracem.nexlify.data.repository.AccountRepository
import com.aracem.nexlify.data.repository.InvestmentSnapshotRepository
import com.aracem.nexlify.data.repository.TransactionRepository
import com.aracem.nexlify.data.repository.WealthRepository
import com.aracem.nexlify.db.NexlifyDatabase
import org.koin.core.context.startKoin
import org.koin.dsl.module

val dataModule = module {
    single { DatabaseDriverFactory() }
    single { NexlifyDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { AccountRepository(get()) }
    single { TransactionRepository(get()) }
    single { InvestmentSnapshotRepository(get()) }
    single { WealthRepository(get()) }
}

fun initKoin() {
    startKoin {
        modules(dataModule)
    }
}
