/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.vevak.app.model.VeVakSettings
import java.security.MessageDigest

/**
 * Reads only the currently connected Wi-Fi network. VeVak stores a one-way hash of the SSID,
 * never the network name itself. If Android does not expose the SSID, callers simply fall back
 * to the normal location path.
 *
 * This deliberately avoids ACCESS_NETWORK_STATE so the canonical FOSS flavor keeps its
 * no-network-permission boundary. Android may redact the SSID; that is treated as "no match".
 */
class TrustedNetworkReader(private val context: Context) {
    fun matches(settings: VeVakSettings): Boolean {
        if (!settings.hasTrustedWifiConfiguration()) return false
        return currentSsidHash()?.equals(settings.trustedWifiHash, ignoreCase = true) == true
    }

    fun currentSsidHash(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val ssid = currentSsid()?.normalizeSsid() ?: return null
        if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID) return null
        return hashSsid(ssid)
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        return wifi?.connectionInfo?.ssid
    }

    private fun String.normalizeSsid(): String = trim().removeSurrounding("\"")

    companion object {
        fun hashSsid(ssid: String): String {
            val normalized = ssid.trim().removeSurrounding("\"")
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
