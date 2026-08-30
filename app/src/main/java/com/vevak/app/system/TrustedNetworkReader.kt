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
import java.net.Inet6Address
import java.security.MessageDigest

enum class TrustedNetworkCaptureMethod {
    SsidAndLocalFingerprint,
    SsidHash,
    LocalNetworkFingerprint,
    SessionOnly
}

data class TrustedNetworkCapture(
    val storedHash: String,
    val durable: Boolean,
    val method: TrustedNetworkCaptureMethod
)

/**
 * Reads only properties of the currently connected Wi-Fi network.
 *
 * VeVak uses the strongest local proof Android exposes without pretending that a generic network
 * property is a precise location signal:
 * - SSID hash when Android allows the connected SSID to be read;
 * - a hashed local IPv6 network fingerprint when a stable global prefix + IPv6 default gateway
 *   are both exposed through LinkProperties;
 * - otherwise the exact opaque Android network session for the current boot only.
 *
 * Raw SSIDs, IPv6 prefixes and gateway addresses are never persisted. The local fingerprint is an
 * exact-match fallback designed to reduce false positives; if Android exposes only weak/common
 * IPv4 traits such as 192.168.1.1, VeVak deliberately refuses to treat them as proof of Maison.
 */
class TrustedNetworkReader(private val context: Context) {
    private val appContext = context.applicationContext
    private val runtimePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun captureCurrentNetwork(): TrustedNetworkCapture? {
        val ssidHash = currentSsidHash()
        val localFingerprint = currentLocalNetworkFingerprint()

        if (ssidHash != null || localFingerprint != null) {
            clearSessionOnlyCapture()
            val stored = encodeStrongIdentity(ssidHash, localFingerprint)
            rememberVerifiedSession(stored)
            return TrustedNetworkCapture(
                storedHash = stored,
                durable = localFingerprint != null,
                method = when {
                    ssidHash != null && localFingerprint != null -> TrustedNetworkCaptureMethod.SsidAndLocalFingerprint
                    localFingerprint != null -> TrustedNetworkCaptureMethod.LocalNetworkFingerprint
                    else -> TrustedNetworkCaptureMethod.SsidHash
                }
            )
        }

        val sessionHash = currentNetworkSessionHash() ?: return null
        val bootCount = currentBootCount()
        if (bootCount == INVALID_BOOT_COUNT) return null

        runtimePrefs.edit()
            .putString(KEY_SESSION_ONLY_NETWORK_SESSION, sessionHash)
            .putInt(KEY_SESSION_ONLY_BOOT_COUNT, bootCount)
            .apply()

        return TrustedNetworkCapture(
            storedHash = SESSION_ONLY_MARKER,
            durable = false,
            method = TrustedNetworkCaptureMethod.SessionOnly
        )
    }

    fun matches(settings: VeVakSettings): Boolean {
        if (!settings.hasTrustedWifiConfiguration()) return false
        val stored = settings.trustedWifiHash

        if (stored == SESSION_ONLY_MARKER) {
            val rememberedSessionHash = runtimePrefs.getString(KEY_SESSION_ONLY_NETWORK_SESSION, null)
            val rememberedBootCount = runtimePrefs.getInt(KEY_SESSION_ONLY_BOOT_COUNT, INVALID_BOOT_COUNT)
            return sessionMatches(
                rememberedSessionHash = rememberedSessionHash,
                rememberedBootCount = rememberedBootCount,
                currentSessionHash = currentNetworkSessionHash(),
                currentBootCount = currentBootCount()
            )
        }

        parseStrongIdentity(stored)?.let { identity ->
            val currentSsid = currentSsidHash()
            if (currentSsid != null && identity.ssidHash != null) {
                if (!currentSsid.equals(identity.ssidHash, ignoreCase = true)) return false
                rememberVerifiedSession(stored)
                return true
            }

            if (identity.localFingerprint != null) {
                val currentLocal = currentLocalNetworkFingerprint()
                if (currentLocal != null && currentLocal.equals(identity.localFingerprint, ignoreCase = true)) {
                    rememberVerifiedSession(stored)
                    return true
                }
            }

            return rememberedVerifiedSessionMatches(stored)
        }

        // Migration path for 0.3.1-0.3.4 settings that stored only a plain SSID hash.
        currentSsidHash()?.let { currentHash ->
            if (!currentHash.equals(stored, ignoreCase = true)) return false
            rememberVerifiedSession(stored)
            return true
        }

        return rememberedVerifiedSessionMatches(stored)
    }

