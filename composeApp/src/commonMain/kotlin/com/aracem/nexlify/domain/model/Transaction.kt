package com.aracem.nexlify.domain.model

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

// Suggested categories — user can also type freely
enum class TransactionCategory(val label: String) {
    SALARY("Nómina"),
    FREELANCE("Freelance"),
    RENT_INCOME("Alquiler cobrado"),
    GROCERIES("Supermercado"),
    RENT_EXPENSE("Alquiler pagado"),
    UTILITIES("Suministros"),
    TRANSPORT("Transporte"),
    DINING("Restaurantes"),
    SUBSCRIPTIONS("Suscripciones"),
    HEALTH("Salud"),
    EDUCATION("Formación"),
    TRAVEL("Viajes"),
    SHOPPING("Compras"),
    INVESTMENT("Inversión"),
    OTHER("Otros"),
}

data class Transaction(
    val id: Long,
    val accountId: Long,
    val type: TransactionType,
    val amount: Double,
    val category: String?,
    val description: String?,
    val relatedAccountId: Long?,
    val date: Long,
)
