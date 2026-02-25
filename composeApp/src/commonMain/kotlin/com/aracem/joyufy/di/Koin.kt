package com.aracem.joyufy.di

import com.aracem.joyufy.data.db.DatabaseDriverFactory
import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.BackupRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.data.repository.WealthRepository
import com.aracem.joyufy.db.JoyufyDatabase
import com.aracem.joyufy.ui.account.AccountDetailViewModel
import com.aracem.joyufy.ui.account.CreateAccountViewModel
import com.aracem.joyufy.ui.backup.BackupViewModel
import com.aracem.joyufy.ui.dashboard.DashboardViewModel
import com.aracem.joyufy.ui.settings.SettingsViewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

val dataModule = module {
    single { DatabaseDriverFactory() }
    single { JoyufyDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { AccountRepository(get()) }
    single { TransactionRepository(get()) }
    single { InvestmentSnapshotRepository(get()) }
    single { WealthRepository(get()) }
    single { BackupRepository(get(), get(), get()) }
}

val viewModelModule = module {
    factory { DashboardViewModel(get(), get(), get(), get()) }
    factory { CreateAccountViewModel(get(), get()) }
    factory { (accountId: Long) -> AccountDetailViewModel(accountId, get(), get(), get()) }
    factory { BackupViewModel(get()) }
    factory { SettingsViewModel(get(), get(), get()) }
}

fun initKoin() {
    startKoin {
        modules(dataModule, viewModelModule)
    }
}
