package com.aracem.joyufy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val JoyufyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),  // Tags, badges
    small = RoundedCornerShape(12.dp),       // Inputs, chips
    medium = RoundedCornerShape(20.dp),     // Cards
    large = RoundedCornerShape(24.dp),      // Bottom sheets, dialogs
    extraLarge = RoundedCornerShape(28.dp), // Modals grandes
)
