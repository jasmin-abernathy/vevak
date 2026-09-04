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
     * Legacy/explicit-share contract: returns only the newest real/local position already known.
     *
     * Manual sharing and the emergency shortcut intentionally keep using this method so an opted-in
     * IP estimate can never silently replace the stricter "last real point" behaviour introduced in
     * 0.3.6. No new positioning request is started here.
     */
    suspend fun fetchLastKnownLocation(): VeVakLocationSnapshot? =
        fetchCachedLocation(includeNetworkApproximation = false)

    /**
     * Automatic phrase-key contract: returns the newest coordinate-bearing position already known,
     * regardless of whether it came from Android or from the explicitly enabled network/IP fallback.
     * Its source and age remain attached to the snapshot for honest SMS formatting.
     */
    suspend fun fetchLastKnownAnyLocation(): VeVakLocationSnapshot? =
        fetchCachedLocation(includeNetworkApproximation = true)

    /**
     * Tries the local Android location stack without letting a remembered IP estimate short-circuit
     * a real device fix.
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
        val androidCached = platformCachedLocation()
        val rememberedCached = runCatching { rememberedLocationStore.readReal() }.getOrNull()
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
     * Persists any legitimate coordinate-bearing source selected by the resolver. The memory store
     * keeps a separate last-real slot, so remembering an IP estimate cannot erase the emergency or
     * manual-share fallback. Safety/duress coordinates and mocked points are rejected by policy.
     */
    suspend fun rememberLocation(location: VeVakLocationSnapshot) {
        rememberedLocationStore.remember(location)
    }

    fun backendStatus(): LocationBackendStatus = provider.backendStatus()

    suspend fun clearRememberedLocation() {
        rememberedLocationStore.clear()
    }

    private suspend fun fetchCachedLocation(includeNetworkApproximation: Boolean): VeVakLocationSnapshot? {
        val androidCached = platformCachedLocation()
        androidCached?.let { runCatching { rememberLocation(it) } }

        val rememberedCached = runCatching {
            if (includeNetworkApproximation) rememberedLocationStore.read()
            else rememberedLocationStore.readReal()
        }.getOrNull()

        val bestCached = freshest(androidCached, rememberedCached) ?: return null
        return enrich(bestCached)
    }

    private suspend fun platformCachedLocation(): VeVakLocationSnapshot? = runCatching {
        provider.lastKnownLocation()
            ?.toVeVakSnapshot(provider.lastKnownSource)
            ?.takeUnless { it.isMocked }
    }.getOrNull()

    private fun freshest(vararg candidates: VeVakLocationSnapshot?): VeVakLocationSnapshot? =
        candidates.filterNotNull().minByOrNull { it.ageMillis }

    private suspend fun enrich(location: VeVakLocationSnapshot): VeVakLocationSnapshot {
        val address = reverseGeocoder.resolve(location) ?: return location
        return location.copy(address = address)
    }
}
