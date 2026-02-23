package com.aracem.joyufy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.net.URL

/**
 * Shows the account color dot always, with the bank logo overlaid when available.
 * [size] controls the overall size; the logo is slightly smaller inside.
 */
@Composable
fun AccountLogo(
    color: Color,
    logoUrl: String?,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Always show color dot as background
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )

        // Overlay logo if available
        if (logoUrl != null) {
            val logoSize = (size.value * 0.78f).dp
            RemoteImage(
                url = logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(logoSize)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URL(url).openStream().use { it.readBytes() }
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
