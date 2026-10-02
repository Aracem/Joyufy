package com.aracem.joyufy.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CategorizationRule(
    val id: Long,
    val contains: String,
    val category: String,
    val accountId: Long? = null,
) {
    fun matches(description: String, candidateAccountId: Long): Boolean =
        contains.isNotBlank() &&
            description.contains(contains, ignoreCase = true) &&
            (accountId == null || accountId == candidateAccountId)
}

fun List<CategorizationRule>.categoryFor(description: String, accountId: Long): String? =
    firstOrNull { it.matches(description, accountId) }?.category
