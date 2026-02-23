package com.aracem.joyufy.data.mapper

import androidx.compose.ui.graphics.Color
import com.aracem.joyufy.db.Account as AccountEntity
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.domain.model.AccountType

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = AccountType.valueOf(type),
    color = color_hex.toComposeColor(),
    logoUrl = logo_url,
    position = position.toInt(),
    createdAt = created_at,
)

fun Account.toColorHex(): String {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

/** Parses #RRGGBB or #AARRGGBB into a Compose Color. */
fun String.toComposeColor(): Color {
    val hex = removePrefix("#")
    return when (hex.length) {
        6 -> Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f,
        )
        8 -> Color(
            alpha = hex.substring(0, 2).toInt(16) / 255f,
            red = hex.substring(2, 4).toInt(16) / 255f,
            green = hex.substring(4, 6).toInt(16) / 255f,
            blue = hex.substring(6, 8).toInt(16) / 255f,
        )
        else -> Color.Gray
    }
}