    fun currentSsidHash(): String? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val ssid = currentSsid()?.normalizeSsid() ?: return null
        if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID) return null
        return hashToken(ssid)
    }

    /**
     * Builds a persistent exact-match token only from strong LinkProperties signals. A global IPv6
     * prefix is network-specific while the link-local default gateway represents the local router
     * path. Either may change and cause a safe false-negative; weak IPv4-only networks stay in
     * session-only mode instead of risking a false Maison match.
     */
    fun currentLocalNetworkFingerprint(): String? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val properties = connectivity.getLinkProperties(network) ?: return null

        val prefixes = properties.linkAddresses.asSequence()
            .mapNotNull { link ->
                val address = link.address as? Inet6Address ?: return@mapNotNull null
                stableGlobalIpv6Prefix(address, link.prefixLength)
            }
            .distinct()
            .sorted()
            .toList()

        val gateways = properties.routes.asSequence()
            .filter { it.isDefaultRoute }
            .mapNotNull { route -> route.gateway as? Inet6Address }
            .filter { it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress?.substringBefore('%')?.lowercase() }
            .distinct()
            .sorted()
            .toList()

        return localNetworkFingerprint(prefixes, gateways)
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

    private fun rememberedVerifiedSessionMatches(storedIdentity: String): Boolean {
        val rememberedTrustedHash = runtimePrefs.getString(KEY_LAST_VERIFIED_SSID_HASH, null)
        val rememberedSessionHash = runtimePrefs.getString(KEY_LAST_VERIFIED_NETWORK_SESSION, null)
        val rememberedBootCount = runtimePrefs.getInt(KEY_LAST_VERIFIED_BOOT_COUNT, INVALID_BOOT_COUNT)
        if (!rememberedTrustedHash.equals(storedIdentity, ignoreCase = true)) return false
        return sessionMatches(
            rememberedSessionHash = rememberedSessionHash,
            rememberedBootCount = rememberedBootCount,
            currentSessionHash = currentNetworkSessionHash(),
            currentBootCount = currentBootCount()
        )
    }

    private fun clearSessionOnlyCapture() {
        runtimePrefs.edit()
            .remove(KEY_SESSION_ONLY_NETWORK_SESSION)
            .remove(KEY_SESSION_ONLY_BOOT_COUNT)
            .apply()
    }

    private fun rememberVerifiedSession(identity: String) {
        val sessionHash = currentNetworkSessionHash() ?: return
        val bootCount = currentBootCount()
        if (bootCount == INVALID_BOOT_COUNT) return

        runtimePrefs.edit()
            .putString(KEY_LAST_VERIFIED_SSID_HASH, identity)
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
        const val STRONG_IDENTITY_PREFIX = "trusted-network-v2|"

        private const val PREFS_NAME = "vevak_trusted_network_runtime"
        private const val KEY_LAST_VERIFIED_SSID_HASH = "last_verified_ssid_hash"
        private const val KEY_LAST_VERIFIED_NETWORK_SESSION = "last_verified_network_session"
        private const val KEY_LAST_VERIFIED_BOOT_COUNT = "last_verified_boot_count"
        private const val KEY_SESSION_ONLY_NETWORK_SESSION = "session_only_network_session"
        private const val KEY_SESSION_ONLY_BOOT_COUNT = "session_only_boot_count"
        private const val INVALID_BOOT_COUNT = -1

        private data class StrongIdentity(val ssidHash: String?, val localFingerprint: String?)

        fun hashSsid(ssid: String): String = hashToken(ssid.trim().removeSurrounding("\""))

        internal fun localNetworkFingerprint(prefixes: List<String>, gateways: List<String>): String? {
            val cleanPrefixes = prefixes.map(String::trim).filter(String::isNotBlank).distinct().sorted()
            val cleanGateways = gateways.map(String::trim).filter(String::isNotBlank).distinct().sorted()
            if (cleanPrefixes.isEmpty() || cleanGateways.isEmpty()) return null
            return hashToken(
                "local-network-v1|prefixes=${cleanPrefixes.joinToString(",")}|gateways=${cleanGateways.joinToString(",")}"
            )
        }

        internal fun sessionMatches(
            rememberedSessionHash: String?,
            rememberedBootCount: Int,
            currentSessionHash: String?,
            currentBootCount: Int
        ): Boolean {
            if (rememberedSessionHash.isNullOrBlank() || currentSessionHash.isNullOrBlank()) return false
            if (rememberedBootCount == INVALID_BOOT_COUNT || currentBootCount == INVALID_BOOT_COUNT) return false
            if (rememberedBootCount != currentBootCount) return false
            return rememberedSessionHash.equals(currentSessionHash, ignoreCase = true)
        }

        private fun encodeStrongIdentity(ssidHash: String?, localFingerprint: String?): String =
            STRONG_IDENTITY_PREFIX + "ssid=${ssidHash.orEmpty()}|local=${localFingerprint.orEmpty()}"

        private fun parseStrongIdentity(value: String): StrongIdentity? {
            if (!value.startsWith(STRONG_IDENTITY_PREFIX)) return null
            val fields = value.removePrefix(STRONG_IDENTITY_PREFIX)
                .split('|')
                .mapNotNull { part ->
                    val index = part.indexOf('=')
                    if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
                }
                .toMap()
            val ssid = fields["ssid"]?.takeIf { it.length == 64 }
            val local = fields["local"]?.takeIf { it.length == 64 }
            if (ssid == null && local == null) return null
            return StrongIdentity(ssid, local)
        }

        private fun stableGlobalIpv6Prefix(address: Inet6Address, prefixLength: Int): String? {
            if (prefixLength !in 48..64) return null
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isMulticastAddress) return null
            val raw = address.address.copyOf()
            // fc00::/7 is unique-local rather than a globally delegated network prefix.
            if (((raw[0].toInt() and 0xff) and 0xfe) == 0xfc) return null

            var remaining = prefixLength
            for (index in raw.indices) {
                when {
                    remaining >= 8 -> remaining -= 8
                    remaining > 0 -> {
                        val mask = (0xff shl (8 - remaining)) and 0xff
                        raw[index] = ((raw[index].toInt() and 0xff) and mask).toByte()
                        remaining = 0
                    }
                    else -> raw[index] = 0
                }
            }
            val hex = raw.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return "$hex/$prefixLength"
        }

        private fun hashToken(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
