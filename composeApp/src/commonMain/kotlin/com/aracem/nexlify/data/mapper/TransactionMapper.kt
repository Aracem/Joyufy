package com.aracem.nexlify.data.mapper

import com.aracem.nexlify.db.Transaction as TransactionEntity
import com.aracem.nexlify.domain.model.Transaction
import com.aracem.nexlify.domain.model.TransactionType

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
