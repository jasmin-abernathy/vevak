/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class PlatformLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    override val currentSource = LocationSource.FusedCurrent
    override val lastKnownSource = LocationSource.FusedLastKnown

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownLocation(): Location? = suspendCancellableCoroutine { continuation ->
        client.lastLocation
            .addOnSuccessListener { location ->
                if (continuation.isActive) continuation.resume(location)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    /**
     * Prefer a low-power fused fix first. If that does not produce anything quickly, spend the
     * remaining bounded budget on high accuracy so Play builds can let FLP combine Wi-Fi, cellular
     * and GNSS as needed without making GNSS the first choice.
     */
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(timeoutMillis: Long): Location? {
        if (timeoutMillis <= 0L) return null
        val startedAt = SystemClock.elapsedRealtime()
        val balancedBudget = minOf(BALANCED_MAX_BUDGET_MILLIS, (timeoutMillis / 2).coerceAtLeast(1_000L))
        requestCurrent(Priority.PRIORITY_BALANCED_POWER_ACCURACY, balancedBudget)?.let { return it }

        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (timeoutMillis - elapsed).coerceAtLeast(0L)
        return if (remaining >= 500L) {
            requestCurrent(Priority.PRIORITY_HIGH_ACCURACY, remaining)
        } else {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(priority: Int, timeoutMillis: Long): Location? =
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationTokenSource()
                continuation.invokeOnCancellation { cancellation.cancel() }

                client.getCurrentLocation(priority, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        }

    override fun backendStatus(): LocationBackendStatus {
        val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        val available = result == ConnectionResult.SUCCESS
        return LocationBackendStatus(
            name = "Google Fused Location Provider",
            available = available,
            detail = if (available) {
                "Google Play Services disponible."
            } else {
                "Google Play Services indisponible (code $result)."
            }
        )
    }

    companion object {
        private const val BALANCED_MAX_BUDGET_MILLIS = 3_000L
    }
}
