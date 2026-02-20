package com.aracem.nexlify.ui.navigation

sealed class Screen {
    data object Dashboard : Screen()
    data class AccountDetail(val accountId: Long) : Screen()
    data object Settings : Screen()
}
