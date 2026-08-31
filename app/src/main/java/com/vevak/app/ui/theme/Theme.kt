/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val VeVakBlue = Color(0xFF3567C8)
val VeVakTeal = Color(0xFF1E9F9A)
val VeVakWarm = Color(0xFFD77A5C)

private val Light = lightColorScheme(
    primary = VeVakBlue,
    onPrimary = Color.White,
    secondary = VeVakTeal,
    onSecondary = Color.White,
    tertiary = VeVakWarm,
    onTertiary = Color.White,
    primaryContainer = Color(0xFFE7EEFF),
    onPrimaryContainer = Color(0xFF18366E),
    secondaryContainer = Color(0xFFDDF5F2),
    onSecondaryContainer = Color(0xFF124A48),
    tertiaryContainer = Color(0xFFFFE9E1),
    onTertiaryContainer = Color(0xFF6B3425),
    background = Color(0xFFF8FAFD),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F4F8),
    onSurface = Color(0xFF17212B),
    onSurfaceVariant = Color(0xFF52606D),
    outline = Color(0xFF84909C)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAFC7FF),
    onPrimary = Color(0xFF12366F),
    secondary = Color(0xFF7ADBD5),
    onSecondary = Color(0xFF073C3A),
    tertiary = Color(0xFFFFB59D),
    onTertiary = Color(0xFF5C2819),
    primaryContainer = Color(0xFF233F70),
    onPrimaryContainer = Color(0xFFE0E8FF),
    secondaryContainer = Color(0xFF154E4C),
    onSecondaryContainer = Color(0xFFC5F3EF),
    tertiaryContainer = Color(0xFF673727),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF101820),
    surface = Color(0xFF17212B),
    surfaceVariant = Color(0xFF222E39),
    onSurface = Color(0xFFE7EDF5),
    onSurfaceVariant = Color(0xFFC2CCD6),
    outline = Color(0xFF8E9AA6)
)

private val VeVakShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp)
)

@Composable
fun VeVakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = VeVakShapes,
        content = content
    )
}
