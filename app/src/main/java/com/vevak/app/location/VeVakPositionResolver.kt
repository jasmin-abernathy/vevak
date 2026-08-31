/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.content.Context
import android.location.LocationManager
import android.os.Build
import com.vevak.app.model.VeVakSettings
import com.vevak.app.system.TrustedNetworkReader

sealed interface VeVakPositionResolution {
    data class KnownPlace(val label: String) : VeVakPositionResolution
    data class Coordinates(val location: VeVakLocationSnapshot) : VeVakPositionResolution
    data object Unavailable : VeVakPositionResolution
}

/**
 * Single location decision engine shared by automatic SMS replies and diagnostic/test flows.
 *
 * When Android location is enabled, the normal local location stack always gets first chance.
 * Alternative sources are fallbacks and must never prevent VeVak from acquiring and remembering
 * a real device position:
 * 1. fresh/cached local Android or VeVak point while Android location is enabled;
 * 2. already-recognised trusted place (for example Maison);
 * 3. an older real local point when stale fallback is allowed;
 * 4. explicit opt-in IP-only approximation via beaconDB as absolute last resort.
 *
 * This ordering is important for safety as well as reliability: a trusted Wi-Fi match remains a
 * useful low-cost fallback, but it no longer short-circuits Android positioning while location is
 * enabled. That also lets VeVak keep its bounded local remembered-position cache populated for the
 * moment when Wi-Fi later becomes unavailable.
 */
class VeVakPositionResolver(context: Context) {
    private val appContext = context.applicationContext
    private val locationRepository = VeVakLocationRepository(appContext)
    private val trustedNetworkReader = TrustedNetworkReader(appContext)
    private val onlineApproximation = OnlineApproximateLocationProvider()
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    suspend fun resolve(
        settings: VeVakSettings,
        includeTrustedPlace: Boolean = true
    ): VeVakPositionResolution {
        if (isSystemLocationEnabled()) {
            val freshPolicy = LocationRequestPolicy(
                maxAcceptedCacheAgeMillis = settings.maxCachedLocationAgeSeconds * 1_000L,
                currentLocationTimeoutMillis = settings.locationTimeoutSeconds * 1_000L,
                allowStaleFallback = false
            )
            locationRepository.fetchBestLocation(freshPolicy)?.let {
                return VeVakPositionResolution.Coordinates(it)
            }
        }

        if (includeTrustedPlace && settings.hasTrustedWifiConfiguration() && trustedNetworkReader.matches(settings)) {
            return VeVakPositionResolution.KnownPlace(settings.trustedPlaceLabel.trim().ifBlank { "Maison" })
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

    private fun isSystemLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }.getOrDefault(false)
    }
}
