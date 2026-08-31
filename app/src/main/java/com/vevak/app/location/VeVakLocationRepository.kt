/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.content.Context

class VeVakLocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val provider: LocationProvider = PlatformLocationProvider(appContext)
    private val reverseGeocoder = SystemReverseGeocoder(appContext)
    private val rememberedLocationStore = RememberedLocationStore(appContext)

    /**
     * Returns only the freshest real location that is already known locally.
     *
     * This method never starts a new positioning request, never resolves a trusted Wi-Fi place and
     * never calls the optional IP approximation service. It is used by explicit/manual and
     * emergency sharing so "last known" means exactly that. The returned snapshot carries its age.
     */
    suspend fun fetchLastKnownLocation(): VeVakLocationSnapshot? {
        val androidCached = runCatching { provider.lastKnownLocation() }
            .getOrNull()
            ?.toVeVakSnapshot(provider.lastKnownSource)
            ?.takeUnless { it.isMocked }
        val rememberedCached = runCatching { rememberedLocationStore.read() }.getOrNull()
        val bestCached = freshest(androidCached, rememberedCached) ?: return null

        rememberPlatformLocation(bestCached)
        return enrich(bestCached)
    }

    /**
     * Resolution order:
     * 1. freshest cache available from Android or VeVak's own bounded local memory;
     * 2. a bounded fresh Android location request;
     * 3. freshest stale cache, when the user kept stale fallback enabled.
     *
     * VeVak keeps its own copy because Android may clear provider/fused caches when the user turns
     * the global Location switch off. The remembered copy is app-private, never uploaded and
     * automatically expires after RememberedLocationPolicy.MAX_RETENTION_MILLIS.
     */
    suspend fun fetchBestLocation(policy: LocationRequestPolicy): VeVakLocationSnapshot? {
        val androidCached = provider.lastKnownLocation()?.toVeVakSnapshot(provider.lastKnownSource)
        val rememberedCached = rememberedLocationStore.read()
        val bestCached = freshest(androidCached, rememberedCached)

        if (bestCached != null && LocationSelectionPolicy.acceptsCache(
                bestCached.ageMillis,
                policy.maxAcceptedCacheAgeMillis
            )
        ) {
            rememberPlatformLocation(bestCached)
            return enrich(bestCached)
        }

        val current = provider.currentLocation(policy.currentLocationTimeoutMillis)
            ?.toVeVakSnapshot(provider.currentSource)
        if (current != null) {
            rememberPlatformLocation(current)
            return enrich(current)
        }

        if (!policy.allowStaleFallback) return null

        return bestCached?.let { stale ->
            rememberPlatformLocation(stale)
            enrich(stale)
        }
    }

    fun backendStatus(): LocationBackendStatus = provider.backendStatus()

    suspend fun clearRememberedLocation() {
        rememberedLocationStore.clear()
    }

    private suspend fun rememberPlatformLocation(location: VeVakLocationSnapshot) {
        if (location.source == LocationSource.VeVakRemembered) return
        rememberedLocationStore.remember(location)
    }

    private fun freshest(vararg candidates: VeVakLocationSnapshot?): VeVakLocationSnapshot? =
        candidates.filterNotNull().minByOrNull { it.ageMillis }

    private suspend fun enrich(location: VeVakLocationSnapshot): VeVakLocationSnapshot {
        val address = reverseGeocoder.resolve(location) ?: return location
        return location.copy(address = address)
    }
}
