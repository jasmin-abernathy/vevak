/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.location.LocationSource
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.VeVakSettings
import com.vevak.app.sms.SmsReplyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationReplyTest {
    private val settings = VeVakSettings(includeAccuracy = true)

    @Test
    fun automaticReply_includesResolvedAddressWhenAvailable() {
        val location = VeVakLocationSnapshot(
            latitude = 49.1193,
            longitude = 6.1757,
            accuracyMeters = 24f,
            source = LocationSource.AndroidCurrent,
            ageMillis = 0L,
            isMocked = false,
            address = "12 rue Exemple, 57000 Metz, France"
        )

        val body = SmsReplyFormatter.format(settings, location, null)

        assertTrue(body.contains("Adresse approx. : 12 rue Exemple, 57000 Metz, France"))
        assertTrue(body.contains("49.1193"))
        assertTrue(body.contains("6.1757"))
    }

    @Test
    fun manualReply_keepsCoordinatesWhenNoAddressWasResolved() {
        val location = VeVakLocationSnapshot(
            latitude = 49.1193,
            longitude = 6.1757,
            accuracyMeters = 24f,
            source = LocationSource.AndroidCurrent,
            ageMillis = 0L,
            isMocked = false
        )

        val body = SmsReplyFormatter.formatManualShare(settings, location, null)

        assertFalse(body.contains("Adresse approx."))
        assertTrue(body.contains("49.1193"))
        assertTrue(body.contains("6.1757"))
    }

    @Test
    fun trustedHomeReply_isExactlyTheRequestedSentence() {
        assertEquals("Je suis à la maison", SmsReplyFormatter.formatTrustedPlace())
    }
}
