/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Best-effort reverse geocoding through Android's system Geocoder.
 *
 * VeVak never requires this result: coordinates remain the source of truth and a geocoder failure
 * must never prevent an SMS from being sent. Android's geocoder implementation may itself use a
 * network backend even though VeVak does not request INTERNET permission.
 */
class SystemReverseGeocoder(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())

    suspend fun resolve(
        location: VeVakLocationSnapshot,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): String? {
        if (!Geocoder.isPresent()) return null
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return null
        if (location.accuracyMeters != null && location.accuracyMeters > MAX_ADDRESS_ACCURACY_METERS) return null

        val address = withTimeoutOrNull(timeoutMillis.coerceAtLeast(250L)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                resolveAsync(location.latitude, location.longitude)
            } else {
                resolveLegacy(location.latitude, location.longitude)
            }
        } ?: return null

        return format(address)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun resolveAsync(latitude: Double, longitude: Double): Address? =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(latitude: Double, longitude: Double): Address? =
        withContext(Dispatchers.IO) {
            runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
        }

    private fun format(address: Address): String? {
        address.getAddressLine(0)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it.replace(Regex("\\s+"), " ").take(MAX_ADDRESS_LENGTH)
        }

        val parts = listOfNotNull(
            listOfNotNull(address.subThoroughfare, address.thoroughfare)
                .joinToString(" ")
                .trim()
                .takeIf { it.isNotBlank() },
            address.postalCode?.trim()?.takeIf { it.isNotBlank() },
            address.locality?.trim()?.takeIf { it.isNotBlank() }
                ?: address.subAdminArea?.trim()?.takeIf { it.isNotBlank() },
            address.countryName?.trim()?.takeIf { it.isNotBlank() }
        ).distinct()

        return parts.joinToString(", ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.take(MAX_ADDRESS_LENGTH)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 1_500L
        const val MAX_ADDRESS_ACCURACY_METERS = 500f
        private const val MAX_ADDRESS_LENGTH = 180
    }
}
