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
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.vevak.app.model.VeVakSettings
import java.security.MessageDigest

data class TrustedNetworkCapture(
    val storedHash: String,
    val durable: Boolean
)

/**
 * Reads only the currently connected Wi-Fi network.
 *
 * VeVak supports two privacy-preserving trusted-Wi-Fi modes:
 * - durable: when Android exposes the SSID, VeVak stores only a one-way hash of it;
 * - session-only: when SSID access is unavailable (for example because Location is off), VeVak
 *   stores no Wi-Fi name and trusts only the exact current Android network session in this boot.
 *
 * Session-only mode is deliberately conservative: a disconnect/reconnect or reboot invalidates it.
 * VeVak never guesses a trusted place from IP addresses, gateways or other shared network traits.
 */
class TrustedNetworkReader(private val context: Context) {
    private val appContext = context.applicationContext
    private val runtimePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun captureCurrentNetwork(): TrustedNetworkCapture? {
        currentSsidHash()?.let { ssidHash ->
            clearSessionOnlyCapture()
            return TrustedNetworkCapture(storedHash = ssidHash, durable = true)
        }

        val sessionHash = currentNetworkSessionHash() ?: return null
        val bootCount = currentBootCount()
        if (bootCount == INVALID_BOOT_COUNT) return null

        runtimePrefs.edit()
            .putString(KEY_SESSION_ONLY_NETWORK_SESSION, sessionHash)
            .putInt(KEY_SESSION_ONLY_BOOT_COUNT, bootCount)
            .apply()

        return TrustedNetworkCapture(storedHash = SESSION_ONLY_MARKER, durable = false)
    }

    fun matches(settings: VeVakSettings): Boolean {
        if (!settings.hasTrustedWifiConfiguration()) return false

        if (settings.trustedWifiHash == SESSION_ONLY_MARKER) {
            return matchesSessionOnlyCapture()
        }

        // If Android exposes the SSID, it is authoritative. A visible non-match must never be
        // overridden by an old session marker. currentSsidHash() also refreshes the continuity
        // marker only after a positive, readable network identity was obtained.
        currentSsidHash()?.let { currentHash ->
            return currentHash.equals(settings.trustedWifiHash, ignoreCase = true)
        }

        // Location may have been switched off after this Wi-Fi was positively identified. Keep
        // trusting only the exact same Android network session from the same device boot.
        val rememberedTrustedHash = runtimePrefs.getString(KEY_LAST_VERIFIED_SSID_HASH, null)
        val rememberedSessionHash = runtimePrefs.getString(KEY_LAST_VERIFIED_NETWORK_SESSION, null)
        val rememberedBootCount = runtimePrefs.getInt(KEY_LAST_VERIFIED_BOOT_COUNT, INVALID_BOOT_COUNT)
        if (!rememberedTrustedHash.equals(settings.trustedWifiHash, ignoreCase = true)) return false
        if (rememberedSessionHash.isNullOrBlank()) return false

        val currentBootCount = currentBootCount()
        if (rememberedBootCount == INVALID_BOOT_COUNT || currentBootCount == INVALID_BOOT_COUNT) return false
        if (rememberedBootCount != currentBootCount) return false

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

    fun clearRuntimeCapture() {
        runtimePrefs.edit()
            .remove(KEY_LAST_VERIFIED_SSID_HASH)
            .remove(KEY_LAST_VERIFIED_NETWORK_SESSION)
            .remove(KEY_LAST_VERIFIED_BOOT_COUNT)
            .remove(KEY_SESSION_ONLY_NETWORK_SESSION)
            .remove(KEY_SESSION_ONLY_BOOT_COUNT)
            .apply()
    }

    private fun matchesSessionOnlyCapture(): Boolean {
        val rememberedSessionHash = runtimePrefs.getString(KEY_SESSION_ONLY_NETWORK_SESSION, null)
        val rememberedBootCount = runtimePrefs.getInt(KEY_SESSION_ONLY_BOOT_COUNT, INVALID_BOOT_COUNT)
        if (rememberedSessionHash.isNullOrBlank()) return false

        val currentBootCount = currentBootCount()
        if (rememberedBootCount == INVALID_BOOT_COUNT || currentBootCount == INVALID_BOOT_COUNT) return false
        if (rememberedBootCount != currentBootCount) return false

        return currentNetworkSessionHash()?.equals(rememberedSessionHash, ignoreCase = true) == true
    }

    private fun clearSessionOnlyCapture() {
        runtimePrefs.edit()
            .remove(KEY_SESSION_ONLY_NETWORK_SESSION)
            .remove(KEY_SESSION_ONLY_BOOT_COUNT)
            .apply()
    }

    private fun rememberVerifiedSession(ssidHash: String) {
        val sessionHash = currentNetworkSessionHash() ?: return
        val bootCount = currentBootCount()
        if (bootCount == INVALID_BOOT_COUNT) return

        runtimePrefs.edit()
            .putString(KEY_LAST_VERIFIED_SSID_HASH, ssidHash)
            .putString(KEY_LAST_VERIFIED_NETWORK_SESSION, sessionHash)
            .putInt(KEY_LAST_VERIFIED_BOOT_COUNT, bootCount)
            .apply()
    }

    private fun currentBootCount(): Int = runCatching {
        Settings.Global.getInt(
            appContext.contentResolver,
            Settings.Global.BOOT_COUNT,
            INVALID_BOOT_COUNT
        )
    }.getOrDefault(INVALID_BOOT_COUNT)

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? {
        val wifi = appContext.getSystemService(WifiManager::class.java)
        return wifi?.connectionInfo?.ssid
    }

    private fun String.normalizeSsid(): String = trim().removeSurrounding("\"")

    companion object {
        const val SESSION_ONLY_MARKER = "session-only-v1"

        private const val PREFS_NAME = "vevak_trusted_network_runtime"
        private const val KEY_LAST_VERIFIED_SSID_HASH = "last_verified_ssid_hash"
        private const val KEY_LAST_VERIFIED_NETWORK_SESSION = "last_verified_network_session"
        private const val KEY_LAST_VERIFIED_BOOT_COUNT = "last_verified_boot_count"
        private const val KEY_SESSION_ONLY_NETWORK_SESSION = "session_only_network_session"
        private const val KEY_SESSION_ONLY_BOOT_COUNT = "session_only_boot_count"
        private const val INVALID_BOOT_COUNT = -1

        fun hashSsid(ssid: String): String = hashToken(ssid.trim().removeSurrounding("\""))

        private fun hashToken(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
