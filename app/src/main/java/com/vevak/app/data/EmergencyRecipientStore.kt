/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.data

import android.content.Context
import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings

/**
 * Local-only emergency recipient preferences.
 *
 * Emergency is unconfigured by default. The owner must explicitly select all active contacts or a
 * local subset. Revoked or expired contacts are never reintroduced by this preference store.
 */
class EmergencyRecipientStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = prefs.getBoolean(KEY_CONFIGURED, false) ||
        // Migration: older betas wrote one of these keys only after an explicit user choice.
        prefs.contains(KEY_ALL_ACTIVE) ||
        !prefs.getStringSet(KEY_SELECTED_IDS, emptySet()).isNullOrEmpty()

    fun usesAllActiveContacts(): Boolean = isConfigured() && prefs.getBoolean(KEY_ALL_ACTIVE, false)

    fun selectedContactIds(): Set<String> =
        prefs.getStringSet(KEY_SELECTED_IDS, emptySet())?.toSet().orEmpty()

    fun setUseAllActiveContacts(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CONFIGURED, true)
            .putBoolean(KEY_ALL_ACTIVE, enabled)
            .apply()
    }

    fun setSelectedContactIds(ids: Set<String>) {
        prefs.edit()
            .putBoolean(KEY_CONFIGURED, true)
            .putBoolean(KEY_ALL_ACTIVE, false)
            .putStringSet(KEY_SELECTED_IDS, ids.toSet())
            .apply()
    }

    fun recipients(settings: VeVakSettings, nowMillis: Long = System.currentTimeMillis()): List<TrustedContact> {
        if (!isConfigured()) return emptyList()
        val active = settings.activeTrustedContacts(nowMillis)
        if (usesAllActiveContacts()) return active
        val selected = selectedContactIds()
        return active.filter { it.id in selected }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "vevak_emergency_recipients"
        const val KEY_CONFIGURED = "configured"
        const val KEY_ALL_ACTIVE = "all_active_contacts"
        const val KEY_SELECTED_IDS = "selected_contact_ids"
    }
}
