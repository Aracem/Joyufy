package com.aracem.nexlify.data.mapper

import com.aracem.nexlify.db.InvestmentSnapshot as SnapshotEntity
import com.aracem.nexlify.domain.model.InvestmentSnapshot

fun SnapshotEntity.toDomain(): InvestmentSnapshot = InvestmentSnapshot(
    id = id,
    accountId = account_id,
    totalValue = total_value,
    weekDate = week_date,
)
