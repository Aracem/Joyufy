package com.aracem.joyufy.ui.importer

import com.aracem.joyufy.data.repository.AccountRepository
import com.aracem.joyufy.data.repository.TransactionRepository
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.domain.model.Transaction
import com.aracem.joyufy.domain.model.TransactionReviewStatus
import com.aracem.joyufy.domain.model.TransactionType
import com.aracem.joyufy.ui.components.formatDate
import com.aracem.joyufy.ui.components.parseDateInputToMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

enum class CsvColumnRole {
    DATE,
    AMOUNT,
    DESCRIPTION,
    CATEGORY,
    TYPE,
    ACCOUNT,
}

enum class CsvImportRowField {
    DATE,
    AMOUNT,
    DESCRIPTION,
    CATEGORY,
    TYPE,
}

data class CsvImportDraftRow(
    val id: Int,
    val enabled: Boolean = true,
    val accountId: Long? = null,
    val dateText: String = "",
    val amountText: String = "",
    val description: String = "",
    val category: String = "",
    val typeText: String = "",
    val errors: List<String> = emptyList(),
    val duplicateHint: Boolean = false,
) {
    val canCommit: Boolean get() = enabled && errors.isEmpty() && !duplicateHint
}

data class CsvImportUiState(
    val isOpen: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val headers: List<String> = emptyList(),
    val mapping: Map<CsvColumnRole, String?> = emptyMap(),
    val defaultAccountId: Long? = null,
    val rows: List<CsvImportDraftRow> = emptyList(),
    val committedCount: Int = 0,
) {
    val validCount: Int get() = rows.count { it.canCommit }
    val enabledCount: Int get() = rows.count { it.enabled }
    val errorCount: Int get() = rows.count { it.enabled && it.errors.isNotEmpty() }
    val duplicateCount: Int get() = rows.count { it.enabled && it.duplicateHint }
}

sealed interface CsvImportEvent {
    data object Idle : CsvImportEvent
    data class Imported(val count: Int) : CsvImportEvent
    data class Error(val message: String) : CsvImportEvent
}

private data class ParsedCsv(
    val headers: List<String>,
    val rows: List<Map<String, String>>,
)

class CsvImportViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

    private val _event = MutableStateFlow<CsvImportEvent>(CsvImportEvent.Idle)
    val event: StateFlow<CsvImportEvent> = _event.asStateFlow()

    private var parsedCsv = ParsedCsv(emptyList(), emptyList())
    private var existingTransactions = emptyList<Transaction>()

    init {
        scope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.filter { it.type != AccountType.INVESTMENT },
                    defaultAccountId = _uiState.value.defaultAccountId
                        ?: accounts.firstOrNull { it.type != AccountType.INVESTMENT }?.id,
                )
                rebuildRowsFromMapping()
            }
        }
    }

    fun loadCsv(raw: String) {
        scope.launch {
            runCatching {
                parsedCsv = parseStatement(raw)
                existingTransactions = transactionRepository.getAllTransactions()
                val mapping = guessMapping(parsedCsv.headers)
                _uiState.value = _uiState.value.copy(
                    isOpen = true,
                    headers = parsedCsv.headers,
                    mapping = mapping,
                    committedCount = 0,
                )
                rebuildRowsFromMapping()
            }.onFailure {
                _event.value = CsvImportEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun close() {
        parsedCsv = ParsedCsv(emptyList(), emptyList())
        existingTransactions = emptyList()
        val accounts = _uiState.value.accounts
        _uiState.value = CsvImportUiState(
            accounts = accounts,
            defaultAccountId = accounts.firstOrNull()?.id,
        )
    }

    fun setDefaultAccount(accountId: Long?) {
        _uiState.value = _uiState.value.copy(defaultAccountId = accountId)
        rebuildRowsFromMapping()
    }

    fun setMapping(role: CsvColumnRole, header: String?) {
        _uiState.value = _uiState.value.copy(mapping = _uiState.value.mapping + (role to header))
        rebuildRowsFromMapping()
    }

    fun updateRowField(rowId: Int, field: CsvImportRowField, value: String) {
        updateRow(rowId) { row ->
            when (field) {
                CsvImportRowField.DATE -> row.copy(dateText = value)
                CsvImportRowField.AMOUNT -> row.copy(amountText = value)
                CsvImportRowField.DESCRIPTION -> row.copy(description = value)
                CsvImportRowField.CATEGORY -> row.copy(category = value)
                CsvImportRowField.TYPE -> row.copy(typeText = value)
            }
        }
    }

    fun updateRowAccount(rowId: Int, accountId: Long?) {
        updateRow(rowId) { it.copy(accountId = accountId) }
    }

    fun toggleRow(rowId: Int) {
        updateRow(rowId) { it.copy(enabled = !it.enabled) }
    }

    fun commitValidRows() {
        val state = _uiState.value
        val rows = state.rows.filter { it.canCommit }
        if (rows.isEmpty()) return
        scope.launch {
            runCatching {
                val importBatch = "csv-${System.currentTimeMillis()}"
                rows.forEach { row ->
                    val parsed = row.toParsedTransaction() ?: return@forEach
                    transactionRepository.insertImportedTransaction(
                        accountId = parsed.accountId,
                        type = parsed.type,
                        amount = parsed.amount,
                        category = parsed.category,
                        description = parsed.description,
                        relatedAccountId = null,
                        date = parsed.date,
                        reviewStatus = TransactionReviewStatus.DRAFT,
                        importBatch = importBatch,
                    )
                }
                rows.size
            }.onSuccess { count ->
                _uiState.value = _uiState.value.copy(committedCount = count)
                _event.value = CsvImportEvent.Imported(count)
                close()
            }.onFailure {
                _event.value = CsvImportEvent.Error(it.message.orEmpty())
            }
        }
    }

    fun resetEvent() {
        _event.value = CsvImportEvent.Idle
    }

    private fun updateRow(rowId: Int, transform: (CsvImportDraftRow) -> CsvImportDraftRow) {
        val rows = _uiState.value.rows.map { row ->
            if (row.id == rowId) validateRow(transform(row)) else row
        }
        _uiState.value = _uiState.value.copy(rows = markDuplicates(rows))
    }

    private fun rebuildRowsFromMapping() {
        val state = _uiState.value
        if (!state.isOpen || parsedCsv.rows.isEmpty()) return
        val rows = parsedCsv.rows.mapIndexed { index, rawRow ->
            validateRow(
                CsvImportDraftRow(
                    id = index,
                    accountId = accountFrom(rawRow[state.mapping[CsvColumnRole.ACCOUNT]])?.id ?: state.defaultAccountId,
                    dateText = rawRow[state.mapping[CsvColumnRole.DATE]].orEmpty(),
                    amountText = rawRow[state.mapping[CsvColumnRole.AMOUNT]].orEmpty(),
                    description = rawRow[state.mapping[CsvColumnRole.DESCRIPTION]].orEmpty(),
                    category = rawRow[state.mapping[CsvColumnRole.CATEGORY]].orEmpty(),
                    typeText = rawRow[state.mapping[CsvColumnRole.TYPE]].orEmpty(),
                )
            )
        }
        _uiState.value = state.copy(rows = markDuplicates(rows))
    }

    private fun validateRow(row: CsvImportDraftRow): CsvImportDraftRow {
        if (!row.enabled) return row.copy(errors = emptyList(), duplicateHint = false)
        val errors = buildList {
            if (row.accountId == null) add("account")
            if (parseDate(row.dateText) == null) add("date")
            if (parseAmount(row.amountText) == null) add("amount")
        }
        return row.copy(
            errors = errors,
            duplicateHint = false,
        )
    }

    private fun CsvImportDraftRow.toParsedTransaction(): ParsedTransaction? {
        val accountId = accountId ?: return null
        val rawAmount = parseAmount(amountText) ?: return null
        val date = parseDate(dateText) ?: return null
        val explicitType = parseType(typeText)
        val type = explicitType ?: if (rawAmount < 0) TransactionType.EXPENSE else TransactionType.INCOME
        return ParsedTransaction(
            accountId = accountId,
            type = type,
            amount = kotlin.math.abs(rawAmount),
            category = category.trim().ifBlank { null },
            description = description.trim().ifBlank { null },
            date = date,
        )
    }

    private fun hasDuplicate(candidate: ParsedTransaction): Boolean {
        val candidateKey = candidate.duplicateKey()
        return existingTransactions.any { tx ->
            tx.duplicateKey() == candidateKey
        }
    }

    private fun markDuplicates(rows: List<CsvImportDraftRow>): List<CsvImportDraftRow> {
        val rowKeys = rows
            .mapNotNull { row ->
                row.toParsedTransaction()
                    ?.takeIf { row.enabled && row.errors.isEmpty() }
                    ?.duplicateKey()
                    ?.let { key -> row.id to key }
            }
        val repeatedImportIds = rowKeys
            .groupBy { (_, key) -> key }
            .values
            .filter { duplicates -> duplicates.size > 1 }
            .flatten()
            .map { (rowId, _) -> rowId }
            .toSet()
        return rows.map { row ->
            val parsed = row.toParsedTransaction()
            val isDuplicate = row.enabled &&
                row.errors.isEmpty() &&
                parsed != null &&
                (row.id in repeatedImportIds || hasDuplicate(parsed))
            row.copy(duplicateHint = isDuplicate)
        }
    }

    private fun accountFrom(raw: String?): Account? {
        val q = raw?.trim()?.lowercase().orEmpty()
        if (q.isBlank()) return null
        return _uiState.value.accounts.firstOrNull { it.name.lowercase() == q }
    }

    private fun parseDate(value: String): Long? {
        val text = value.trim()
        parseDateInputToMillis(text, dayOffsetMillis = 0L)?.let { return it }
        val iso = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})""").matchEntire(text)
        if (iso != null) {
            val (year, month, day) = iso.destructured
            return runCatching {
                LocalDate(year.toInt(), month.toInt(), day.toInt())
                    .atStartOfDayIn(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()
            }.getOrNull()
        }
        val dashed = Regex("""(\d{1,2})-(\d{1,2})-(\d{4})""").matchEntire(text) ?: return null
        val (day, month, year) = dashed.destructured
        return runCatching {
            LocalDate(year.toInt(), month.toInt(), day.toInt())
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        }.getOrNull()
    }

    private fun parseAmount(value: String): Double? {
        val cleaned = value
            .replace("€", "")
            .replace("EUR", "", ignoreCase = true)
            .trim()
        if (cleaned.isBlank()) return null
        val decimalNormalized = when {
            cleaned.contains(",") && cleaned.contains(".") ->
                cleaned.replace(".", "").replace(",", ".")
            cleaned.contains(",") -> cleaned.replace(",", ".")
            else -> cleaned
        }
        return decimalNormalized.filter { it.isDigit() || it == '.' || it == '-' || it == '+' }.toDoubleOrNull()
    }

    private fun parseType(value: String): TransactionType? = when (value.trim().lowercase()) {
        "income", "ingreso", "credit", "abono" -> TransactionType.INCOME
        "expense", "gasto", "debit", "cargo" -> TransactionType.EXPENSE
        else -> null
    }

    private fun parseStatement(raw: String): ParsedCsv = when {
        raw.contains("STMTTRN", ignoreCase = true) -> parseOfx(raw)
        raw.looksLikeIngStatement() -> parseIngStatement(raw)
        else -> parseCsv(raw)
    }

    private fun parseCsv(raw: String): ParsedCsv {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size >= 2) { "CSV needs a header and at least one row" }
        val delimiter = detectDelimiter(lines.first())
        val headers = splitCsvLine(lines.first(), delimiter).mapIndexed { index, header ->
            header.ifBlank { "Column ${index + 1}" }
        }
        val rows = lines.drop(1).map { line ->
            val cells = splitCsvLine(line, delimiter)
            headers.associateWith { header ->
                cells.getOrNull(headers.indexOf(header)).orEmpty()
            }
        }
        return ParsedCsv(headers, rows)
    }

    private fun parseIngStatement(raw: String): ParsedCsv {
        parseIngDelimitedStatement(raw)?.let { return it }

        val headers = listOf("date", "category", "description", "amount")
        val lines = raw.lineSequence().map { it.trim() }.toList()
        val rows = mutableListOf<Map<String, String>>()
        var index = lines.indexOfFirst { it.equals("F. VALOR", ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0

        while (index < lines.size) {
            val date = lines[index].takeIf { it.isIngDate() }
            if (date == null) {
                index++
                continue
            }
            index++

            val category = lines.nextNonBlank(index)?.also { index = it.nextIndex }?.value.orEmpty()
            val subcategory = lines.nextNonBlank(index)?.also { index = it.nextIndex }?.value.orEmpty()
            val descriptionParts = mutableListOf<String>()
            while (index < lines.size) {
                val line = lines[index]
                when {
                    line.isBlank() -> {
                        index++
                        break
                    }
                    line.isSpanishAmount() || line.isIngDate() -> break
                    else -> {
                        descriptionParts += line
                        index++
                    }
                }
            }

            val commentParts = mutableListOf<String>()
            while (index < lines.size) {
                val line = lines[index]
                when {
                    line.isBlank() -> index++
                    line.isSpanishAmount() || line.isIngDate() -> break
                    else -> {
                        commentParts += line
                        index++
                    }
                }
            }

            val amount = lines.nextNonBlank(index)?.also { index = it.nextIndex }?.value.orEmpty()
            val balance = lines.nextNonBlank(index)?.also { index = it.nextIndex }?.value.orEmpty()
            if (amount.isSpanishAmount() && balance.isSpanishAmount()) {
                val description = (descriptionParts + commentParts).joinToString(" ").trim()
                rows += mapOf(
                    "date" to date,
                    "category" to buildIngCategory(category, subcategory),
                    "description" to description,
                    "amount" to amount,
                )
            }
        }
        require(rows.isNotEmpty()) { "ING statement contains no transactions" }
        return ParsedCsv(headers, rows)
    }

    private fun parseIngDelimitedStatement(raw: String): ParsedCsv? {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val headerIndex = lines.indexOfFirst { line ->
            val delimiter = detectDelimiter(line)
            val cells = splitCsvLine(line, delimiter).map { it.normalized() }
            "fvalor" in cells && "categoria" in cells && "subcategoria" in cells &&
                "descripcion" in cells && "importe" in cells && "saldo" in cells
        }
        if (headerIndex < 0) return null

        val delimiter = detectDelimiter(lines[headerIndex])
        val sourceHeaders = splitCsvLine(lines[headerIndex], delimiter).map { it.normalized() }
        fun indexOf(header: String): Int = sourceHeaders.indexOf(header)
        val dateIndex = indexOf("fvalor")
        val categoryIndex = indexOf("categoria")
        val subcategoryIndex = indexOf("subcategoria")
        val descriptionIndex = indexOf("descripcion")
        val commentIndex = indexOf("comentario")
        val amountIndex = indexOf("importe")

        val rows = lines.drop(headerIndex + 1).mapNotNull { line ->
            val cells = splitCsvLine(line, delimiter)
            val date = cells.getOrNull(dateIndex).orEmpty()
            val amount = cells.getOrNull(amountIndex).orEmpty()
            if (!date.isIngDate() || !amount.isSpanishAmount()) return@mapNotNull null

            val category = cells.getOrNull(categoryIndex).orEmpty()
            val subcategory = cells.getOrNull(subcategoryIndex).orEmpty()
            val description = listOf(
                cells.getOrNull(descriptionIndex).orEmpty(),
                cells.getOrNull(commentIndex).orEmpty(),
            ).filter { it.isNotBlank() }.joinToString(" ")

            mapOf(
                "date" to date,
                "category" to buildIngCategory(category, subcategory),
                "description" to description,
                "amount" to amount,
            )
        }
        require(rows.isNotEmpty()) { "ING statement contains no transactions" }
        return ParsedCsv(headers = listOf("date", "category", "description", "amount"), rows = rows)
    }

    private fun parseOfx(raw: String): ParsedCsv {
        val headers = listOf("date", "amount", "description", "type")
        val blocks = Regex("""<STMTTRN>(.*?)</STMTTRN>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()
        require(blocks.isNotEmpty()) { "OFX contains no transactions" }
        val rows = blocks.map { block ->
            val amount = ofxTag(block, "TRNAMT").orEmpty()
            val type = when (ofxTag(block, "TRNTYPE")?.lowercase()) {
                "credit", "dep", "directdep", "int" -> "income"
                else -> "expense"
            }
            mapOf(
                "date" to ofxTag(block, "DTPOSTED").toOfxDate(),
                "amount" to amount,
                "description" to listOfNotNull(ofxTag(block, "NAME"), ofxTag(block, "MEMO"))
                    .distinct()
                    .joinToString(" ")
                    .trim(),
                "type" to type,
            )
        }
        return ParsedCsv(headers, rows)
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && line.getOrNull(i + 1) == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun detectDelimiter(header: String): Char {
        return listOf(',', ';', '\t').maxBy { delimiter -> header.count { it == delimiter } }
    }

    private fun ofxTag(block: String, tag: String): String? {
        Regex("""<$tag>(.*?)</$tag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { return it }
        return Regex("""<$tag>([^\r\n<]*)""", RegexOption.IGNORE_CASE)
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    private fun String?.toOfxDate(): String {
        val value = this?.take(8).orEmpty()
        if (value.length != 8) return orEmpty()
        return "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
    }

    private fun guessMapping(headers: List<String>): Map<CsvColumnRole, String?> =
        CsvColumnRole.entries.associateWith { role ->
            headers.firstOrNull { header -> role.matches(header) }
        }

    private fun CsvColumnRole.matches(header: String): Boolean {
        val normalized = header.normalized()
        return when (this) {
            CsvColumnRole.DATE -> normalized in setOf("date", "fecha", "bookingdate", "operationdate")
            CsvColumnRole.AMOUNT -> normalized in setOf("amount", "importe", "monto", "value", "valor")
            CsvColumnRole.DESCRIPTION -> normalized in setOf("description", "descripcion", "concept", "concepto", "payee", "merchant")
            CsvColumnRole.CATEGORY -> normalized in setOf("category", "categoria")
            CsvColumnRole.TYPE -> normalized in setOf("type", "tipo")
            CsvColumnRole.ACCOUNT -> normalized in setOf("account", "cuenta")
        }
    }

    private fun String.normalized(): String =
        trim().lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace(Regex("""[^a-z0-9]"""), "")

    private fun cents(value: Double): Long = kotlin.math.round(value * 100).toLong()

    private fun String.looksLikeIngStatement(): Boolean {
        val normalized = normalized()
        return listOf("fvalor", "categoria", "subcategoria", "descripcion", "importe", "saldo")
            .all { token -> token in normalized }
    }

    private fun String.isIngDate(): Boolean =
        Regex("""\d{2}/\d{2}/\d{4}""").matches(trim())

    private fun String.isSpanishAmount(): Boolean =
        Regex("""[+-]?(?:\d{1,3}(?:\.\d{3})+|\d+),\d{2}""").matches(trim())

    private fun List<String>.nextNonBlank(startIndex: Int): IndexedText? {
        var index = startIndex
        while (index < size) {
            val value = this[index].trim()
            index++
            if (value.isNotBlank()) return IndexedText(value = value, nextIndex = index)
        }
        return null
    }

    private fun buildIngCategory(category: String, subcategory: String): String = when {
        category.isBlank() -> subcategory
        subcategory.isBlank() || category.equals(subcategory, ignoreCase = true) -> category
        else -> "$category · $subcategory"
    }

    private fun ParsedTransaction.duplicateKey(): ImportDuplicateKey =
        ImportDuplicateKey(
            accountId = accountId,
            date = date.formatDate(),
            type = type,
            amountCents = cents(amount),
            description = description?.normalized().orEmpty(),
        )

    private fun Transaction.duplicateKey(): ImportDuplicateKey =
        ImportDuplicateKey(
            accountId = accountId,
            date = date.formatDate(),
            type = type,
            amountCents = cents(amount),
            description = description?.normalized().orEmpty(),
        )

    private data class ParsedTransaction(
        val accountId: Long,
        val type: TransactionType,
        val amount: Double,
        val category: String?,
        val description: String?,
        val date: Long,
    )

    private data class ImportDuplicateKey(
        val accountId: Long,
        val date: String,
        val type: TransactionType,
        val amountCents: Long,
        val description: String,
    )

    private data class IndexedText(
        val value: String,
        val nextIndex: Int,
    )
}
