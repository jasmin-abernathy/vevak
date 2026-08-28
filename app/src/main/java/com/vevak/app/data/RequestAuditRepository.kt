/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class RequestAuditOutcome(val label: String) {
    Replied("Réponse envoyée"),
    Unavailable("Position indisponible"),
    BlockedRate("Demande bloquée par la limite anti-suivi"),
    BlockedVisibility("Demande bloquée : notifications désactivées"),
    BlockedAuthorization("Demande ignorée : autorisation expirée ou révoquée"),
    SendFailed("Échec d'envoi de la réponse")
}

data class RequestAuditEvent(
    val timestampMillis: Long,
    val outcome: RequestAuditOutcome
)

private val Context.auditDataStore by preferencesDataStore(name = "vevak_request_audit")

class RequestAuditRepository(private val context: Context) {
    private object Keys {
        val EVENTS = stringPreferencesKey("events")
    }

    val eventsFlow: Flow<List<RequestAuditEvent>> = context.auditDataStore.data.map { prefs ->
        decode(prefs[Keys.EVENTS].orEmpty())
    }

    suspend fun append(timestampMillis: Long, outcome: RequestAuditOutcome) {
        if (timestampMillis <= 0L) return
        context.auditDataStore.edit { prefs ->
            val events = listOf(RequestAuditEvent(timestampMillis, outcome)) +
                decode(prefs[Keys.EVENTS].orEmpty())
            prefs[Keys.EVENTS] = encode(events.take(MAX_EVENTS))
        }
    }

    suspend fun clear() {
        context.auditDataStore.edit { it.clear() }
    }

    private fun encode(events: List<RequestAuditEvent>): String =
        events.joinToString("\n") { "${it.timestampMillis},${it.outcome.name}" }

    private fun decode(raw: String): List<RequestAuditEvent> = raw.lineSequence()
        .mapNotNull { line ->
            val parts = line.split(',', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
            val outcome = runCatching { RequestAuditOutcome.valueOf(parts[1]) }.getOrNull()
                ?: return@mapNotNull null
            RequestAuditEvent(timestamp, outcome)
        }
        .filter { it.timestampMillis > 0L }
        .take(MAX_EVENTS)
        .toList()

    private companion object {
        const val MAX_EVENTS = 20
    }
}
