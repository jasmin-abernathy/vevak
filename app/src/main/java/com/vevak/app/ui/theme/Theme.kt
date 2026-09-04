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

// VeVak keeps its own blue/teal/warm identity. The new surfaces borrow the visual rhythm of a
// modern safety dashboard (large status cards, deep layered dark surfaces) without copying the
// reference application's palette or component layout.
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
    primaryContainer = Color(0xFFDDE7FF),
    onPrimaryContainer = Color(0xFF18325F),
    secondaryContainer = Color(0xFFD9F3EF),
    onSecondaryContainer = Color(0xFF123F3D),
    tertiaryContainer = Color(0xFFFFE4DA),
    onTertiaryContainer = Color(0xFF633326),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF17202B),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBF0F6),
    onSurface = Color(0xFF17202B),
    onSurfaceVariant = Color(0xFF526171),
    outline = Color(0xFF7D8A99),
    outlineVariant = Color(0xFFD4DCE5)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAFC7FF),
    onPrimary = Color(0xFF14376D),
    secondary = Color(0xFF78D8D1),
    onSecondary = Color(0xFF073B39),
    tertiary = Color(0xFFFFB7A0),
    onTertiary = Color(0xFF5B291B),
    primaryContainer = Color(0xFF233C68),
    onPrimaryContainer = Color(0xFFE2EBFF),
    secondaryContainer = Color(0xFF153D3B),
    onSecondaryContainer = Color(0xFFC9F3EF),
    tertiaryContainer = Color(0xFF563328),
    onTertiaryContainer = Color(0xFFFFE0D5),
    background = Color(0xFF0D1118),
    onBackground = Color(0xFFE8EDF5),
    surface = Color(0xFF151B25),
    surfaceVariant = Color(0xFF1D2633),
    onSurface = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFFBBC6D3),
    outline = Color(0xFF667487),
    outlineVariant = Color(0xFF2D3948)
)

private val VeVakShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(30.dp)
)

@Composable
fun VeVakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = VeVakShapes,
        content = content
    )
}
