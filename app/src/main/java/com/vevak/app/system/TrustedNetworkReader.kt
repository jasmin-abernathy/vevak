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
 * - a hashed local IPv6 network fingerprint when a stable global or locally-assigned ULA prefix +
 *   IPv6 default gateway are both exposed through LinkProperties;
 * - otherwise the exact opaque Android network session for the current boot only.
 *
 * Raw SSIDs, IPv6 prefixes and gateway addresses are never persisted. The local fingerprint is an
 * exact-match fallback designed to reduce false positives; weak IPv4-only networks such as generic
 * 192.168.x.x layouts are deliberately refused as automatic proof of Maison.
 */
class TrustedNetworkReader(private val context: Context) {
    private val appContext = context.applicationContext
    private val runtimePrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun captureCurrentNetwork(): TrustedNetworkCapture? {
        val ssidHash = currentSsidHash()
        val localFingerprint = currentLocalNetworkFingerprint()

        if (ssidHash != null || localFingerprint != null) {
            clearSessionOnlyCapture()
            clearSessionConfirmationDismissal()
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

        clearSessionConfirmationDismissal()
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

    /**
     * Android can redact SSID information when system Location is switched off even while the app
     * still has location permission. If no strong observable signal contradicts the saved Maison
     * identity, VeVak may offer one explicit local confirmation for the current Wi-Fi session.
     */
    fun shouldOfferTrustedSessionConfirmation(settings: VeVakSettings): Boolean {
        if (!settings.hasTrustedWifiConfiguration()) return false
        if (matches(settings)) return false
        if (currentStrongSignalContradicts(settings.trustedWifiHash)) return false

        val currentSession = currentNetworkSessionHash() ?: return false
        val bootCount = currentBootCount()
        if (bootCount == INVALID_BOOT_COUNT) return false
        if (sessionMatches(
                rememberedSessionHash = runtimePrefs.getString(KEY_DISMISSED_NETWORK_SESSION, null),
                rememberedBootCount = runtimePrefs.getInt(KEY_DISMISSED_BOOT_COUNT, INVALID_BOOT_COUNT),
                currentSessionHash = currentSession,
                currentBootCount = bootCount
            )
        ) return false

        return true
    }

    /**
     * Binds only the current opaque Android Wi-Fi session to the already configured Maison identity.
     * This is deliberately session-scoped: it is not a new durable Wi-Fi identifier and never
     * guesses from generic IPv4 properties.
     */
    fun confirmCurrentSessionAsTrusted(settings: VeVakSettings): Boolean {
        if (!settings.hasTrustedWifiConfiguration()) return false
        if (matches(settings)) return true
        if (currentStrongSignalContradicts(settings.trustedWifiHash)) return false

        val sessionHash = currentNetworkSessionHash() ?: return false
        val bootCount = currentBootCount()
        if (bootCount == INVALID_BOOT_COUNT) return false

        val editor = runtimePrefs.edit()
        if (settings.trustedWifiHash == SESSION_ONLY_MARKER) {
            editor
                .putString(KEY_SESSION_ONLY_NETWORK_SESSION, sessionHash)
                .putInt(KEY_SESSION_ONLY_BOOT_COUNT, bootCount)
        } else {
            editor
                .putString(KEY_LAST_VERIFIED_TRUSTED_IDENTITY, settings.trustedWifiHash)
                .putString(KEY_LAST_VERIFIED_NETWORK_SESSION, sessionHash)
                .putInt(KEY_LAST_VERIFIED_BOOT_COUNT, bootCount)
        }
        editor
            .remove(KEY_DISMISSED_NETWORK_SESSION)
            .remove(KEY_DISMISSED_BOOT_COUNT)
            .apply()
        return true
    }

    /** Avoids repeatedly prompting during the same Wi-Fi session after a local refusal. */
    fun dismissCurrentSessionConfirmation(): Boolean {
        val sessionHash = currentNetworkSessionHash() ?: return false
        val bootCount = currentBootCount()
        if (bootCount == INVALID_BOOT_COUNT) return false
        runtimePrefs.edit()
            .putString(KEY_DISMISSED_NETWORK_SESSION, sessionHash)
            .putInt(KEY_DISMISSED_BOOT_COUNT, bootCount)
            .apply()
        return true
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
     * Builds a persistent exact-match token from strong LinkProperties signals. A delegated global
     * IPv6 prefix is network-specific; a locally-assigned RFC4193 ULA (`fd00::/8`) also carries a
     * randomized network identifier when configured normally. Combining the prefix with the exact
     * link-local default gateway keeps the match intentionally strict. Either may change and cause a
     * safe false-negative; weak IPv4-only networks stay session-only instead of risking a false
     * Maison match.
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
                stableNetworkIpv6Prefix(address, link.prefixLength)
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
            .remove(KEY_LAST_VERIFIED_TRUSTED_IDENTITY)
            .remove(KEY_LAST_VERIFIED_NETWORK_SESSION)
            .remove(KEY_LAST_VERIFIED_BOOT_COUNT)
            .remove(KEY_SESSION_ONLY_NETWORK_SESSION)
            .remove(KEY_SESSION_ONLY_BOOT_COUNT)
            .remove(KEY_DISMISSED_NETWORK_SESSION)
            .remove(KEY_DISMISSED_BOOT_COUNT)
            .apply()
    }

    private fun currentStrongSignalContradicts(storedIdentity: String): Boolean {
        if (storedIdentity == SESSION_ONLY_MARKER) return false

        parseStrongIdentity(storedIdentity)?.let { identity ->
            val currentSsid = currentSsidHash()
            if (currentSsid != null && identity.ssidHash != null &&
                !currentSsid.equals(identity.ssidHash, ignoreCase = true)
            ) return true

            val currentLocal = currentLocalNetworkFingerprint()
            if (currentLocal != null && identity.localFingerprint != null &&
                !currentLocal.equals(identity.localFingerprint, ignoreCase = true)
            ) return true

            return false
        }

        val currentSsid = currentSsidHash() ?: return false
        return !currentSsid.equals(storedIdentity, ignoreCase = true)
    }

    private fun rememberedVerifiedSessionMatches(storedIdentity: String): Boolean {
        val rememberedTrustedHash = runtimePrefs.getString(KEY_LAST_VERIFIED_TRUSTED_IDENTITY, null)
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

    private fun clearSessionConfirmationDismissal() {
        runtimePrefs.edit()
            .remove(KEY_DISMISSED_NETWORK_SESSION)
            .remove(KEY_DISMISSED_BOOT_COUNT)
            .apply()
    }

    private fun rememberVerifiedSession(identity: String) {
        val sessionHash = currentNetworkSessionHash() ?: return
        val bootCount = currentBootCount()
        if (bootCount == INVALID_BOOT_COUNT) return

        runtimePrefs.edit()
            .putString(KEY_LAST_VERIFIED_TRUSTED_IDENTITY, identity)
            .putString(KEY_LAST_VERIFIED_NETWORK_SESSION, sessionHash)
            .putInt(KEY_LAST_VERIFIED_BOOT_COUNT, bootCount)
            .remove(KEY_DISMISSED_NETWORK_SESSION)
            .remove(KEY_DISMISSED_BOOT_COUNT)
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
        // Historical preference key name intentionally retained on disk for migration compatibility.
        private const val KEY_LAST_VERIFIED_TRUSTED_IDENTITY = "last_verified_ssid_hash"
        private const val KEY_LAST_VERIFIED_NETWORK_SESSION = "last_verified_network_session"
        private const val KEY_LAST_VERIFIED_BOOT_COUNT = "last_verified_boot_count"
        private const val KEY_SESSION_ONLY_NETWORK_SESSION = "session_only_network_session"
        private const val KEY_SESSION_ONLY_BOOT_COUNT = "session_only_boot_count"
        private const val KEY_DISMISSED_NETWORK_SESSION = "dismissed_confirmation_network_session"
        private const val KEY_DISMISSED_BOOT_COUNT = "dismissed_confirmation_boot_count"
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

        internal fun stableNetworkIpv6Prefix(address: Inet6Address, prefixLength: Int): String? {
            if (prefixLength !in 48..64) return null
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isMulticastAddress) return null
            val raw = address.address.copyOf()
            val first = raw[0].toInt() and 0xff
            // fc00::/8 is reserved; fd00::/8 is the locally-assigned ULA space with a randomized
            // network identifier and is useful as part of an exact local fingerprint.
            if ((first and 0xfe) == 0xfc && first != 0xfd) return null

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

        private fun hashToken(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
