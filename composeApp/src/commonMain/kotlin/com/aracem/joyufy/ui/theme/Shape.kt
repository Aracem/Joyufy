package com.aracem.joyufy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val JoyufyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),  // Tags, badges
    small = RoundedCornerShape(8.dp),       // Inputs, chips
    medium = RoundedCornerShape(12.dp),     // Cards
    large = RoundedCornerShape(16.dp),      // Bottom sheets, dialogs
    extraLarge = RoundedCornerShape(24.dp), // Modals grandes
)
