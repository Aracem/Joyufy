package com.aracem.nexlify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aracem.nexlify.domain.model.Account
import com.aracem.nexlify.domain.model.AccountType
import com.aracem.nexlify.ui.theme.ContentSecondary
import com.aracem.nexlify.ui.theme.Negative
import com.aracem.nexlify.ui.theme.Positive
import com.aracem.nexlify.ui.theme.SurfaceRaised

@Composable
fun AccountCard(
    account: Account,
    balance: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(account.color)
        )

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
                color = ContentSecondary,
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
