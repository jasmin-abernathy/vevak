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
import android.os.SystemClock
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.vevak.app.system.TrustedNetworkReader

/**
 * Privacy-safe capability snapshot for comparing a real device with Android Location ON/OFF.
 * Counts and booleans only: no coordinates, Cell IDs, MCC/MNC, LAC/TAC, BSSIDs, SSIDs or phone
 * identifiers leave this class or enter the diagnostic report.
 */
data class LocationCapabilitySnapshot(
    val locationEnabled: Boolean,
    val knownProviderCount: Int,
    val enabledProviderCount: Int,
    val cachedProviderFixCount: Int,
    val visibleCellRecordCount: Int,
    val registeredCellRecordCount: Int,
    val offlineCellLookupReadyCount: Int,
    val cellRadioTechnologies: List<String>,
    val freshestCellAgeMillis: Long?,
    val telephonyRadioAccessSupported: Boolean,
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

        val radioAccessSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)
        } else {
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        }
        val visibleCells = if (fineGranted && radioAccessSupported) {
            val telephony = appContext.getSystemService(TelephonyManager::class.java)
            runCatching { telephony?.allCellInfo.orEmpty() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val cellSummary = CellularFallbackFeasibilityPolicy.summarize(
            visibleCells.map(::redactedCellMetadata)
        )

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
            visibleCellRecordCount = cellSummary.visibleCount,
            registeredCellRecordCount = cellSummary.registeredCount,
            offlineCellLookupReadyCount = cellSummary.lookupReadyCount,
            cellRadioTechnologies = cellSummary.radios,
            freshestCellAgeMillis = cellSummary.freshestAgeMillis,
            telephonyRadioAccessSupported = radioAccessSupported,
            wifiIdentityReadable = wifiReadable,
            localNetworkFingerprintAvailable = localFingerprintAvailable,
            activeTransport = activeTransport(),
            fineLocationPermission = fineGranted
        )
    }

    private fun redactedCellMetadata(cell: CellInfo): CellObservationMetadata = CellObservationMetadata(
        radio = radioLabel(cell),
        registered = cell.isRegistered,
        lookupIdentityComplete = lookupIdentityComplete(cell),
        ageMillis = cellAgeMillis(cell)
    )

    private fun radioLabel(cell: CellInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            modernRadioLabel(cell)?.let { return it }
        }
        return when (cell) {
            is CellInfoGsm -> "GSM"
            is CellInfoWcdma -> "UMTS"
            is CellInfoLte -> "LTE"
            is CellInfoCdma -> "CDMA"
            else -> "OTHER"
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun modernRadioLabel(cell: CellInfo): String? = when (cell) {
        is CellInfoNr -> "NR"
        is CellInfoTdscdma -> "TD-SCDMA"
        else -> null
    }

    private fun lookupIdentityComplete(cell: CellInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            modernLookupIdentityComplete(cell)?.let { return it }
        }
        return when (cell) {
            is CellInfoGsm -> cell.cellIdentity.let { identity ->
                validPlmn(identity) && validArea(identity.lac) && validCell(identity.cid)
            }
            is CellInfoWcdma -> cell.cellIdentity.let { identity ->
                validPlmn(identity) && validArea(identity.lac) && validCell(identity.cid)
            }
            is CellInfoLte -> cell.cellIdentity.let { identity ->
                validPlmn(identity) && validArea(identity.tac) && validCell(identity.ci)
            }
            // OpenCellID can represent CDMA, but Android's CellIdentityCdma does not expose MCC.
            // Do not claim a complete local lookup identity until we design a safe mapping.
            is CellInfoCdma -> false
            else -> false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun modernLookupIdentityComplete(cell: CellInfo): Boolean? = when (cell) {
        is CellInfoNr -> (cell.cellIdentity as? CellIdentityNr)?.let { identity ->
            validPlmn(identity.mccString, identity.mncString) &&
                identity.tac != CellInfo.UNAVAILABLE && identity.tac >= 0 &&
                identity.nci != CellInfo.UNAVAILABLE_LONG && identity.nci > 0L
        } ?: false
        is CellInfoTdscdma -> (cell.cellIdentity as? CellIdentityTdscdma)?.let { identity ->
            validPlmn(identity.mccString, identity.mncString) &&
                validArea(identity.lac) && validCell(identity.cid)
        } ?: false
        else -> null
    }

    @Suppress("DEPRECATION")
    private fun validPlmn(identity: CellIdentityGsm): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        validPlmn(identity.mccString, identity.mncString)
    } else {
        validLegacyPlmn(identity.mcc, identity.mnc)
    }

    @Suppress("DEPRECATION")
    private fun validPlmn(identity: CellIdentityWcdma): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        validPlmn(identity.mccString, identity.mncString)
    } else {
        validLegacyPlmn(identity.mcc, identity.mnc)
    }

    @Suppress("DEPRECATION")
    private fun validPlmn(identity: CellIdentityLte): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        validPlmn(identity.mccString, identity.mncString)
    } else {
        validLegacyPlmn(identity.mcc, identity.mnc)
    }

    private fun validPlmn(mcc: String?, mnc: String?): Boolean {
        val mccValue = mcc?.toIntOrNull()
        val mncValue = mnc?.toIntOrNull()
        return mccValue != null && mccValue in 100..999 &&
            mncValue != null && mncValue in 0..999
    }

    private fun validLegacyPlmn(mcc: Int, mnc: Int): Boolean =
        mcc != CellInfo.UNAVAILABLE && mcc in 100..999 &&
            mnc != CellInfo.UNAVAILABLE && mnc in 0..999

    private fun validArea(value: Int): Boolean = value != CellInfo.UNAVAILABLE && value > 0

    private fun validCell(value: Int): Boolean = value != CellInfo.UNAVAILABLE && value > 0

    @Suppress("DEPRECATION")
    private fun cellAgeMillis(cell: CellInfo): Long? {
        val receivedAt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cell.timestampMillis
        } else {
            cell.timeStamp / 1_000_000L
        }
        val now = SystemClock.elapsedRealtime()
        return if (receivedAt <= 0L || receivedAt > now) null else now - receivedAt
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
