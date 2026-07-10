package com.aracem.joyufy.ui.ledger

import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.InvestmentSnapshotRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.InvestmentSnapshot
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionReviewStatus
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.MILLIS_IN_DAY
import com.aracem.joyufy.ui.components.currentWeekStartMillis
import com.aracem.joyufy.ui.navigation.LedgerInitialFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class LedgerPreset {
    ALL,
    UNCATEGORIZED,
    DUPLICATES,
    TRANSFERS,
    IMPORTED_DRAFTS,
    DATA_QUALITY,
}

enum class LedgerWarning {
    MISSING_CATEGORY,
    EMPTY_DESCRIPTION,
    POSSIBLE_DUPLICATE,
    BROKEN_TRANSFER,
    UNUSUAL_AMOUNT,
    IMPORTED_DRAFT,
}

enum class LedgerQualityType {
    MISSING_CATEGORIES,
    EMPTY_DESCRIPTIONS,
    POSSIBLE_DUPLICATES,
    BROKEN_TRANSFERS,
    UNUSUAL_AMOUNTS,
    STALE_ACCOUNTS,
    STALE_SNAPSHOTS,
    IMPORTED_DRAFTS,
}

data class LedgerQualityMetric(
    val type: LedgerQualityType,
    val count: Int,
)

data class LedgerTransactionRow(
    val transaction: Transaction,
    val account: Account?,
    val relatedAccount: Account?,
    val warnings: Set<LedgerWarning>,
    val duplicateGroupSize: Int,
    val selected: Boolean,
)

data class LedgerUiState(
    val isLoading: Boolean = true,
    val rows: List<LedgerTransactionRow> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val customCategories: List<String> = emptyList(),
    val selectedPreset: LedgerPreset = LedgerPreset.ALL,
    val searchQuery: String = "",
    val selectedAccountId: Long? = null,
    val selectedType: TransactionType? = null,
    val selectedIds: Set<Long> = emptySet(),
    val totalTransactionCount: Int = 0,
    val uncategorizedCount: Int = 0,
    val duplicateCount: Int = 0,
    val staleSnapshotCount: Int = 0,
    val importedDraftCount: Int = 0,
    val qualityMetrics: List<LedgerQualityMetric> = emptyList(),
) {
    val selectedCount: Int get() = selectedIds.size
}

sealed interface LedgerEvent {
    data object Idle : LedgerEvent
    data class Deleted(val count: Int) : LedgerEvent
    data class Restored(val count: Int) : LedgerEvent
    data class Updated(val count: Int) : LedgerEvent
    data class Error(val message: String) : LedgerEvent
}

private data class LedgerSource(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val snapshots: List<InvestmentSnapshot>,
)

private data class LedgerFilterState(
    val preset: LedgerPreset,
    val searchQuery: String,
    val accountId: Long?,
    val type: TransactionType?,
    val selectedIds: Set<Long>,
)

private data class QualitySets(
    val missingCategoryIds: Set<Long>,
    val emptyDescriptionIds: Set<Long>,
    val duplicateIds: Set<Long>,
    val brokenTransferIds: Set<Long>,
    val unusualAmountIds: Set<Long>,
    val staleAccountIds: Set<Long>,
    val staleSnapshotAccountIds: Set<Long>,
    val importedDraftIds: Set<Long>,
    val duplicateGroupSizes: Map<Long, Int>,
)

class TransactionLedgerViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val snapshotRepository: InvestmentSnapshotRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val preset = MutableStateFlow(LedgerPreset.ALL)
    private val searchQuery = MutableStateFlow("")
    private val selectedAccountId = MutableStateFlow<Long?>(null)
    private val selectedType = MutableStateFlow<TransactionType?>(null)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private var lastDeletedTransactions: List<Transaction> = emptyList()

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    private val _event = MutableStateFlow<LedgerEvent>(LedgerEvent.Idle)
    val event: StateFlow<LedgerEvent> = _event.asStateFlow()

    init {
        observeLedger()
    }

    fun applyInitialFilter(initialFilter: LedgerInitialFilter) {
        setPreset(
            when (initialFilter) {
                LedgerInitialFilter.ALL -> LedgerPreset.ALL
                LedgerInitialFilter.UNCATEGORIZED -> LedgerPreset.UNCATEGORIZED
                LedgerInitialFilter.DUPLICATES -> LedgerPreset.DUPLICATES
                LedgerInitialFilter.DATA_QUALITY -> LedgerPreset.DATA_QUALITY
            }
        )
    }

    fun setPreset(value: LedgerPreset) {
        preset.value = value
        clearSelection()
    }

    fun setSearchQuery(value: String) {
        searchQuery.value = value
        clearSelection()
    }

    fun setSelectedAccountId(value: Long?) {
        selectedAccountId.value = value
        clearSelection()
    }

    fun setSelectedType(value: TransactionType?) {
        selectedType.value = value
        clearSelection()
    }

    fun toggleSelected(id: Long) {
        selectedIds.value = selectedIds.value.toggle(id)
    }

    fun selectAllVisible() {
        selectedIds.value = _uiState.value.rows.map { it.transaction.id }.toSet()
    }

    fun clearSelection() {
        if (selectedIds.value.isNotEmpty()) {
            selectedIds.value = emptySet()
        }
    }

    fun bulkSetCategory(category: String?) {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        val normalizedCategory = category?.trim()?.ifBlank { null }
        scope.launch {
            runCatching {
                ids.forEach { id ->
                    val tx = transactionRepository.getTransactionById(id) ?: return@forEach
                    transactionRepository.updateTransaction(
                        id = tx.id,
                        type = tx.type,
                        amount = tx.amount,
                        category = normalizedCategory,
                        description = tx.description,
                        relatedAccountId = tx.relatedAccountId,
                        date = tx.date,
                    )
                }
                ids.size
            }.onSuccess {
                clearSelection()
                _event.value = LedgerEvent.Updated(it)
            }.onFailure {
                _event.value = LedgerEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun bulkMoveToAccount(accountId: Long) {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        scope.launch {
            runCatching {
                var movedCount = 0
                ids.forEach { id ->
                    val tx = transactionRepository.getTransactionById(id) ?: return@forEach
                    if (!tx.isTransfer() && tx.accountId != accountId) {
                        transactionRepository.updateTransactionAccount(id = tx.id, accountId = accountId)
                        movedCount++
                    }
                }
                movedCount
            }.onSuccess { count ->
                clearSelection()
                _event.value = LedgerEvent.Updated(count)
            }.onFailure {
                _event.value = LedgerEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun bulkMarkReviewed() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        scope.launch {
            runCatching {
                ids.forEach { id ->
                    transactionRepository.updateTransactionReviewStatus(id, TransactionReviewStatus.REVIEWED)
                }
                ids.size
            }.onSuccess { count ->
                clearSelection()
                _event.value = LedgerEvent.Updated(count)
            }.onFailure {
                _event.value = LedgerEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun bulkDeleteSelected() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        scope.launch {
            runCatching {
                val toDeleteById = linkedMapOf<Long, Transaction>()
                ids.forEach { id ->
                    val tx = transactionRepository.getTransactionById(id) ?: return@forEach
                    toDeleteById[tx.id] = tx
                    if (tx.relatedAccountId != null) {
                        val sister = transactionRepository.getRelatedTransfer(
                            relatedAccountId = tx.relatedAccountId,
                            originAccountId = tx.accountId,
                            amount = tx.amount,
                            type = tx.oppositeTransferLegType(),
                            date = tx.date,
                        )
                        if (sister != null) toDeleteById[sister.id] = sister
                    }
                }
                toDeleteById.keys.forEach { transactionRepository.deleteTransaction(it) }
                lastDeletedTransactions = toDeleteById.values.toList()
                toDeleteById.size
            }.onSuccess { count ->
                clearSelection()
                _event.value = LedgerEvent.Deleted(count)
            }.onFailure {
                _event.value = LedgerEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun undoLastDelete() {
        val transactions = lastDeletedTransactions
        if (transactions.isEmpty()) return
        scope.launch {
            runCatching {
                transactions.sortedBy { it.id }.forEach { tx ->
                    transactionRepository.insertTransactionWithId(
                        id = tx.id,
                        accountId = tx.accountId,
                        type = tx.type,
                        amount = tx.amount,
                        category = tx.category,
                        description = tx.description,
                        relatedAccountId = tx.relatedAccountId,
                        date = tx.date,
                        reviewStatus = tx.reviewStatus,
                        importBatch = tx.importBatch,
                    )
                }
                transactions.size
            }.onSuccess { count ->
                lastDeletedTransactions = emptyList()
                _event.value = LedgerEvent.Restored(count)
            }.onFailure {
                _event.value = LedgerEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun resetEvent() {
        _event.value = LedgerEvent.Idle
    }

    private fun observeLedger() {
        val sourceFlow = combine(
            accountRepository.observeAccounts(),
            transactionRepository.observeAllTransactions(),
            snapshotRepository.observeAllSnapshots(),
        ) { accounts, transactions, snapshots ->
            LedgerSource(accounts, transactions, snapshots)
        }
        val filterFlow = combine(
            preset,
            searchQuery,
            selectedAccountId,
            selectedType,
            selectedIds,
        ) { selectedPreset, query, accountId, type, selected ->
            LedgerFilterState(selectedPreset, query, accountId, type, selected)
        }

        scope.launch {
            combine(sourceFlow, filterFlow) { source, filters ->
                buildUiState(source, filters)
            }.catch { error ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                _event.value = LedgerEvent.Error(error.message.orEmpty())
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun buildUiState(
        source: LedgerSource,
        filters: LedgerFilterState,
    ): LedgerUiState {
        val accountsById = source.accounts.associateBy { it.id }
        val quality = buildQualitySets(source)
        val validSelectedIds = filters.selectedIds intersect source.transactions.map { it.id }.toSet()
        if (validSelectedIds.size != filters.selectedIds.size) {
            selectedIds.value = validSelectedIds
        }

        val allRows = source.transactions
            .distinctBy { it.id }
            .sortedByDescending { it.date }
            .map { tx ->
                val warnings = buildWarnings(tx, quality)
                LedgerTransactionRow(
                    transaction = tx,
                    account = accountsById[tx.accountId],
                    relatedAccount = tx.relatedAccountId?.let(accountsById::get),
                    warnings = warnings,
                    duplicateGroupSize = quality.duplicateGroupSizes[tx.id] ?: 1,
                    selected = tx.id in validSelectedIds,
                )
            }

        val query = filters.searchQuery.trim().lowercase()
        val rows = allRows
            .asSequence()
            .filter { row -> row.matchesPreset(filters.preset) }
            .filter { row -> filters.accountId == null || row.transaction.accountId == filters.accountId }
            .filter { row ->
                when (filters.type) {
                    null -> true
                    TransactionType.TRANSFER -> row.transaction.isTransfer()
                    else -> row.transaction.type == filters.type && !row.transaction.isTransfer()
                }
            }
            .filter { row -> query.isBlank() || row.matchesSearch(query) }
            .toList()

        val qualityMetrics = listOf(
            LedgerQualityMetric(LedgerQualityType.MISSING_CATEGORIES, quality.missingCategoryIds.size),
            LedgerQualityMetric(LedgerQualityType.EMPTY_DESCRIPTIONS, quality.emptyDescriptionIds.size),
            LedgerQualityMetric(LedgerQualityType.POSSIBLE_DUPLICATES, quality.duplicateIds.size),
            LedgerQualityMetric(LedgerQualityType.BROKEN_TRANSFERS, quality.brokenTransferIds.size),
            LedgerQualityMetric(LedgerQualityType.UNUSUAL_AMOUNTS, quality.unusualAmountIds.size),
            LedgerQualityMetric(LedgerQualityType.STALE_ACCOUNTS, quality.staleAccountIds.size),
            LedgerQualityMetric(LedgerQualityType.STALE_SNAPSHOTS, quality.staleSnapshotAccountIds.size),
            LedgerQualityMetric(LedgerQualityType.IMPORTED_DRAFTS, quality.importedDraftIds.size),
        )

        return LedgerUiState(
            isLoading = false,
            rows = rows,
            accounts = source.accounts,
            customCategories = source.transactions
                .mapNotNull { it.category?.takeIf(String::isNotBlank) }
                .distinct()
                .sorted(),
            selectedPreset = filters.preset,
            searchQuery = filters.searchQuery,
            selectedAccountId = filters.accountId,
            selectedType = filters.type,
            selectedIds = validSelectedIds,
            totalTransactionCount = source.transactions.size,
            uncategorizedCount = quality.missingCategoryIds.size,
            duplicateCount = quality.duplicateIds.size,
            staleSnapshotCount = quality.staleSnapshotAccountIds.size,
            importedDraftCount = quality.importedDraftIds.size,
            qualityMetrics = qualityMetrics,
        )
    }

    private fun buildQualitySets(source: LedgerSource): QualitySets {
        val nonTransferTransactions = source.transactions.filterNot { it.isTransfer() }
        val missingCategoryIds = nonTransferTransactions
            .filter { it.category.isNullOrBlank() }
            .map { it.id }
            .toSet()
        val emptyDescriptionIds = nonTransferTransactions
            .filter { it.description.isNullOrBlank() }
            .map { it.id }
            .toSet()

        val duplicateGroups = nonTransferTransactions
            .groupBy { tx ->
                DuplicateKey(
                    accountId = tx.accountId,
                    type = tx.type,
                    amountInCents = (tx.amount * 100).toLong(),
                    date = tx.date,
                    description = tx.description?.trim()?.lowercase().orEmpty(),
                )
            }
            .values
            .filter { it.size > 1 }
        val duplicateIds = duplicateGroups.flatten().map { it.id }.toSet()
        val duplicateGroupSizes = duplicateGroups
            .flatMap { group -> group.map { it.id to group.size } }
            .toMap()

        val brokenTransferIds = source.transactions
            .filter { it.relatedAccountId != null }
            .filterNot { tx ->
                source.transactions.any { other ->
                    other.id != tx.id &&
                        other.accountId == tx.relatedAccountId &&
                        other.relatedAccountId == tx.accountId &&
                        (other.amount * 100).toLong() == (tx.amount * 100).toLong() &&
                        other.type == tx.oppositeTransferLegType()
                }
            }
            .map { it.id }
            .toSet()

        val averageAmount = nonTransferTransactions
            .map { it.amount }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?: 0.0
        val unusualThreshold = maxOf(1_000.0, averageAmount * 4)
        val unusualAmountIds = nonTransferTransactions
            .filter { it.amount >= unusualThreshold }
            .map { it.id }
            .toSet()
        val importedDraftIds = source.transactions
            .filter {
                it.reviewStatus == TransactionReviewStatus.DRAFT ||
                    it.reviewStatus == TransactionReviewStatus.NEEDS_REVIEW
            }
            .map { it.id }
            .toSet()

        val now = System.currentTimeMillis()
        val staleSince = now - 90 * MILLIS_IN_DAY
        val transactionsByAccount = source.transactions.groupBy { it.accountId }
        val snapshotsByAccount = source.snapshots.groupBy { it.accountId }
        val staleAccountIds = source.accounts
            .filter { account ->
                val latestTransaction = transactionsByAccount[account.id]?.maxOfOrNull { it.date }
                val latestSnapshot = snapshotsByAccount[account.id]?.maxOfOrNull { it.weekDate }
                val latestActivity = listOfNotNull(latestTransaction, latestSnapshot, account.createdAt).maxOrNull()
                latestActivity != null && latestActivity < staleSince
            }
            .map { it.id }
            .toSet()
        val currentWeek = currentWeekStartMillis()
        val staleSnapshotAccountIds = source.accounts
            .filter { it.type == AccountType.INVESTMENT }
            .filter { account ->
                snapshotsByAccount[account.id]?.maxOfOrNull { it.weekDate }?.let { it < currentWeek } ?: true
            }
            .map { it.id }
            .toSet()

        return QualitySets(
            missingCategoryIds = missingCategoryIds,
            emptyDescriptionIds = emptyDescriptionIds,
            duplicateIds = duplicateIds,
            brokenTransferIds = brokenTransferIds,
            unusualAmountIds = unusualAmountIds,
            staleAccountIds = staleAccountIds,
            staleSnapshotAccountIds = staleSnapshotAccountIds,
            importedDraftIds = importedDraftIds,
            duplicateGroupSizes = duplicateGroupSizes,
        )
    }

    private fun buildWarnings(tx: Transaction, quality: QualitySets): Set<LedgerWarning> = buildSet {
        if (tx.id in quality.missingCategoryIds) add(LedgerWarning.MISSING_CATEGORY)
        if (tx.id in quality.emptyDescriptionIds) add(LedgerWarning.EMPTY_DESCRIPTION)
        if (tx.id in quality.duplicateIds) add(LedgerWarning.POSSIBLE_DUPLICATE)
        if (tx.id in quality.brokenTransferIds) add(LedgerWarning.BROKEN_TRANSFER)
        if (tx.id in quality.unusualAmountIds) add(LedgerWarning.UNUSUAL_AMOUNT)
        if (tx.id in quality.importedDraftIds) add(LedgerWarning.IMPORTED_DRAFT)
    }

    private fun LedgerTransactionRow.matchesPreset(value: LedgerPreset): Boolean =
        when (value) {
            LedgerPreset.ALL -> true
            LedgerPreset.UNCATEGORIZED -> LedgerWarning.MISSING_CATEGORY in warnings
            LedgerPreset.DUPLICATES -> LedgerWarning.POSSIBLE_DUPLICATE in warnings
            LedgerPreset.TRANSFERS -> transaction.isTransfer()
            LedgerPreset.IMPORTED_DRAFTS -> LedgerWarning.IMPORTED_DRAFT in warnings
            LedgerPreset.DATA_QUALITY -> warnings.isNotEmpty()
        }

    private fun LedgerTransactionRow.matchesSearch(query: String): Boolean =
        transaction.description?.lowercase()?.contains(query) == true ||
            transaction.category?.lowercase()?.contains(query) == true ||
            account?.name?.lowercase()?.contains(query) == true ||
            relatedAccount?.name?.lowercase()?.contains(query) == true ||
            transaction.amount.toString().contains(query)

    private fun Transaction.isTransfer(): Boolean =
        relatedAccountId != null || type == TransactionType.TRANSFER

    private fun Transaction.oppositeTransferLegType(): TransactionType =
        when (type) {
            TransactionType.INCOME -> TransactionType.EXPENSE
            TransactionType.EXPENSE,
            TransactionType.TRANSFER -> TransactionType.INCOME
        }

    private fun Set<Long>.toggle(id: Long): Set<Long> =
        if (id in this) this - id else this + id

    private data class DuplicateKey(
        val accountId: Long,
        val type: TransactionType,
        val amountInCents: Long,
        val date: Long,
        val description: String,
    )

}
