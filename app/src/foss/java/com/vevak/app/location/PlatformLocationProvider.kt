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
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class PlatformLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    override val currentSource = LocationSource.AndroidCurrent
    override val lastKnownSource = LocationSource.AndroidLastKnown

    /**
     * Ask every location provider exposed by the device for its cached fix. This includes the
     * Android system fused provider and vendor providers when present, in addition to network,
     * passive and GNSS. Reading a cache does not start a new location scan.
     */
    @SuppressLint("MissingPermission")
    override suspend fun lastKnownLocation(): Location? {
        val locationManager = manager ?: return null
        return allKnownProviders().mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.minWithOrNull(
            compareBy<Location> { ageMillis(it) }
                .thenBy { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
        )
    }

    /**
     * Keep the whole acquisition bounded. Non-GNSS providers are attempted first and in parallel
     * (system fused, Wi-Fi/cellular network provider and vendor providers). GNSS is kept as the
     * final fresh-location fallback instead of letting the network provider consume most of the
     * eight-second budget on its own.
     */
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(timeoutMillis: Long): Location? {
        if (timeoutMillis <= 0L) return null
        val enabled = enabledProviders()
        if (enabled.isEmpty()) return null

        val gpsEnabled = LocationManager.GPS_PROVIDER in enabled
        val primaryProviders = enabled
            .filterNot { it == LocationManager.GPS_PROVIDER || it == LocationManager.PASSIVE_PROVIDER }
            .sortedBy(::providerPriority)
            .take(MAX_PARALLEL_PRIMARY_PROVIDERS)

        val startedAt = SystemClock.elapsedRealtime()
        if (primaryProviders.isNotEmpty()) {
            val primaryBudget = if (gpsEnabled) {
                minOf(PRIMARY_MAX_BUDGET_MILLIS, (timeoutMillis / 2).coerceAtLeast(PRIMARY_MIN_BUDGET_MILLIS))
            } else {
                timeoutMillis
            }
            requestBest(primaryProviders, primaryBudget)?.let { return it }
        }

        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (timeoutMillis - elapsed).coerceAtLeast(0L)
        return if (gpsEnabled && remaining >= MIN_GPS_BUDGET_MILLIS) {
            requestOne(LocationManager.GPS_PROVIDER, remaining)
        } else {
            null
        }
    }

    override fun backendStatus(): LocationBackendStatus {
        val enabled = enabledProviders()
        return LocationBackendStatus(
            name = "Android LocationManager",
            available = manager != null && enabled.isNotEmpty(),
            detail = if (enabled.isEmpty()) {
                "Aucune source de localisation Android active."
            } else {
                "Sources actives : ${enabled.sortedBy(::providerPriority).joinToString()}"
            }
        )
    }

    private suspend fun requestBest(providers: List<String>, timeoutMillis: Long): Location? = coroutineScope {
        providers.map { provider ->
            async { requestOne(provider, timeoutMillis) }
        }.awaitAll()
            .filterNotNull()
            .minWithOrNull(
                compareBy<Location> { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
                    .thenBy { ageMillis(it) }
            )
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOne(provider: String, timeoutMillis: Long): Location? {
        val locationManager = manager ?: return null
        if (timeoutMillis <= 0L) return null

        return withTimeoutOrNull(timeoutMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val cancellation = CancellationSignal()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    runCatching {
                        locationManager.getCurrentLocation(
                            provider,
                            cancellation,
                            appContext.mainExecutor
                        ) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }.onFailure {
                        cancellation.cancel()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            } else {
                requestOneLegacy(locationManager, provider)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOneLegacy(locationManager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            var delivered = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!delivered) {
                        delivered = true
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                }

                override fun onProviderDisabled(provider: String) = Unit
                override fun onProviderEnabled(provider: String) = Unit

                @Deprecated("Deprecated by Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }

            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
            runCatching {
                locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure {
                locationManager.removeUpdates(listener)
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun allKnownProviders(): List<String> {
        val dynamic = runCatching { manager?.allProviders.orEmpty() }.getOrDefault(emptyList())
        return (listOf(SYSTEM_FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER) + dynamic)
            .distinct()
    }

    private fun enabledProviders(): List<String> {
        val enabled = runCatching { manager?.getProviders(true).orEmpty() }.getOrDefault(emptyList())
        return enabled.distinct()
    }

    private fun providerPriority(provider: String): Int = when (provider) {
        SYSTEM_FUSED_PROVIDER -> 0
        LocationManager.NETWORK_PROVIDER -> 1
        LocationManager.PASSIVE_PROVIDER -> 90
        LocationManager.GPS_PROVIDER -> 100
        else -> 10
    }

    private fun ageMillis(location: Location): Long =
        location.toVeVakSnapshot(lastKnownSource).ageMillis

    companion object {
        // The provider name existed on devices before the public FUSED_PROVIDER constant (API 31).
        private const val SYSTEM_FUSED_PROVIDER = "fused"
        private const val MAX_PARALLEL_PRIMARY_PROVIDERS = 3
        private const val PRIMARY_MIN_BUDGET_MILLIS = 1_500L
        private const val PRIMARY_MAX_BUDGET_MILLIS = 3_500L
        private const val MIN_GPS_BUDGET_MILLIS = 500L
    }
}
