package com.aracem.joyufy.domain.logic

import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val MILLIS_IN_DAY = 86_400_000L
private const val MILLIS_IN_WEEK = 7 * MILLIS_IN_DAY

data class InvestmentSnapshotFlows(
    val deposits: Double = 0.0,
    val withdrawals: Double = 0.0,
) {
    val hasFlows: Boolean get() = deposits != 0.0 || withdrawals != 0.0
}

data class InvestmentFlowBackfillCandidate(
    val snapshot: InvestmentSnapshot,
    val flows: InvestmentSnapshotFlows,
)

fun InvestmentSnapshot.hasManualInvestmentFlowAnnotations(): Boolean =
    deposits != 0.0 || withdrawals != 0.0 || fees != 0.0 || dividends != 0.0

fun InvestmentSnapshot.withDerivedInvestmentFlows(transactions: List<Transaction>): InvestmentSnapshot {
    if (hasManualInvestmentFlowAnnotations()) return this
    val flows = deriveInvestmentSnapshotFlows(accountId, weekDate, transactions)
    if (!flows.hasFlows) return this
    return copy(deposits = flows.deposits, withdrawals = flows.withdrawals)
}

fun deriveInvestmentSnapshotFlows(
    accountId: Long,
    weekDate: Long,
    transactions: List<Transaction>,
): InvestmentSnapshotFlows {
    val weekEnd = weekDate + MILLIS_IN_WEEK
    return transactions
        .asSequence()
        .filter { tx ->
            tx.accountId == accountId &&
                tx.relatedAccountId != null &&
                tx.date >= weekDate &&
                tx.date < weekEnd
        }
        .fold(InvestmentSnapshotFlows()) { acc, tx ->
            when (tx.type) {
                TransactionType.INCOME -> acc.copy(deposits = acc.deposits + tx.amount)
                TransactionType.EXPENSE -> acc.copy(withdrawals = acc.withdrawals + tx.amount)
                TransactionType.TRANSFER -> acc
            }
        }
}

fun deriveInvestmentSnapshotFlowsByWeek(
    accountId: Long,
    transactions: List<Transaction>,
): Map<Long, InvestmentSnapshotFlows> =
    transactions
        .asSequence()
        .filter { it.accountId == accountId && it.relatedAccountId != null }
        .groupBy { it.date.weekStartMillis() }
        .mapValues { (_, weekTransactions) ->
            weekTransactions.fold(InvestmentSnapshotFlows()) { acc, tx ->
                when (tx.type) {
                    TransactionType.INCOME -> acc.copy(deposits = acc.deposits + tx.amount)
                    TransactionType.EXPENSE -> acc.copy(withdrawals = acc.withdrawals + tx.amount)
                    TransactionType.TRANSFER -> acc
                }
            }
        }
        .filterValues { it.hasFlows }

fun findInvestmentFlowBackfillCandidates(
    snapshots: List<InvestmentSnapshot>,
    transactions: List<Transaction>,
): List<InvestmentFlowBackfillCandidate> =
    snapshots
        .filterNot { it.hasManualInvestmentFlowAnnotations() }
        .mapNotNull { snapshot ->
            val flows = deriveInvestmentSnapshotFlows(snapshot.accountId, snapshot.weekDate, transactions)
            if (flows.hasFlows) InvestmentFlowBackfillCandidate(snapshot, flows) else null
        }
        .sortedWith(compareBy<InvestmentFlowBackfillCandidate> { it.snapshot.weekDate }.thenBy { it.snapshot.id })

private fun Long.weekStartMillis(): Long {
    val dayOfWeek = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .dayOfWeek
        .ordinal
    return (this / MILLIS_IN_DAY - dayOfWeek) * MILLIS_IN_DAY
}
