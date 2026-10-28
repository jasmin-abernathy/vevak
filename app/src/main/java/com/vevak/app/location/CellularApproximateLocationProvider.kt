/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.vevak.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single serving cell. Never stored locally or logged, only transmitted to beaconDB
 * after separate cellular consent, while Android system Location remains enabled.
 */
internal data class CellTowerObservation(
    val radioType: String, val mcc: Int, val mnc: Int, val areaCode: Int, val cellId: Long
) {
    fun valid(): Boolean =
        radioType in setOf("gsm", "wcdma", "lte", "nr") &&
            mcc in 1..999 && mnc in 0..999 &&
            areaCode in 0..(if (radioType == "nr") 16_777_215 else 65_535) &&
            cellId in 1L..(when (radioType) {
                "nr" -> 68_719_476_735L
                "gsm" -> 65_535L
                else -> 268_435_455L
            })

    fun toJson(): JSONObject = JSONObject()
        .put("mobileCountryCode", mcc)
        .put("mobileNetworkCode", mnc)
        .put("locationAreaCode", areaCode)
        .put(if (radioType == "nr") "newRadioCellId" else "cellId", cellId)
}

/**
 * An opt-in cellular positioning fallback, NOT a workaround for Android Location OFF.
 * Uses an existing cached serving-cell observation, no active scan and no history.
 * beaconDB may not know this tower; null falls back to IP (if separately opted in)
 * or the single local remembered location.
 */
internal class CellularApproximateLocationProvider(context: Context) {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    suspend fun locate(): VeVakLocationSnapshot? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) return@withContext null
        val location = appContext.getSystemService(LocationManager::class.java) ?: return@withContext null
        if (!enabled(location)) return@withContext null
        val telephony = appContext.getSystemService(TelephonyManager::class.java) ?: return@withContext null
        val cells = runCatching { telephony.allCellInfo.orEmpty() }.getOrDefault(emptyList())
        val tower = cells.asSequence().filter { it.isRegistered }.mapNotNull(::observation)
            .firstOrNull { it.valid() } ?: return@withContext null

        // Recheck the user's system choice immediately before transmitting any radio identity.
        if (!enabled(location)) return@withContext null
        request(tower)
    }

    private fun enabled(manager: LocationManager): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.isLocationEnabled
        else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun observation(info: CellInfo): CellTowerObservation? {
        fun codes(oldMcc: Int, oldMnc: Int, modernMcc: String?, modernMnc: String?): Pair<Int, Int> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                (modernMcc?.toIntOrNull() ?: -1) to (modernMnc?.toIntOrNull() ?: -1)
            else oldMcc to oldMnc
        return when (info) {
            is CellInfoLte -> {
                val c = info.cellIdentity
                val (mcc,mnc) = codes(c.mcc,c.mnc,
                    if (Build.VERSION.SDK_INT >= 28) c.mccString else null,
                    if (Build.VERSION.SDK_INT >= 28) c.mncString else null)
                CellTowerObservation("lte",mcc,mnc,c.tac,c.ci.toLong())
            }
            is CellInfoGsm -> {
                val c = info.cellIdentity
                val (mcc,mnc) = codes(c.mcc,c.mnc,
                    if (Build.VERSION.SDK_INT >= 28) c.mccString else null,
                    if (Build.VERSION.SDK_INT >= 28) c.mncString else null)
                CellTowerObservation("gsm",mcc,mnc,c.lac,c.cid.toLong())
            }
            is CellInfoWcdma -> {
                val c = info.cellIdentity
                val (mcc,mnc) = codes(c.mcc,c.mnc,
                    if (Build.VERSION.SDK_INT >= 28) c.mccString else null,
                    if (Build.VERSION.SDK_INT >= 28) c.mncString else null)
                CellTowerObservation("wcdma",mcc,mnc,c.lac,c.cid.toLong())
            }
            is CellInfoNr -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val c = info.cellIdentity as? android.telephony.CellIdentityNr ?: return null
                CellTowerObservation("nr", c.mccString?.toIntOrNull() ?: -1,
                    c.mncString?.toIntOrNull() ?: -1, c.tac, c.nci)
            } else null
            // TD-SCDMA and CDMA need independent verified mappings.
            else -> null
        }
    }

    private fun request(tower: CellTowerObservation): VeVakLocationSnapshot? {
        val payload = JSONObject()
            .put("radioType", tower.radioType)
            .put("cellTowers", JSONArray().put(tower.toJson()))
            .put("considerIp", false)
            .put("fallbacks", JSONObject().put("ipf", false).put("lacf", false))
            .toString().toByteArray(Charsets.UTF_8)

        val connection = runCatching {
            (URL("https://api.beacondb.net/v1/geolocate").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6_000
                readTimeout = 6_000
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VeVak/" + BuildConfig.VERSION_NAME + " (" + BuildConfig.FLAVOR + "; Android)")
            }
        }.getOrNull() ?: return null

        return try {
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            if (connection.responseCode !in 200..299) return null
            val body = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            // Never label an IP/network fallback as a cellular estimate.
            if (body.optString("fallback").isNotBlank()) return null
            val position = body.optJSONObject("location") ?: return null
            val lat = position.optDouble("lat", Double.NaN)
            val lon = position.optDouble("lng", Double.NaN)
            val radius = body.optDouble("accuracy", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
                !radius.isFinite() || radius <= 0.0) return null
            VeVakLocationSnapshot(lat, lon,
                radius.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat(),
                LocationSource.CellularApproximation, 0L, false)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
