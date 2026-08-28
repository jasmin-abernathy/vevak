/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.data

import com.vevak.app.model.TrustedContact
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Compact local persistence codec for additional trusted contacts.
 *
 * Preferences DataStore does not provide a structured-record type. Each field
 * is therefore URL-safe Base64 encoded before records are joined. The codec is
 * deliberately private/local persistence, not a public interchange format.
 */
object TrustedContactStorageCodec {
    private const val FIELD_SEPARATOR = "|"
    private const val RECORD_SEPARATOR = "\n"
    private const val FIELD_COUNT = 6

    fun encode(contacts: List<TrustedContact>): String = contacts.joinToString(RECORD_SEPARATOR) { contact ->
        listOf(
            encodeString(contact.id),
            encodeString(contact.name),
            encodeString(contact.phone),
            encodeString(contact.triggerPhrase),
            contact.authorizationGrantedAtEpochMs.coerceAtLeast(0L).toString(),
            contact.authorizationExpiresAtEpochMs.coerceAtLeast(0L).toString()
        ).joinToString(FIELD_SEPARATOR)
    }

    fun decode(value: String): List<TrustedContact> {
        if (value.isBlank()) return emptyList()
        return value.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::decodeRecord)
            .distinctBy { it.id }
            .toList()
    }

    private fun decodeRecord(record: String): TrustedContact? {
        val fields = record.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) return null
        return runCatching {
            TrustedContact(
                id = decodeString(fields[0]).takeIf { it.isNotBlank() } ?: return null,
                name = decodeString(fields[1]),
                phone = decodeString(fields[2]),
                triggerPhrase = decodeString(fields[3]),
                authorizationGrantedAtEpochMs = fields[4].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                authorizationExpiresAtEpochMs = fields[5].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            )
        }.getOrNull()
    }

    private fun encodeString(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeString(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
