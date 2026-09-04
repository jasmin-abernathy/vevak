/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.location.LocationSource
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.MapProvider
import com.vevak.app.model.VeVakSettings
import com.vevak.app.sms.MapLinkBuilder
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
            source = LocationSource.AndroidLastKnown,
            ageMillis = 7 * 60_000L,
            isMocked = false
        )

        val body = SmsReplyFormatter.formatManualShareWithBatteryLabel(settings, location, "Batterie en charge")

        assertTrue(body.contains("Dernière position connue : il y a 7 min"))
        assertTrue(body.contains("Rayon approximatif : env. 24 m"))
        assertTrue(body.contains("Batterie en charge"))
    }

    @Test
    fun emergencyReply_isExplicitAndIncludesLastKnownAge() {
        val location = VeVakLocationSnapshot(
            latitude = 49.1193,
            longitude = 6.1757,
            accuracyMeters = 38f,
            source = LocationSource.VeVakRemembered,
            ageMillis = 2 * 60 * 60_000L,
            isMocked = false
        )

        val body = SmsReplyFormatter.formatEmergencyShareWithBatteryLabel(settings, location, "Batterie : 31 %")

        assertTrue(body.startsWith("URGENCE VeVak"))
        assertTrue(body.contains("Dernière position connue : il y a 2 h"))
        assertTrue(body.contains("openstreetmap.org"))
        assertTrue(body.contains("Batterie : 31 %"))
        assertFalse(body.contains("Estimation via le réseau"))
    }

    @Test
    fun emergencyUnavailable_doesNotInventCoordinates() {
        val body = SmsReplyFormatter.formatEmergencyUnavailableWithBatteryLabel(settings, "Batterie : 18 %")

        assertTrue(body.startsWith("URGENCE VeVak"))
        assertTrue(body.contains("Aucune dernière position connue"))
        assertTrue(body.contains("Batterie : 18 %"))
        assertFalse(body.contains("http"))
    }

    @Test
    fun ageLabel_usesDaysWhenNeeded() {
        val location = VeVakLocationSnapshot(
            latitude = 49.1193,
            longitude = 6.1757,
            accuracyMeters = null,
            source = LocationSource.AndroidLastKnown,
            ageMillis = 2 * 24 * 60 * 60_000L,
            isMocked = false
        )

        assertEquals("il y a 2 jours", location.ageLabel())
    }

    @Test
    fun networkApproximation_isPresentedAsAnAgedAreaNotAnExactFix() {
        val location = VeVakLocationSnapshot(
            latitude = 47.7427,
            longitude = 6.82733,
            accuracyMeters = 25_000f,
            source = LocationSource.NetworkApproximation,
            ageMillis = 0L,
            isMocked = false
        )

        val body = SmsReplyFormatter.format(settings, location, null)

        assertTrue(body.contains("Dernière zone connue : maintenant"))
        assertTrue(body.contains("Estimation via le réseau"))
        assertTrue(body.contains("pas une position exacte"))
        assertTrue(body.contains("Précision faible"))
        assertTrue(body.contains("25.0 km"))
        assertTrue(body.contains("Carte de la zone"))
        assertTrue(body.contains("#map=10/47.742700/6.827330"))
        assertFalse(body.contains("Dernière position connue"))
        assertFalse(body.contains("mlat="))
        assertFalse(body.contains("mlon="))
    }

    @Test
    fun approximateZoneZoom_tracksReturnedUncertainty() {
        assertEquals(16, MapLinkBuilder.approximateZoom(200f))
        assertEquals(11, MapLinkBuilder.approximateZoom(8_000f))
        assertEquals(10, MapLinkBuilder.approximateZoom(25_000f))
        assertEquals(8, MapLinkBuilder.approximateZoom(100_000f))
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
