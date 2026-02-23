package com.aracem.joyufy.domain.model

import androidx.compose.ui.graphics.Color

enum class AccountType { BANK, INVESTMENT, CASH }

data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val color: Color,
    val position: Int,
    val createdAt: Long,
)
