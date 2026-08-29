/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VeVakBlue = Color(0xFF083F8C)
val VeVakCyan = Color(0xFF20C6C9)

private val Light = lightColorScheme(
    primary = VeVakBlue,
    onPrimary = Color.White,
    secondary = VeVakCyan,
    onSecondary = Color(0xFF053A54),
    primaryContainer = Color(0xFFE7F0FF),
    onPrimaryContainer = Color(0xFF062F68),
    secondaryContainer = Color(0xFFDDF9F8),
    onSecondaryContainer = Color(0xFF073D4A),
    background = Color(0xFFF5F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F4F8),
    onSurface = Color(0xFF17212B),
    onSurfaceVariant = Color(0xFF52606D),
    outline = Color(0xFF7B8794)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9BC1FF),
    onPrimary = Color(0xFF002E6A),
    secondary = Color(0xFF66E4E5),
    onSecondary = Color(0xFF003738),
    primaryContainer = Color(0xFF0E438C),
    onPrimaryContainer = Color(0xFFD8E7FF),
    secondaryContainer = Color(0xFF0B4F51),
    onSecondaryContainer = Color(0xFFB5F4F4),
    background = Color(0xFF0E141C),
    surface = Color(0xFF151D27),
    surfaceVariant = Color(0xFF202A35),
    onSurface = Color(0xFFE7EDF5),
    onSurfaceVariant = Color(0xFFC0CAD5),
    outline = Color(0xFF8995A3)
)

@Composable
fun VeVakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
