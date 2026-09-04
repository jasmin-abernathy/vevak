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
 * 3. if explicitly enabled, request a fresh coarse IP/network estimate and remember it;
 * 4. return the newest coordinate-bearing point VeVak/Android already knows, whatever its source
 *    or age, with that age made explicit in the SMS;
 * 5. unavailable only when no source has ever produced usable information.
 *
 * The duress/protection path deliberately bypasses this resolver in SmsRequestHandler.
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
            runCatching { locationRepository.fetchBestLocation(freshPolicy) }
                .getOrNull()
                ?.let { return VeVakPositionResolution.Coordinates(it) }
        }

        if (includeTrustedPlace && settings.hasTrustedWifiConfiguration() && trustedNetworkReader.matches(settings)) {
            return VeVakPositionResolution.KnownPlace(settings.trustedPlaceLabel.trim().ifBlank { "Maison" })
        }

        if (settings.allowNetworkApproximation) {
            runCatching { onlineApproximation.locate() }
                .getOrNull()
                ?.let { approximate ->
                    runCatching { locationRepository.rememberLocation(approximate) }
                    return VeVakPositionResolution.Coordinates(approximate)
                }
        }

        runCatching { locationRepository.fetchLastKnownLocation() }
            .getOrNull()
            ?.let { return VeVakPositionResolution.Coordinates(it) }

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
