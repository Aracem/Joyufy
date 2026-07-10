package com.aracem.joyufy.ui.navigation

enum class LedgerInitialFilter {
    ALL,
    UNCATEGORIZED,
    DUPLICATES,
    DATA_QUALITY,
}

sealed class Screen {
    data object Dashboard : Screen()
    data class AccountDetail(
        val accountId: Long,
        val openSnapshotDialog: Boolean = false,
        val openTransactionDialog: Boolean = false,
        val focusSearch: Boolean = false,
        val launchRequestId: Long = 0L,
    ) : Screen()
    data class Ledger(
        val initialFilter: LedgerInitialFilter = LedgerInitialFilter.ALL,
    ) : Screen()
    data object Settings : Screen()
}
