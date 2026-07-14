package com.aracem.joyufy.ui.account

import com.aracem.joyufy.domain.logic.findInvestmentFlowBackfillCandidates
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

class InvestmentPerformanceCalculatorTest {

    private val weekOne = 10_000L
    private val weekTwo = weekOne + 604_800_000L
    private val weekThree = weekTwo + 604_800_000L

    @Test
    fun performanceSeparatesCashFlowsAndMarketMovement() {
        val snapshots = listOf(
            InvestmentSnapshot(
                id = 3L,
                accountId = 1L,
                totalValue = 1_050.0,
                weekDate = 3_000L,
                withdrawals = 100.0,
                fees = 1.0,
            ),
            InvestmentSnapshot(
                id = 1L,
                accountId = 1L,
                totalValue = 1_000.0,
                weekDate = 1_000L,
            ),
            InvestmentSnapshot(
                id = 2L,
                accountId = 1L,
                totalValue = 1_100.0,
                weekDate = 2_000L,
                deposits = 50.0,
                withdrawals = 20.0,
                fees = 2.0,
                dividends = 5.0,
            ),
        )

        val points = buildInvestmentPerformance(snapshots)
        val summary = requireNotNull(buildInvestmentPerformanceSummary(points))

        assertEquals(listOf(1L, 2L, 3L), points.map { it.snapshot.id })

        assertEquals(100.0, points[1].valueChange)
        assertEquals(70.0, points[1].contributionAdjustedGain)
        assertEquals(67.0, points[1].marketPerformance)
        assertEquals(7.0, requireNotNull(points[1].returnPct), absoluteTolerance = 0.0001)

        assertEquals(-50.0, points[2].valueChange)
        assertEquals(50.0, points[2].contributionAdjustedGain)
        assertEquals(51.0, points[2].marketPerformance)
        assertEquals(4.5454, requireNotNull(points[2].returnPct), absoluteTolerance = 0.0001)

        assertEquals(50.0, summary.deposits)
        assertEquals(120.0, summary.withdrawals)
        assertEquals(3.0, summary.fees)
        assertEquals(5.0, summary.dividends)
        assertEquals(120.0, summary.contributionAdjustedGain)
        assertEquals(118.0, summary.marketPerformance)
        assertEquals(11.8636, requireNotNull(summary.timeWeightedReturnPct), absoluteTolerance = 0.0001)
    }

    @Test
    fun performanceInfersTransferFlowsForUnannotatedSnapshots() {
        val snapshots = listOf(
            InvestmentSnapshot(id = 1L, accountId = 1L, totalValue = 1_000.0, weekDate = weekOne),
            InvestmentSnapshot(id = 2L, accountId = 1L, totalValue = 1_150.0, weekDate = weekTwo),
        )
        val transactions = listOf(
            transferLeg(id = 10L, type = TransactionType.INCOME, amount = 100.0, date = weekTwo + 1_000L),
        )

        val points = buildInvestmentPerformance(snapshots, transactions)
        val second = points[1]

        assertEquals(100.0, second.snapshot.deposits)
        assertEquals(0.0, second.snapshot.withdrawals)
        assertEquals(50.0, second.contributionAdjustedGain)
        assertEquals(5.0, requireNotNull(second.returnPct), absoluteTolerance = 0.0001)
    }

    @Test
    fun manualSnapshotAnnotationsOverrideDerivedTransferFlows() {
        val snapshots = listOf(
            InvestmentSnapshot(id = 1L, accountId = 1L, totalValue = 1_000.0, weekDate = weekOne),
            InvestmentSnapshot(id = 2L, accountId = 1L, totalValue = 1_150.0, weekDate = weekTwo, deposits = 25.0),
        )
        val transactions = listOf(
            transferLeg(id = 10L, type = TransactionType.INCOME, amount = 100.0, date = weekTwo + 1_000L),
        )

        val points = buildInvestmentPerformance(snapshots, transactions)
        val second = points[1]

        assertEquals(25.0, second.snapshot.deposits)
        assertEquals(125.0, second.contributionAdjustedGain)
    }

    @Test
    fun backfillCandidatesOnlyIncludeUnannotatedSnapshotsWithTransferFlows() {
        val snapshots = listOf(
            InvestmentSnapshot(id = 1L, accountId = 1L, totalValue = 1_000.0, weekDate = weekOne),
            InvestmentSnapshot(id = 2L, accountId = 1L, totalValue = 1_150.0, weekDate = weekTwo),
            InvestmentSnapshot(id = 3L, accountId = 1L, totalValue = 1_250.0, weekDate = weekThree, deposits = 10.0),
        )
        val transactions = listOf(
            transferLeg(id = 10L, type = TransactionType.INCOME, amount = 100.0, date = weekTwo + 1_000L),
            transferLeg(id = 11L, type = TransactionType.INCOME, amount = 200.0, date = weekThree + 1_000L),
        )

        val candidates = findInvestmentFlowBackfillCandidates(snapshots, transactions)

        assertEquals(listOf(2L), candidates.map { it.snapshot.id })
        assertEquals(100.0, candidates.single().flows.deposits)
    }

    private fun transferLeg(
        id: Long,
        type: TransactionType,
        amount: Double,
        date: Long,
    ): Transaction = Transaction(
        id = id,
        accountId = 1L,
        type = type,
        amount = amount,
        category = null,
        description = null,
        relatedAccountId = 2L,
        date = date,
    )
}
