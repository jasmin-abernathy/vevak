/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import java.util.concurrent.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EmergencyLocationLookupTest {
    @Test
    fun blockedPlatformCache_stillReturnsRememberedRealPoint() = runBlocking {
        val remembered = snapshot(LocationSource.VeVakRemembered, ageMillis = 42_000L)

        val result = withTimeout(1_000L) {
            EmergencyLocationLookup.resolve(
                remembered = { remembered },
                platform = { awaitCancellation() },
                platformTimeoutMillis = 20L
            )
        }

        assertEquals(remembered, result)
    }

    @Test
    fun fresherRealPlatformCache_winsWithoutChangingItsAgeOrSource() = runBlocking {
        val remembered = snapshot(LocationSource.VeVakRemembered, ageMillis = 90_000L)
        val platform = snapshot(LocationSource.FusedLastKnown, ageMillis = 5_000L)

        val result = EmergencyLocationLookup.resolve(
            remembered = { remembered },
            platform = { platform },
            platformTimeoutMillis = 100L
        )

        assertEquals(platform, result)
        assertEquals(LocationSource.FusedLastKnown, result?.source)
        assertEquals(5_000L, result?.ageMillis)
    }

    @Test
    fun networkEstimateAndMockedPoint_areNeverEmergencyCandidates() = runBlocking {
        val network = snapshot(LocationSource.NetworkApproximation, ageMillis = 1_000L)
        val mocked = snapshot(LocationSource.AndroidLastKnown, ageMillis = 500L, mocked = true)

        assertNull(
            EmergencyLocationLookup.resolve(
                remembered = { network },
                platform = { null },
                platformTimeoutMillis = 100L
            )
        )
        assertNull(
            EmergencyLocationLookup.resolve(
                remembered = { null },
                platform = { mocked },
                platformTimeoutMillis = 100L
            )
        )
    }

    @Test
    fun noRealPoint_returnsNull() = runBlocking {
        assertNull(
            EmergencyLocationLookup.resolve(
                remembered = { null },
                platform = { null },
                platformTimeoutMillis = 100L
            )
        )
    }

    @Test
    fun cancellation_isNotSwallowedAsAProviderFailure() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                EmergencyLocationLookup.resolve(
                    remembered = { throw CancellationException("cancelled") },
                    platform = { null },
                    platformTimeoutMillis = 100L
                )
            }
        }
    }

    private fun snapshot(
        source: LocationSource,
        ageMillis: Long,
        mocked: Boolean = false
    ) = VeVakLocationSnapshot(
        latitude = 49.1193,
        longitude = 6.1757,
        accuracyMeters = 25f,
        source = source,
        ageMillis = ageMillis,
        isMocked = mocked
    )
}
