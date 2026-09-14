/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import com.vevak.app.R
import java.util.UUID

enum class EmergencyShortcutPreset(val label: String, val description: String, @DrawableRes val iconRes: Int) {
    Notes("Notes", "Carnet à spirales", R.drawable.ic_shortcut_notes),
    Liste("Liste", "Checklist simple", R.drawable.ic_shortcut_list),
    Horaires("Horaires", "Horloge bleue", R.drawable.ic_shortcut_clock),
    Dossier("Dossier", "Dossier générique", R.drawable.ic_shortcut_folder),
    Outils("Outils", "Boîte à outils", R.drawable.ic_shortcut_tools),
    Memos("Mémos", "Note jaune", R.drawable.ic_shortcut_memo)
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
        val shortcut = ShortcutInfo.Builder(appContext, SHORTCUT_ID)
            .setShortLabel(preset.label)
            .setLongLabel(preset.label)
            .setDisabledMessage("Raccourci indisponible.")
            .setIcon(Icon.createWithResource(appContext, preset.iconRes))
            .setIntent(target)
            .build()
        prefs.edit().putString(KEY_PRESET, preset.name).apply()
        return manager.requestPinShortcut(shortcut, null)
    }

    fun isValidToken(candidate: String?): Boolean = !candidate.isNullOrBlank() && candidate == prefs.getString(KEY_TOKEN, null)
    fun selectedPreset(): EmergencyShortcutPreset = runCatching { EmergencyShortcutPreset.valueOf(prefs.getString(KEY_PRESET, null).orEmpty()) }.getOrDefault(EmergencyShortcutPreset.Notes)

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
        const val SHORTCUT_ID = "vevak_discreet_emergency"
    }
}
