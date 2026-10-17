package com.vevak.app.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class LocationAttemptTest {
    @Test fun revokedCapabilityCanFallBack() {
        assertNull(locationAttempt<String> { throw SecurityException("revoked") }.getOrNull())
    }

    @Test fun cancellationDoesNotBecomeMissingLocation() = runBlocking {
        val cancellation = CancellationException("request cancelled")
        var fallbackVisited = false
        try {
            locationAttempt<String> { yield(); throw cancellation }.getOrNull()
            fallbackVisited = true
            fail("Cancellation was swallowed")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
        assertFalse(fallbackVisited)
    }
}
