/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.content.Context
import com.vevak.app.model.VeVakSettings
import com.vevak.app.system.TrustedNetworkReader

sealed interface VeVakPositionResolution {
    data class KnownPlace(val label: String) : VeVakPositionResolution
    data class Coordinates(val location: VeVakLocationSnapshot) : VeVakPositionResolution
    data object Unavailable : VeVakPositionResolution
}

/**
 * Single location decision engine shared by automatic SMS replies, manual sharing and diagnostics.
 *
 * Order is intentionally trust-first rather than freshness-only:
 * 1. already-recognised trusted place (no positioning request);
 * 2. fresh/cached local Android or VeVak point;
 * 3. an older real local point when stale fallback is allowed;
 * 4. explicit opt-in IP-only approximation via beaconDB as absolute last resort.
 *
 * A clearly dated real fix is generally more useful in a safety context than a current IP centroid
 * with an uncertainty of many kilometres. The formatter still exposes the age and radius so the
 * recipient can judge the information instead of treating every result as equally reliable.
 */
class VeVakPositionResolver(context: Context) {
    private val appContext = context.applicationContext
    private val locationRepository = VeVakLocationRepository(appContext)
    private val trustedNetworkReader = TrustedNetworkReader(appContext)
    private val onlineApproximation = OnlineApproximateLocationProvider()

    suspend fun resolve(
        settings: VeVakSettings,
        includeTrustedPlace: Boolean = true
    ): VeVakPositionResolution {
        if (includeTrustedPlace && settings.hasTrustedWifiConfiguration() && trustedNetworkReader.matches(settings)) {
            return VeVakPositionResolution.KnownPlace(settings.trustedPlaceLabel.trim().ifBlank { "Maison" })
        }

        val freshPolicy = LocationRequestPolicy(
            maxAcceptedCacheAgeMillis = settings.maxCachedLocationAgeSeconds * 1_000L,
            currentLocationTimeoutMillis = settings.locationTimeoutSeconds * 1_000L,
            allowStaleFallback = false
        )
        locationRepository.fetchBestLocation(freshPolicy)?.let {
            return VeVakPositionResolution.Coordinates(it)
        }

        if (settings.allowStaleFallback) {
            val staleOnlyPolicy = LocationRequestPolicy(
                maxAcceptedCacheAgeMillis = settings.maxCachedLocationAgeSeconds * 1_000L,
                currentLocationTimeoutMillis = 0L,
                allowStaleFallback = true
            )
            locationRepository.fetchBestLocation(staleOnlyPolicy)?.let {
                if (!it.isApproximateNetworkEstimate()) {
                    return VeVakPositionResolution.Coordinates(it)
                }
            }
        }

        if (settings.allowNetworkApproximation) {
            onlineApproximation.locate()?.let {
                return VeVakPositionResolution.Coordinates(it)
            }
        }

        return VeVakPositionResolution.Unavailable
    }
}
