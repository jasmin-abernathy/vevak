/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.protectionPromptDataStore by preferencesDataStore(name = "vevak_protection_prompt")

/**
 * Stores only coarse local counters keyed by VeVak contact ids. It never stores SMS bodies,
 * phone numbers, coordinates or the protection phrase.
 */
class ProtectionPromptRepository(private val context: Context) {
    private object Keys {
        val COUNTS = stringPreferencesKey("normal_reply_counts_v1")
        val DISMISSED = stringPreferencesKey("dismissed_contacts_v1")
    }

    suspend fun recordSuccessfulNormalReply(contactId: String) {
        if (contactId.isBlank()) return
        context.protectionPromptDataStore.edit { prefs ->
            val counts = decodeCounts(prefs[Keys.COUNTS].orEmpty()).toMutableMap()
            counts[contactId] = ((counts[contactId] ?: 0) + 1).coerceAtMost(2)
            prefs[Keys.COUNTS] = encodeCounts(counts)
        }
    }

    suspend fun firstEligibleContactId(): String? {
        val prefs = context.protectionPromptDataStore.data.first()
        val dismissed = decodeIds(prefs[Keys.DISMISSED].orEmpty())
        return decodeCounts(prefs[Keys.COUNTS].orEmpty())
            .entries
            .firstOrNull { (contactId, count) -> count >= 2 && contactId !in dismissed }
            ?.key
    }

    suspend fun dismiss(contactId: String) {
        if (contactId.isBlank()) return
        context.protectionPromptDataStore.edit { prefs ->
            val dismissed = decodeIds(prefs[Keys.DISMISSED].orEmpty()).toMutableSet()
            dismissed += contactId
            prefs[Keys.DISMISSED] = dismissed.joinToString("\n")
        }
    }

    suspend fun clear() {
        context.protectionPromptDataStore.edit { it.clear() }
    }

    private fun encodeCounts(values: Map<String, Int>): String =
        values.entries.joinToString("\n") { (id, count) -> "$id,$count" }

    private fun decodeCounts(raw: String): Map<String, Int> = raw.lineSequence()
        .mapNotNull { line ->
            val parts = line.split(',', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0].trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val count = parts[1].toIntOrNull()?.coerceIn(0, 2) ?: return@mapNotNull null
            id to count
        }
        .toMap()

    private fun decodeIds(raw: String): Set<String> = raw.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()
}
