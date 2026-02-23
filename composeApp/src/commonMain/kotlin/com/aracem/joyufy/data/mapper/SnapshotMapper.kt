package com.aracem.joyufy.data.mapper

import com.aracem.joyufy.db.InvestmentSnapshot as SnapshotEntity
import com.aracem.joyufy.domain.model.InvestmentSnapshot

fun SnapshotEntity.toDomain(): InvestmentSnapshot = InvestmentSnapshot(
    id = id,
    accountId = account_id,
    totalValue = total_value,
    weekDate = week_date,
)
