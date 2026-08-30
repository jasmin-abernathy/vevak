/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.vevak.app.system.TrustedNetworkReader

/**
 * Privacy-safe capability snapshot for comparing a real device with Android Location ON/OFF.
 * Counts and booleans only: no coordinates, Cell IDs, BSSIDs, SSIDs or phone identifiers leave
 * this class or enter the diagnostic report.
 */
data class LocationCapabilitySnapshot(
    val locationEnabled: Boolean,
    val knownProviderCount: Int,
    val enabledProviderCount: Int,
    val cachedProviderFixCount: Int,
    val visibleCellRecordCount: Int,
    val wifiIdentityReadable: Boolean,
    val localNetworkFingerprintAvailable: Boolean,
    val activeTransport: String,
    val fineLocationPermission: Boolean
)

class LocationCapabilityProbe(private val context: Context) {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION", "MissingPermission")
    fun snapshot(): LocationCapabilitySnapshot {
        val fineGranted = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            runCatching {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            }.getOrDefault(false)
        }

        val providers = runCatching { locationManager?.allProviders.orEmpty().distinct() }.getOrDefault(emptyList())
        val enabledProviders = runCatching { locationManager?.getProviders(true).orEmpty().distinct() }.getOrDefault(emptyList())
        val cachedFixes = if (fineGranted || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            providers.count { provider ->
                runCatching { locationManager?.getLastKnownLocation(provider) != null }.getOrDefault(false)
            }
        } else {
            0
        }

        val visibleCells = if (fineGranted) {
            val telephony = appContext.getSystemService(TelephonyManager::class.java)
            runCatching { telephony?.allCellInfo?.size ?: 0 }.getOrDefault(0)
        } else {
            0
        }

        val wifiReadable = if (fineGranted) {
            val wifi = appContext.getSystemService(WifiManager::class.java)
            runCatching {
                val info = wifi?.connectionInfo ?: return@runCatching false
                val bssid = info.bssid.orEmpty()
                val ssid = info.ssid.orEmpty().trim().removeSurrounding("\"")
                bssid.isNotBlank() && bssid != REDACTED_BSSID &&
                    ssid.isNotBlank() && ssid != WifiManager.UNKNOWN_SSID
            }.getOrDefault(false)
        } else {
            false
        }

        val localFingerprintAvailable = runCatching {
            TrustedNetworkReader(appContext).currentLocalNetworkFingerprint() != null
        }.getOrDefault(false)

        return LocationCapabilitySnapshot(
            locationEnabled = locationEnabled,
            knownProviderCount = providers.size,
            enabledProviderCount = enabledProviders.size,
            cachedProviderFixCount = cachedFixes,
            visibleCellRecordCount = visibleCells,
            wifiIdentityReadable = wifiReadable,
            localNetworkFingerprintAvailable = localFingerprintAvailable,
            activeTransport = activeTransport(),
            fineLocationPermission = fineGranted
        )
    }

    private fun activeTransport(): String {
        if (!granted(Manifest.permission.ACCESS_NETWORK_STATE)) return "unknown"
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return "none"
        val network = connectivity.activeNetwork ?: return "none"
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return "none"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val REDACTED_BSSID = "02:00:00:00:00:00"
    }
}
