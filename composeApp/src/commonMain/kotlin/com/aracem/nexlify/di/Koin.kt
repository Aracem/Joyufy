package com.aracem.nexlify.di

import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    // TODO: Add dependencies as they are created
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
