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

/** Injectable platform inputs; all three actions still use this single decision engine. */
internal interface PositionSources {
    fun isSystemLocationEnabled(): Boolean
    suspend fun fresh(policy: LocationRequestPolicy): VeVakLocationSnapshot?
    fun matchesTrustedPlace(settings: VeVakSettings): Boolean
    suspend fun approximate(): VeVakLocationSnapshot?
    suspend fun cellular(): VeVakLocationSnapshot?
    suspend fun remember(location: VeVakLocationSnapshot)
    suspend fun cached(): VeVakLocationSnapshot?
}

/**
 * Canonical position decision engine shared by authorised automatic replies and diagnostic flows.
 *
 * VeVak does not continuously track the device and does not require Android location to stay on.
 * Whenever a coordinate-bearing source succeeds, the repository remembers that point locally. A
 * later request can therefore return the last position even when Android can no longer acquire a
 * new one.
 *
 * Normal resolution order:
 * 1. while Android location is currently usable, try a recent/current local device point;
 * 2. recognise the configured trusted place if the current network matches;
 * 3. with Location OFF, prefer the last locally remembered coordinate;
 * 4. with Location ON, optionally ask for a cell-based estimate (separate consent);
 * 5. optionally fall back to IP, and finally to any local point.
 *
 * Explicit manual sharing and local emergency use this same canonical resolver. The duress/protection path deliberately bypasses this resolver in SmsRequestHandler.
 */
class VeVakPositionResolver internal constructor(private val sources: PositionSources) {
    constructor(context: Context) : this(AndroidPositionSources(context.applicationContext))

    suspend fun resolve(
        settings: VeVakSettings,
        includeTrustedPlace: Boolean = true
    ): VeVakPositionResolution {
        val androidLocationEnabled = sources.isSystemLocationEnabled()
        if (androidLocationEnabled) {
            val freshPolicy = LocationRequestPolicy(
                maxAcceptedCacheAgeMillis = settings.maxCachedLocationAgeSeconds * 1_000L,
                currentLocationTimeoutMillis = settings.locationTimeoutSeconds * 1_000L,
                allowStaleFallback = false
            )
            locationAttempt { sources.fresh(freshPolicy) }
                .getOrNull()
                ?.let { return VeVakPositionResolution.Coordinates(it) }
        }

        if (includeTrustedPlace && settings.hasTrustedWifiConfiguration() && locationAttempt { sources.matchesTrustedPlace(settings) }.getOrDefault(false)) {
            return VeVakPositionResolution.KnownPlace(settings.trustedPlaceLabel.trim().ifBlank { "Maison" })
        }

        // Location OFF is a hard privacy boundary: do not collect live cellular IDs.
        // The previously saved point is preferred to a potentially misleading IP estimate.
        if (!androidLocationEnabled) {
            locationAttempt { sources.cached() }.getOrNull()
                ?.let { return VeVakPositionResolution.Coordinates(it) }
        }
        if (androidLocationEnabled && settings.allowCellularApproximation) {
            locationAttempt { sources.cellular() }.getOrNull()
                ?.let { cellular ->
                    locationAttempt { sources.remember(cellular) }
                    return VeVakPositionResolution.Coordinates(cellular)
                }
        }
        if (settings.allowNetworkApproximation) {
            locationAttempt { sources.approximate() }
                .getOrNull()
                ?.let { approximate ->
                    locationAttempt { sources.remember(approximate) }
                    return VeVakPositionResolution.Coordinates(approximate)
                }
        }

        locationAttempt { sources.cached() }
            .getOrNull()
            ?.let { return VeVakPositionResolution.Coordinates(it) }

        return VeVakPositionResolution.Unavailable
    }

}

private class AndroidPositionSources(context: Context) : PositionSources {
    private val locationRepository = VeVakLocationRepository(context)
    private val trustedNetworkReader = TrustedNetworkReader(context)
    private val onlineApproximation = OnlineApproximateLocationProvider()
    private val cellularApproximation = CellularApproximateLocationProvider(context)
    private val locationManager = context.getSystemService(LocationManager::class.java)

    override suspend fun fresh(policy: LocationRequestPolicy) = locationRepository.fetchBestLocation(policy)
    override fun matchesTrustedPlace(settings: VeVakSettings) = trustedNetworkReader.matches(settings)
    override suspend fun approximate() = onlineApproximation.locate()
    override suspend fun cellular() = cellularApproximation.locate()
    override suspend fun remember(location: VeVakLocationSnapshot) = locationRepository.rememberLocation(location)
    override suspend fun cached() = locationRepository.fetchLastKnownAnyLocation()

    override fun isSystemLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return locationAttempt {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }.getOrDefault(false)
    }
}
