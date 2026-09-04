/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.security

import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings
import com.vevak.app.sms.SmsCommandParser

object DuressPolicy {
    private const val MIN_EDIT_DISTANCE = 4

    fun phrasesAreDistinctEnough(normalPhrase: String, duressPhrase: String): Boolean {
        val normal = SmsCommandParser.normalize(normalPhrase)
        val duress = SmsCommandParser.normalize(duressPhrase)
        if (normal.length < 4 || duress.length < 4) return false
        if (normal == duress) return false
        if (normal.contains(duress) || duress.contains(normal)) return false
        return editDistance(normal, duress) >= MIN_EDIT_DISTANCE
    }

    fun coordinatesAreValid(latitude: Double?, longitude: Double?): Boolean =
        latitude?.let { it in -90.0..90.0 } == true &&
            longitude?.let { it in -180.0..180.0 } == true

    fun configurationIsValid(settings: VeVakSettings): Boolean = when {
        !settings.duressEnabled -> true
        !coordinatesAreValid(settings.fallbackLatitude, settings.fallbackLongitude) -> false
        settings.usesContactTargetedProtection() -> settings.protectedContact() != null
        settings.usesLegacyProtectionPhrase() ->
            settings.normalTriggerPhrases().isNotEmpty() &&
                settings.normalTriggerPhrases().all { phrasesAreDistinctEnough(it, settings.duressPhrase) }
        else -> false
    }

    private fun editDistance(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val substitutionCost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitutionCost
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}

enum class IncomingRequestMode { Normal, Duress }

object RequestModeResolver {
    /**
     * Preferred contact-aware path. In the current protection model, the protected contact keeps
     * exactly the normal phrase already assigned to them; only the reply path changes for that
     * sender. This avoids teaching or exposing a second phrase.
     */
    fun resolve(
        messageBody: String,
        contact: TrustedContact,
        settings: VeVakSettings
    ): IncomingRequestMode? {
        val matchesNormal = SmsCommandParser.matches(messageBody, contact.triggerPhrase)

        if (
            settings.usesContactTargetedProtection() &&
            settings.protectedContactId == contact.id &&
            matchesNormal
        ) {
            return IncomingRequestMode.Duress
        }

        // Migration compatibility only: installations configured with the earlier beta's separate
        // protection phrase keep working until the owner explicitly switches to contact targeting.
        if (settings.usesLegacyProtectionPhrase() && SmsCommandParser.matches(messageBody, settings.duressPhrase)) {
            return IncomingRequestMode.Duress
        }

        return if (matchesNormal) IncomingRequestMode.Normal else null
    }

    fun resolve(messageBody: String, settings: VeVakSettings): IncomingRequestMode? =
        resolve(messageBody, settings.primaryTrustedContact(), settings)

    /**
     * Compatibility overload used by existing unit tests and callers that only know the phrase.
     * Contact-targeted protection cannot be inferred without a contact id, so this path preserves
     * the former normal/legacy-phrase semantics only.
     */
    fun resolve(
        messageBody: String,
        normalTriggerPhrase: String,
        settings: VeVakSettings
    ): IncomingRequestMode? {
        if (settings.usesLegacyProtectionPhrase() && SmsCommandParser.matches(messageBody, settings.duressPhrase)) {
            return IncomingRequestMode.Duress
        }
        if (SmsCommandParser.matches(messageBody, normalTriggerPhrase)) {
            return IncomingRequestMode.Normal
        }
        return null
    }
}
