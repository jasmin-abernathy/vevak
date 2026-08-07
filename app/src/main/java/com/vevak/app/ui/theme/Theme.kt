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

private val Light = lightColorScheme(
    primary = Color(0xFF215E45),
    onPrimary = Color.White,
    secondary = Color(0xFF456B59),
    background = Color(0xFFF4F7F5),
    surface = Color.White,
    onSurface = Color(0xFF14251D),
    outline = Color(0xFF718078)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF88C8A6),
    onPrimary = Color(0xFF073522),
    secondary = Color(0xFFA5CDB8),
    background = Color(0xFF0D1712),
    surface = Color(0xFF15241C),
    onSurface = Color(0xFFE3ECE6)
)

@Composable
fun VeVakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
