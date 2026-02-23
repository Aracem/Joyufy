package com.aracem.joyufy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.Positive
import com.aracem.joyufy.ui.theme.joyufyColors

@Composable
fun AccountCard(
    account: Account,
    balance: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .shadow(if (isDragging) 8.dp else 0.dp, MaterialTheme.shapes.medium)
            .background(
                if (isDragging) MaterialTheme.joyufyColors.surfaceRaised.copy(alpha = 0.95f)
                else MaterialTheme.joyufyColors.surfaceRaised
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(8.dp))
        }

        if (account.logoUrl != null) {
            AccountLogo(color = account.color, logoUrl = account.logoUrl, size = 36.dp)
        } else {
            AccountLogoInitials(color = account.color, name = account.name, size = 36.dp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = account.type.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
        }

        Text(
            text = balance.formatCurrency(),
            style = MaterialTheme.typography.titleMedium,
            color = if (balance >= 0) Positive else Negative,
        )
    }
}

private val AccountType.label: String
    get() = when (this) {
        AccountType.BANK -> "Banco"
        AccountType.INVESTMENT -> "Inversión"
        AccountType.CASH -> "Efectivo"
    }
