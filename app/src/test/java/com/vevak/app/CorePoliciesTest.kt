/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import com.vevak.app.location.LocationSelectionPolicy
import com.vevak.app.security.RequestRatePolicy
import com.vevak.app.sms.SmsCommandParser
import org.junit.Assert.assertFalse
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
    fun rateLimit_allowsFirstAndExpiredRequests() {
        assertTrue(RequestRatePolicy.isAllowed(0L, 1_000L, 60_000L))
        assertTrue(RequestRatePolicy.isAllowed(10_000L, 70_000L, 60_000L))
    }

    @Test
    fun rateLimit_rejectsTooFrequentRequest() {
        assertFalse(RequestRatePolicy.isAllowed(10_000L, 69_999L, 60_000L))
    }

    @Test
    fun locationCache_acceptsOnlyBoundedNonNegativeAge() {
        assertTrue(LocationSelectionPolicy.acceptsCache(0L, 120_000L))
        assertTrue(LocationSelectionPolicy.acceptsCache(120_000L, 120_000L))
        assertFalse(LocationSelectionPolicy.acceptsCache(-1L, 120_000L))
        assertFalse(LocationSelectionPolicy.acceptsCache(120_001L, 120_000L))
    }
}
