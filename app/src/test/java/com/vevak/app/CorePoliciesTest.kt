/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.location.LocationSelectionPolicy
import com.vevak.app.location.LocationSource
import com.vevak.app.location.RememberedLocationPolicy
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.AuthorizationDuration
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.DuressPolicy
import com.vevak.app.security.IncomingRequestMode
import com.vevak.app.security.RequestModeResolver
import com.vevak.app.security.RequestRatePolicy
import com.vevak.app.security.RequestRateState
import com.vevak.app.sms.SmsCommandParser
import com.vevak.app.sms.SmsReplyFormatter
import com.vevak.app.system.TrustedNetworkReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePoliciesTest {
    @Test
    fun smsCommand_matchesCaseAndRepeatedWhitespace() {
        assertTrue(SmsCommandParser.matches("  OÙ   ES-TU ? ", "où es-tu ?"))
    }

    @Test
    fun smsCommand_rejectsEmptyConfiguredPhrase() {
        assertFalse(SmsCommandParser.matches("anything", "   "))
    }

    @Test
    fun rateLimit_enforcesHardMinimumEvenIfLegacySettingWasLower() {
        val first = RequestRatePolicy.evaluate(RequestRateState(), 1_000L, 60_000L)
        assertTrue(first.allowed)
        assertFalse(RequestRatePolicy.evaluate(first.state, 61_000L, 60_000L).allowed)
        assertTrue(
            RequestRatePolicy.evaluate(
                first.state,
                1_000L + RequestRatePolicy.HARD_MIN_INTERVAL_MILLIS,
                60_000L
            ).allowed
        )
    }

    @Test
    fun rateLimit_capsAutomaticRepliesInside24Hours() {
        var state = RequestRateState()
        var now = 1_000L
        repeat(RequestRatePolicy.MAX_REQUESTS_PER_WINDOW) {
            val result = RequestRatePolicy.evaluate(state, now, RequestRatePolicy.HARD_MIN_INTERVAL_MILLIS)
            assertTrue(result.allowed)
            state = result.state
            now += RequestRatePolicy.HARD_MIN_INTERVAL_MILLIS
        }
        assertFalse(RequestRatePolicy.evaluate(state, now, RequestRatePolicy.HARD_MIN_INTERVAL_MILLIS).allowed)
    }

    @Test
    fun rateLimit_resetsAfter24HoursButRejectsClockRollback() {
        val first = RequestRatePolicy.evaluate(RequestRateState(), 1_000_000L, 0L)
        assertTrue(first.allowed)
        assertFalse(RequestRatePolicy.evaluate(first.state, 999_999L, 0L).allowed)
        assertTrue(
            RequestRatePolicy.evaluate(
                first.state,
                first.state.windowStartMillis + RequestRatePolicy.WINDOW_MILLIS,
                0L
            ).allowed
        )
    }

    @Test
    fun duressPolicy_requiresClearlyDistinctPhrasesAndValidCoordinates() {
        assertFalse(DuressPolicy.phrasesAreDistinctEnough("où es-tu", "où es-tu stp"))
        assertTrue(DuressPolicy.phrasesAreDistinctEnough("besoin position", "tu peux me rappeler maintenant"))
        assertTrue(DuressPolicy.coordinatesAreValid(48.0, 2.0))
        assertFalse(DuressPolicy.coordinatesAreValid(91.0, 2.0))
    }

    @Test
    fun duressModeWinsFailSafeIfPhrasesEverCollide() {
        val settings = VeVakSettings(
            triggerPhrase = "position maintenant",
            duressEnabled = true,
            duressPhrase = "position maintenant"
        )
        assertEquals(IncomingRequestMode.Duress, RequestModeResolver.resolve("position maintenant", settings))
    }

    @Test
    fun authorizationIsFiniteAndMustBeExplicitlyGranted() {
        val now = 10_000L
        val expiry = AuthorizationDuration.SevenDays.expiresAt(now)
        val active = VeVakSettings(
            completedOnboarding = true,
            contactPhone = "+33123456789",
            triggerPhrase = "besoin position",
            authorizationGrantedAtEpochMs = now,
            authorizationExpiresAtEpochMs = expiry
        )
        assertTrue(active.isConfigured(now + 1L))
        assertFalse(active.isConfigured(expiry))
        assertFalse(active.copy(authorizationGrantedAtEpochMs = 0L, authorizationExpiresAtEpochMs = 0L).isConfigured(now))
    }

    @Test
    fun discreetMode_isAlwaysFinite() {
        val now = 1_000L
        val settings = VeVakSettings(discreetModeUntilEpochMs = now + 3_600_000L)
        assertTrue(settings.isDiscreetModeActive(now))
        assertFalse(settings.isDiscreetModeActive(now + 3_600_000L))
    }

    @Test
    fun trustedWifiHash_isDeterministicAndDoesNotStorePlainSsid() {
        val first = TrustedNetworkReader.hashSsid("My Home WiFi")
        val second = TrustedNetworkReader.hashSsid("\"My Home WiFi\"")
        assertEquals(first, second)
        assertNotEquals("My Home WiFi", first)
        assertEquals(64, first.length)
    }

    @Test
    fun manualShareFormatter_marksShareAsUserInitiatedAndKeepsConfiguredDetails() {
        val settings = VeVakSettings(includeBattery = true, includeAccuracy = true)
        val location = VeVakLocationSnapshot(
            latitude = 48.8566,
            longitude = 2.3522,
            accuracyMeters = 12f,
            source = LocationSource.AndroidCurrent,
            ageMillis = 0L,
            isMocked = false
        )
        val body = SmsReplyFormatter.formatManualShare(settings, location, 73)
        assertTrue(body.contains("Je partage ma position"))
        assertTrue(body.contains("48.8566"))
        assertTrue(body.contains("2.3522"))
        assertTrue(body.contains("Precision: env. 12 m"))
        assertTrue(body.contains("Batterie: 73 %"))
    }

    @Test
    fun locationCache_acceptsOnlyBoundedNonNegativeAge() {
        assertTrue(LocationSelectionPolicy.acceptsCache(0L, 120_000L))
        assertTrue(LocationSelectionPolicy.acceptsCache(120_000L, 120_000L))
        assertFalse(LocationSelectionPolicy.acceptsCache(-1L, 120_000L))
        assertFalse(LocationSelectionPolicy.acceptsCache(120_001L, 120_000L))
    }

    @Test
    fun rememberedLocation_acceptsOnlyRealValidCoordinates() {
        val real = VeVakLocationSnapshot(
            latitude = 49.1193,
            longitude = 6.1757,
            accuracyMeters = 25f,
            source = LocationSource.AndroidCurrent,
            ageMillis = 1_000L,
            isMocked = false
        )
        assertTrue(RememberedLocationPolicy.canPersist(real))
        assertFalse(RememberedLocationPolicy.canPersist(real.copy(latitude = 91.0)))
        assertFalse(RememberedLocationPolicy.canPersist(real.copy(isMocked = true)))
    }

    @Test
    fun rememberedLocation_ageRejectsClockRollbackAndExpiresAfter24Hours() {
        val captured = 1_000_000L
        assertEquals(60_000L, RememberedLocationPolicy.ageMillis(captured, captured + 60_000L))
        assertNull(RememberedLocationPolicy.ageMillis(captured, captured - 1L))
        assertTrue(RememberedLocationPolicy.MAX_RETENTION_MILLIS == 24L * 60L * 60L * 1_000L)
        assertTrue(
            RememberedLocationPolicy.ageMillis(
                captured,
                captured + RememberedLocationPolicy.MAX_RETENTION_MILLIS + 1L
            )!! > RememberedLocationPolicy.MAX_RETENTION_MILLIS
        )
    }
}
