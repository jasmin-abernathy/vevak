/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class PlatformLocationProvider(context: Context) : LocationProvider {
    private val manager = context.getSystemService(LocationManager::class.java)

    override val currentSource = LocationSource.AndroidCurrent
    override val lastKnownSource = LocationSource.AndroidLastKnown

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownLocation(): Location? {
        val preferred = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        return preferred.mapNotNull { provider ->
            runCatching { manager?.getLastKnownLocation(provider) }.getOrNull()
        }.minWithOrNull(compareBy<Location> { ageMillis(it) }.thenBy { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE })
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(timeoutMillis: Long): Location? {
        val available = enabledProviders()
        if (available.isEmpty()) return null
        val network = available.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
        val gps = available.firstOrNull { it == LocationManager.GPS_PROVIDER }

        val startedAt = SystemClock.elapsedRealtime()
        if (network != null) {
            val networkBudget = (timeoutMillis * 2 / 3).coerceAtLeast(1_000L)
            requestOne(network, networkBudget)?.let { return it }
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (timeoutMillis - elapsed).coerceAtLeast(0L)
        return if (gps != null && remaining >= 500L) requestOne(gps, remaining) else null
    }

    override fun backendStatus(): LocationBackendStatus {
        val enabled = enabledProviders()
        return LocationBackendStatus(
            name = "Android LocationManager",
            available = manager != null && enabled.isNotEmpty(),
            detail = if (enabled.isEmpty()) "Aucun fournisseur de localisation actif." else "Fournisseurs actifs : ${enabled.joinToString()}"
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOne(provider: String, timeoutMillis: Long): Location? =
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                var delivered = false
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!delivered) {
                            delivered = true
                            manager?.removeUpdates(this)
                            continuation.resume(location)
                        }
                    }
                    override fun onProviderDisabled(provider: String) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    @Deprecated("Deprecated by Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }

                continuation.invokeOnCancellation { manager?.removeUpdates(listener) }
                runCatching {
                    manager?.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure {
                    manager?.removeUpdates(listener)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    private fun enabledProviders(): List<String> = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER
    ).filter { runCatching { manager?.isProviderEnabled(it) == true }.getOrDefault(false) }

    private fun ageMillis(location: Location): Long =
        location.toVeVakSnapshot(lastKnownSource).ageMillis
}
