package com.aracem.nexlify.di

import com.aracem.nexlify.data.db.DatabaseDriverFactory
import com.aracem.nexlify.data.repository.AccountRepository
import com.aracem.nexlify.data.repository.InvestmentSnapshotRepository
import com.aracem.nexlify.data.repository.TransactionRepository
import com.aracem.nexlify.data.repository.WealthRepository
import com.aracem.nexlify.db.NexlifyDatabase
import com.aracem.nexlify.ui.account.CreateAccountViewModel
import com.aracem.nexlify.ui.dashboard.DashboardViewModel
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

val viewModelModule = module {
    factory { DashboardViewModel(get(), get(), get(), get()) }
    factory { CreateAccountViewModel(get()) }
}

fun initKoin() {
    startKoin {
        modules(dataModule, viewModelModule)
    }
}
