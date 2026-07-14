package com.aracem.joyufy.domain.model

data class InvestmentSnapshot(
    val id: Long,
    val accountId: Long,
    val totalValue: Double,
    val weekDate: Long, // Unix timestamp — Monday of the week
    val deposits: Double = 0.0,
    val withdrawals: Double = 0.0,
    val fees: Double = 0.0,
    val dividends: Double = 0.0,
    val note: String? = null,
)
