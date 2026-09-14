/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.vevak.app.R
import java.util.UUID
import kotlin.math.roundToInt

enum class EmergencyShortcutPreset(
    val label: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val backgroundColor: Int
) {
    Notes("Notes", "Carnet à spirales", R.drawable.ic_shortcut_notes, 0xFFF5F0E8.toInt()),
    Liste("Liste", "Checklist simple", R.drawable.ic_shortcut_list, 0xFFE8F4EA.toInt()),
    Horaires("Horaires", "Horloge bleue", R.drawable.ic_shortcut_clock, 0xFFE7F0F7.toInt()),
    Dossier("Dossier", "Dossier générique", R.drawable.ic_shortcut_folder, 0xFFF7E7C6.toInt()),
    Outils("Outils", "Boîte à outils", R.drawable.ic_shortcut_tools, 0xFFE8ECEF.toInt()),
    Memos("Mémos", "Note jaune", R.drawable.ic_shortcut_memo, 0xFFF7EFB7.toInt())
}

class EmergencyShortcutManager(context: Context) {
    private val appContext = context.applicationContext
    private val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSupported(): Boolean = shortcutManager?.isRequestPinShortcutSupported == true

    fun requestPin(preset: EmergencyShortcutPreset): Boolean {
        val manager = shortcutManager ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val token = existingOrNewToken()
        val target = Intent(appContext, EmergencyShortcutActivity::class.java).apply {
            action = EmergencyShortcutActivity.ACTION_TOGGLE_EMERGENCY
            putExtra(EmergencyShortcutActivity.EXTRA_SHORTCUT_TOKEN, token)
        }
        val shortcut = ShortcutInfo.Builder(appContext, shortcutIdFor(preset))
            .setShortLabel(preset.label)
            .setLongLabel(preset.label)
            .setDisabledMessage("Raccourci indisponible.")
            .setIcon(createAdaptiveLauncherIcon(preset))
            .setIntent(target)
            .build()
        prefs.edit().putString(KEY_PRESET, preset.name).apply()
        return manager.requestPinShortcut(shortcut, null)
    }

    fun isValidToken(candidate: String?): Boolean =
        !candidate.isNullOrBlank() && candidate == prefs.getString(KEY_TOKEN, null)

    fun selectedPreset(): EmergencyShortcutPreset = runCatching {
        EmergencyShortcutPreset.valueOf(prefs.getString(KEY_PRESET, null).orEmpty())
    }.getOrDefault(EmergencyShortcutPreset.Notes)

    /**
     * Render the licensed Streamline vector into an adaptive bitmap instead of asking launchers to
     * interpret a VectorDrawable directly. Some launchers cache or reshape shortcut resources in
     * surprising ways; the 108 dp canvas / 72 dp safe content follows Android shortcut guidance.
     */
    private fun createAdaptiveLauncherIcon(preset: EmergencyShortcutPreset): Icon {
        val density = appContext.resources.displayMetrics.density.coerceAtLeast(1f)
        val canvasSize = (108f * density).roundToInt().coerceAtLeast(108)
        val contentSize = (72f * density).roundToInt().coerceAtMost(canvasSize)
        val inset = (canvasSize - contentSize) / 2
        val drawable = ContextCompat.getDrawable(appContext, preset.iconRes)?.mutate()
            ?: return Icon.createWithResource(appContext, preset.iconRes)

        val bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(preset.backgroundColor)
        drawable.setBounds(inset, inset, inset + contentSize, inset + contentSize)
        drawable.draw(canvas)
        return Icon.createWithAdaptiveBitmap(bitmap)
    }

    private fun existingOrNewToken(): String {
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_TOKEN, created).apply()
        return created
    }

    private companion object {
        const val PREFS = "vevak_emergency_shortcut"
        const val KEY_TOKEN = "shortcut_token"
        const val KEY_PRESET = "shortcut_preset"

        // Bump this only when the launcher icon representation itself changes. Pinned shortcuts are
        // launcher-owned; a new stable ID prevents an old cached Papirus/vector representation from
        // being silently reused while keeping already-pinned shortcuts functional.
        const val SHORTCUT_ICON_SCHEMA = 2

        fun shortcutIdFor(preset: EmergencyShortcutPreset): String =
            "vevak_discreet_emergency_v${SHORTCUT_ICON_SCHEMA}_${preset.name.lowercase()}"
    }
}
