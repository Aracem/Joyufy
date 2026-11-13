package com.aracem.joyufy.ui.strings

import com.aracem.joyufy.domain.model.TransactionCategory

/** Localizes built-in labels for display while leaving stored category strings untouched. */
fun String.localizedCategoryLabel(strings: Strings): String {
    val category = TransactionCategory.entries.firstOrNull { it.label.equals(this, ignoreCase = true) }
    return when (category) {
        TransactionCategory.SALARY -> strings.categorySalary
        TransactionCategory.FREELANCE -> strings.categoryFreelance
        TransactionCategory.RENT_INCOME -> strings.categoryRentIncome
        TransactionCategory.GROCERIES -> strings.categoryGroceries
        TransactionCategory.RENT_EXPENSE -> strings.categoryRentExpense
        TransactionCategory.UTILITIES -> strings.categoryUtilities
        TransactionCategory.TRANSPORT -> strings.categoryTransport
        TransactionCategory.DINING -> strings.categoryDining
        TransactionCategory.SUBSCRIPTIONS -> strings.categorySubscriptions
        TransactionCategory.HEALTH -> strings.categoryHealth
        TransactionCategory.EDUCATION -> strings.categoryEducation
        TransactionCategory.TRAVEL -> strings.categoryTravel
        TransactionCategory.SHOPPING -> strings.categoryShopping
        TransactionCategory.INVESTMENT -> strings.categoryInvestment
        TransactionCategory.OTHER -> strings.categoryOther
        null -> this
    }
}
