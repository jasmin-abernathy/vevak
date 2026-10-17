package com.vevak.app.location

import com.vevak.app.model.VeVakSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CanonicalPositionResolverTest {
    private val settings = VeVakSettings(trustedWifiEnabled = true, trustedWifiHash = "configured", trustedPlaceLabel = "Maison")
    private val point = VeVakLocationSnapshot(48.0, 6.0, 100f, LocationSource.VeVakRemembered, 21_600_000L, false)

    @Test fun recognisedHomeWithoutGpsWinsWithoutTouchingMemoryOrNetwork() = runBlocking {
        val sources = FakeSources(home = true, memory = point)
        val resolver = VeVakPositionResolver(sources)
        repeat(3) { assertEquals(VeVakPositionResolution.KnownPlace("Maison"), resolver.resolve(settings)) }
        assertEquals(0, sources.cacheReads)
        assertEquals(0, sources.networkCalls)
        assertSame(point, sources.memory)
    }

    @Test fun missingHomeKeepsOldUsefulCoordinatesForEveryResolution() = runBlocking {
        val sources = FakeSources(memory = point)
        val resolver = VeVakPositionResolver(sources)
        repeat(3) { assertEquals(VeVakPositionResolution.Coordinates(point), resolver.resolve(settings)) }
        assertEquals(0, sources.networkCalls)
    }

    @Test fun wifiReadFailureDoesNotDiscardRememberedPosition() = runBlocking {
        val sources = FakeSources(memory = point, homeFailure = SecurityException("unavailable"))
        assertEquals(VeVakPositionResolution.Coordinates(point), VeVakPositionResolver(sources).resolve(settings))
    }

    @Test fun cancellationDuringWifiReadStopsFallback() = runBlocking {
        val failure = CancellationException("cancelled")
        val sources = FakeSources(memory = point, homeFailure = failure)
        try {
            VeVakPositionResolver(sources).resolve(settings)
            fail("Cancellation swallowed")
        } catch (caught: CancellationException) { assertSame(failure, caught) }
        assertEquals(0, sources.cacheReads)
    }

    @Test fun newResolutionDoesNotReuseHomeAfterLeavingWifi() = runBlocking {
        val sources = FakeSources(home = true, memory = point)
        val resolver = VeVakPositionResolver(sources)
        assertEquals(VeVakPositionResolution.KnownPlace("Maison"), resolver.resolve(settings))
        sources.home = false
        assertEquals(VeVakPositionResolution.Coordinates(point), resolver.resolve(settings))
    }

    @Test fun networkFallbackRequiresOptIn() = runBlocking {
        val sources = FakeSources()
        val resolver = VeVakPositionResolver(sources)
        assertEquals(VeVakPositionResolution.Unavailable, resolver.resolve(settings.copy(allowNetworkApproximation = false)))
        assertEquals(0, sources.networkCalls)
        resolver.resolve(settings.copy(allowNetworkApproximation = true))
        assertEquals(1, sources.networkCalls)
    }

    private class FakeSources(
        var home: Boolean = false,
        var memory: VeVakLocationSnapshot? = null,
        val homeFailure: Exception? = null
    ) : PositionSources {
        var cacheReads = 0
        var networkCalls = 0
        override fun isSystemLocationEnabled() = false
        override suspend fun fresh(policy: LocationRequestPolicy): VeVakLocationSnapshot? = error("GPS disabled")
        override fun matchesTrustedPlace(settings: VeVakSettings): Boolean {
            homeFailure?.let { throw it }
            return home
        }
        override suspend fun approximate(): VeVakLocationSnapshot? { networkCalls++; return null }
        override suspend fun remember(location: VeVakLocationSnapshot) { memory = location }
        override suspend fun cached(): VeVakLocationSnapshot? { cacheReads++; return memory }
    }
}
