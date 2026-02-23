package com.aracem.joyufy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import joyufy.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Shows the account color circle always, with the bank logo overlaid when available.
 * Falls back to the account initials when no logo resource is found.
 */
@Composable
fun AccountLogo(
    color: Color,
    logoUrl: String?,       // stores the drawable resource name e.g. "logo_santander"
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Always show color circle as background
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )

        val res = logoUrl?.toDrawableResource()
        if (res != null) {
            val logoSize = (size.value * 0.78f).dp
            androidx.compose.foundation.Image(
                painter = painterResource(res),
                contentDescription = null,
                modifier = Modifier
                    .size(logoSize)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * Shows initials (1-2 chars) of [name] on top of the color circle.
 * Used directly when no preset logo exists.
 */
@Composable
fun AccountLogoInitials(
    color: Color,
    name: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    val initials = name
        .split(" ", "-")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

    val fontSize = (size.value * 0.36f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = fontSize,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize),
        )
    }
}

/** Maps a stored resource name string to the generated DrawableResource, or null if unknown. */
private fun String.toDrawableResource(): DrawableResource? = when (this) {
    "logo_santander"     -> Res.drawable.logo_santander
    "logo_bbva"          -> Res.drawable.logo_bbva
    "logo_caixabank"     -> Res.drawable.logo_caixabank
    "logo_bankinter"     -> Res.drawable.logo_bankinter
    "logo_sabadell"      -> Res.drawable.logo_sabadell
    "logo_ing"           -> Res.drawable.logo_ing
    "logo_unicaja"       -> Res.drawable.logo_unicaja
    "logo_kutxabank"     -> Res.drawable.logo_kutxabank
    "logo_abanca"        -> Res.drawable.logo_abanca
    "logo_openbank"      -> Res.drawable.logo_openbank
    "logo_n26"           -> Res.drawable.logo_n26
    "logo_revolut"       -> Res.drawable.logo_revolut
    "logo_myinvestor"    -> Res.drawable.logo_myinvestor
    "logo_degiro"        -> Res.drawable.logo_degiro
    "logo_etoro"         -> Res.drawable.logo_etoro
    "logo_traderepublic" -> Res.drawable.logo_traderepublic
    else                 -> null
}
