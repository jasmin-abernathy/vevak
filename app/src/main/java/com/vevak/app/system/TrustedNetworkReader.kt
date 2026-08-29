/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.vevak.app.model.VeVakSettings
import java.security.MessageDigest

/**
 * Reads only the currently connected Wi-Fi network. VeVak stores a one-way hash of the SSID,
 * never the network name itself.
 *
 * Android treats the SSID as location-sensitive information and can redact it when the device's
 * global Location toggle is off. To avoid immediately forgetting a trusted place when the user
 * switches Location off, VeVak also remembers the opaque Android Network handle for the already
 * verified Wi-Fi connection. That handle is useful only while the same system network session is
 * alive: after a Wi-Fi reconnect or reboot, the SSID must be readable again before the shortcut can
 * be refreshed. This deliberately fails closed rather than guessing from common IP/gateway values.
 */
class TrustedNetworkReader(private val context: Context) {
    private val appContext = context.applicationContext
    private val runtimePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun matches(settings: VeVakSettings): Boolean {
        if (!settings.hasTrustedWifiConfiguration()) return false

        // If Android exposes the SSID, it is authoritative. A visible non-match must never be
        // overridden by an old session marker.
        currentSsidHash()?.let { currentHash ->
            return currentHash.equals(settings.trustedWifiHash, ignoreCase = true)
        }

        // Location may have been switched off after this Wi-Fi was positively identified. Keep
        // trusting only the exact same Android network session; never infer a home network from a
        // generic private gateway or subnet.
        val rememberedTrustedHash = runtimePrefs.getString(KEY_LAST_VERIFIED_SSID_HASH, null)
        val rememberedSessionHash = runtimePrefs.getString(KEY_LAST_VERIFIED_NETWORK_SESSION, null)
        if (!rememberedTrustedHash.equals(settings.trustedWifiHash, ignoreCase = true)) return false
        if (rememberedSessionHash.isNullOrBlank()) return false

        return currentNetworkSessionHash()?.equals(rememberedSessionHash, ignoreCase = true) == true
    }

    fun currentSsidHash(): String? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val ssid = currentSsid()?.normalizeSsid() ?: return null
        if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID) return null

        val ssidHash = hashToken(ssid)
        rememberVerifiedSession(ssidHash)
        return ssidHash
    }

    /**
     * Returns a hash of Android's opaque handle for the active Wi-Fi network. This is not a stable
     * Wi-Fi identifier and intentionally stops matching after the network session is recreated.
     */
    fun currentNetworkSessionHash(): String? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val handle = network.networkHandle
        if (handle == 0L) return null
        return hashToken("network-session:$handle")
    }

    private fun rememberVerifiedSession(ssidHash: String) {
        val sessionHash = currentNetworkSessionHash() ?: return
        runtimePrefs.edit()
            .putString(KEY_LAST_VERIFIED_SSID_HASH, ssidHash)
            .putString(KEY_LAST_VERIFIED_NETWORK_SESSION, sessionHash)
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? {
        val wifi = appContext.getSystemService(WifiManager::class.java)
        return wifi?.connectionInfo?.ssid
    }

    private fun String.normalizeSsid(): String = trim().removeSurrounding("\"")

    companion object {
        private const val PREFS_NAME = "vevak_trusted_network_runtime"
        private const val KEY_LAST_VERIFIED_SSID_HASH = "last_verified_ssid_hash"
        private const val KEY_LAST_VERIFIED_NETWORK_SESSION = "last_verified_network_session"

        fun hashSsid(ssid: String): String = hashToken(ssid.trim().removeSurrounding("\""))

        private fun hashToken(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
