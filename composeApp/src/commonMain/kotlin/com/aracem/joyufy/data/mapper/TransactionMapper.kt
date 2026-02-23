package com.aracem.joyufy.data.mapper

import com.aracem.joyufy.db.Transaction as TransactionEntity
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    accountId = account_id,
    type = TransactionType.valueOf(type),
    amount = amount,
    category = category,
    description = description,
    relatedAccountId = related_account_id,
    date = date,
)
