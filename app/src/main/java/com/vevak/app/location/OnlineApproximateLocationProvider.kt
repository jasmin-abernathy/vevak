/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import com.vevak.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Explicit opt-in coarse network fallback.
 *
 * This provider never scans Wi-Fi or cell towers. It sends an IP-only geolocation request to
 * beaconDB when (and only when) the user has enabled network approximation in VeVak settings.
 * The public IP address is therefore visible to beaconDB. The returned coordinates are only a
 * coarse estimate and must never be presented as an exact device position.
 */
class OnlineApproximateLocationProvider {
    suspend fun locate(timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS): VeVakLocationSnapshot? =
        withContext(Dispatchers.IO) {
            val connection = runCatching {
                (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = timeoutMillis
                    readTimeout = timeoutMillis
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "VeVak/${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}; Android)")
                }
            }.getOrNull() ?: return@withContext null

            try {
                val payload = JSONObject()
                    .put("considerIp", true)
                    .put(
                        "fallbacks",
                        JSONObject()
                            .put("lacf", false)
                            .put("ipf", true)
                    )
                    .toString()

                connection.outputStream.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                }

                if (connection.responseCode !in 200..299) return@withContext null
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                if (json.optString("fallback") != "ipf") return@withContext null

                val location = json.optJSONObject("location") ?: return@withContext null
                val latitude = location.optDouble("lat", Double.NaN)
                val longitude = location.optDouble("lng", Double.NaN)
                val accuracy = json.optDouble("accuracy", Double.NaN)
                if (!latitude.isFinite() || !longitude.isFinite()) return@withContext null
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@withContext null

                VeVakLocationSnapshot(
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = accuracy
                        .takeIf { it.isFinite() && it > 0.0 }
                        ?.coerceAtMost(Float.MAX_VALUE.toDouble())
                        ?.toFloat(),
                    source = LocationSource.NetworkApproximation,
                    ageMillis = 0L,
                    isMocked = false
                )
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        const val SERVICE_NAME = "beaconDB"
        private const val ENDPOINT = "https://api.beacondb.net/v1/geolocate"
        private const val DEFAULT_TIMEOUT_MILLIS = 3_000
    }
}
