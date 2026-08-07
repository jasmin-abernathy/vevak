/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.content.Context

class VeVakLocationRepository(context: Context) {
    private val provider: LocationProvider = PlatformLocationProvider(context.applicationContext)

    suspend fun fetchBestLocation(policy: LocationRequestPolicy): VeVakLocationSnapshot? {
        val cached = provider.lastKnownLocation()?.toVeVakSnapshot(provider.lastKnownSource)
        if (cached != null && LocationSelectionPolicy.acceptsCache(
                cached.ageMillis,
                policy.maxAcceptedCacheAgeMillis
            )
        ) return cached

        val current = provider.currentLocation(policy.currentLocationTimeoutMillis)
            ?.toVeVakSnapshot(provider.currentSource)
        if (current != null) return current

        return cached.takeIf { policy.allowStaleFallback }
    }

    fun backendStatus(): LocationBackendStatus = provider.backendStatus()
}
