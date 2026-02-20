package com.aracem.nexlify.domain.model

data class InvestmentSnapshot(
    val id: Long,
    val accountId: Long,
    val totalValue: Double,
    val weekDate: Long, // Unix timestamp — Monday of the week
)
