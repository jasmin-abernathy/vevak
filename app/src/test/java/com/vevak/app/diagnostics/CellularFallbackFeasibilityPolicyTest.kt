/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellularFallbackFeasibilityPolicyTest {
    @Test
    fun summaryKeepsOnlyRedactedCapabilityMetadata() {
        val summary = CellularFallbackFeasibilityPolicy.summarize(
            listOf(
                CellObservationMetadata("LTE", registered = true, lookupIdentityComplete = true, ageMillis = 12_000L),
                CellObservationMetadata("NR", registered = false, lookupIdentityComplete = true, ageMillis = 45_000L),
                CellObservationMetadata("LTE", registered = false, lookupIdentityComplete = false, ageMillis = null)
            )
        )

        assertEquals(3, summary.visibleCount)
        assertEquals(1, summary.registeredCount)
        assertEquals(2, summary.lookupReadyCount)
        assertEquals(listOf("LTE", "NR"), summary.radios)
        assertEquals(12_000L, summary.freshestAgeMillis)
    }

    @Test
    fun emptySummaryIsStable() {
        val summary = CellularFallbackFeasibilityPolicy.summarize(emptyList())

        assertEquals(0, summary.visibleCount)
        assertEquals(0, summary.registeredCount)
        assertEquals(0, summary.lookupReadyCount)
        assertEquals(emptyList<String>(), summary.radios)
        assertNull(summary.freshestAgeMillis)
    }

    @Test
    fun freshnessIsDeliberatelyCoarse() {
        assertEquals("ancienneté inconnue", CellularFallbackFeasibilityPolicy.freshnessLabel(null))
        assertEquals("moins d'une minute", CellularFallbackFeasibilityPolicy.freshnessLabel(59_999L))
        assertEquals("moins de 5 minutes", CellularFallbackFeasibilityPolicy.freshnessLabel(60_000L))
        assertEquals("moins de 30 minutes", CellularFallbackFeasibilityPolicy.freshnessLabel(5 * 60_000L))
        assertEquals("plus de 30 minutes", CellularFallbackFeasibilityPolicy.freshnessLabel(30 * 60_000L))
    }
}
