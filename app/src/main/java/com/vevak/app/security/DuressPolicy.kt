/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.security

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

    fun configurationIsValid(settings: VeVakSettings): Boolean =
        !settings.duressEnabled ||
            (phrasesAreDistinctEnough(settings.triggerPhrase, settings.duressPhrase) &&
                coordinatesAreValid(settings.fallbackLatitude, settings.fallbackLongitude))

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
    fun resolve(messageBody: String, settings: VeVakSettings): IncomingRequestMode? {
        // Fail safe: the safety phrase always wins if both phrases ever become equal through
        // corrupted/legacy settings. The handler will then refuse to touch the real GPS path.
        if (settings.duressEnabled && SmsCommandParser.matches(messageBody, settings.duressPhrase)) {
            return IncomingRequestMode.Duress
        }
        if (SmsCommandParser.matches(messageBody, settings.triggerPhrase)) {
            return IncomingRequestMode.Normal
        }
        return null
    }
}
