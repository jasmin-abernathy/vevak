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
     * Returns the newest coordinate-bearing position already known locally, regardless of the
     * method that originally produced it.
     *
     * No new positioning request and no network request is started here. Android's own cache and
     * VeVak's app-private memory are compared by capture age. This is the canonical fallback used
     * when a fresh acquisition cannot run (location switch off, permission unavailable, background
     * restrictions, etc.).
     */
    suspend fun fetchLastKnownLocation(): VeVakLocationSnapshot? {
        val androidCached = runCatching {
            provider.lastKnownLocation()
                ?.toVeVakSnapshot(provider.lastKnownSource)
                ?.takeUnless { it.isMocked }
        }.getOrNull()
        val rememberedCached = runCatching { rememberedLocationStore.read() }.getOrNull()

        androidCached?.let { rememberLocation(it) }
        val bestCached = freshest(androidCached, rememberedCached) ?: return null
        return enrich(bestCached)
    }

    /**
     * Tries the local Android location stack without allowing a previously remembered IP estimate
     * to short-circuit a real device fix.
     *
     * Resolution order inside this local stack:
     * 1. sufficiently fresh Android cache or remembered real/local point;
     * 2. bounded current Android request;
     * 3. older real/local point when the caller explicitly asks for it.
     *
     * Provider calls are guarded because an SMS may arrive after the user disabled/revoked a
     * location capability. That must never prevent VeVak from falling back to its remembered point.
     */
    suspend fun fetchBestLocation(policy: LocationRequestPolicy): VeVakLocationSnapshot? {
        val androidCached = runCatching {
            provider.lastKnownLocation()?.toVeVakSnapshot(provider.lastKnownSource)
        }.getOrNull()?.takeUnless { it.isMocked }
        val rememberedCached = runCatching { rememberedLocationStore.read() }.getOrNull()
            ?.takeUnless { it.isApproximateNetworkEstimate() || it.source == LocationSource.SafetyFallback }
        val bestCached = freshest(androidCached, rememberedCached)

        if (bestCached != null && LocationSelectionPolicy.acceptsCache(
                bestCached.ageMillis,
                policy.maxAcceptedCacheAgeMillis
            )
        ) {
            rememberLocation(bestCached)
            return enrich(bestCached)
        }

        val current = runCatching {
            provider.currentLocation(policy.currentLocationTimeoutMillis)
                ?.toVeVakSnapshot(provider.currentSource)
        }.getOrNull()?.takeUnless { it.isMocked }
        if (current != null) {
            rememberLocation(current)
            return enrich(current)
        }

        if (!policy.allowStaleFallback) return null

        return bestCached?.let { stale ->
            rememberLocation(stale)
            enrich(stale)
        }
    }

    /**
     * Persists any legitimate coordinate-bearing source selected by the resolver, including an
     * explicitly opted-in network/IP estimate. Duress fallback coordinates and mocked points are
     * rejected by RememberedLocationPolicy.
     */
    suspend fun rememberLocation(location: VeVakLocationSnapshot) {
        rememberedLocationStore.remember(location)
    }

    fun backendStatus(): LocationBackendStatus = provider.backendStatus()

    suspend fun clearRememberedLocation() {
        rememberedLocationStore.clear()
    }

    private fun freshest(vararg candidates: VeVakLocationSnapshot?): VeVakLocationSnapshot? =
        candidates.filterNotNull().minByOrNull { it.ageMillis }

    private suspend fun enrich(location: VeVakLocationSnapshot): VeVakLocationSnapshot {
        val address = reverseGeocoder.resolve(location) ?: return location
        return location.copy(address = address)
    }
}
