/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.location.LocationSource
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.MapProvider
import com.vevak.app.model.VeVakSettings
import com.vevak.app.sms.SmsReplyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationReplyTest {
    private val settings = VeVakSettings(includeAccuracy = true, mapProvider = MapProvider.OpenStreetMap)

    @Test
    fun normalCoordinateReply_usesLastKnownUrlRadiusAndBatteryOnly() {
        val location = VeVakLocationSnapshot(
            latitude = 49.1193,
            longitude = 6.1757,
            accuracyMeters = 24f,
            source = LocationSource.AndroidCurrent,
            ageMillis = 0L,
            isMocked = false,
            address = "12 rue Exemple, 57000 Metz, France"
        )

        val body = SmsReplyFormatter.formatWithBatteryLabel(settings, location, "Batterie : 49 %")

        assertTrue(body.contains("Dernière position connue"))
        assertTrue(body.contains("openstreetmap.org"))
        assertTrue(body.contains("49.119300"))
        assertTrue(body.contains("6.175700"))
        assertTrue(body.contains("Rayon approximatif : env. 24 m"))
        assertTrue(body.contains("Batterie : 49 %"))
        assertFalse(body.contains("Adresse approx."))
    }

    @Test
    fun manualCoordinateReply_usesTheSamePayloadContract() {
        val location = VeVakLocationSnapshot(
            latitude = 49.1193,
            longitude = 6.1757,
            accuracyMeters = 24f,
            source = LocationSource.AndroidCurrent,
            ageMillis = 0L,
            isMocked = false
        )

        val body = SmsReplyFormatter.formatManualShareWithBatteryLabel(settings, location, "Batterie en charge")

        assertTrue(body.contains("Dernière position connue"))
        assertTrue(body.contains("Rayon approximatif : env. 24 m"))
        assertTrue(body.contains("Batterie en charge"))
    }

    @Test
    fun networkApproximation_isExplicitlyMarkedAsEstimate() {
        val location = VeVakLocationSnapshot(
            latitude = 49.1,
            longitude = 6.2,
            accuracyMeters = 12_000f,
            source = LocationSource.NetworkApproximation,
            ageMillis = 0L,
            isMocked = false
        )

        val body = SmsReplyFormatter.format(settings, location, null)

        assertTrue(body.contains("Dernière position connue (estimation réseau)"))
        assertTrue(body.contains("12.0 km"))
    }

    @Test
    fun networkApproximation_isOptInByDefault() {
        assertFalse(VeVakSettings().allowNetworkApproximation)
    }

    @Test
    fun trustedHomeReply_isExactlyTheRequestedSentence() {
        assertEquals("Je suis chez moi", SmsReplyFormatter.formatTrustedPlace())
    }

    @Test
    fun trustedHomeReply_canIncludeChargingState() {
        assertEquals(
            "Je suis chez moi\nBatterie en charge",
            SmsReplyFormatter.formatTrustedPlaceWithBattery("Maison", "Batterie en charge", true)
        )
    }

    @Test
    fun manualTrustedPlace_doesNotInventCoordinates() {
        val body = SmsReplyFormatter.formatManualTrustedPlaceWithBattery("Maison", "Batterie : 48 %", true)
        assertTrue(body.contains("Je suis chez moi"))
        assertTrue(body.contains("Batterie : 48 %"))
        assertFalse(body.contains("http"))
    }
}
