/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.location

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.locationMemoryDataStore by preferencesDataStore(name = "vevak_location_memory")

/**
 * App-private memory of VeVak's last coordinate-bearing positions.
 *
 * Two snapshots are intentionally retained:
 * - the latest position from any legitimate source, including an explicitly opted-in IP/network
 *   estimate, for authorised automatic phrase-key replies;
 * - the latest real/local position, for explicit manual sharing and the emergency shortcut, whose
 *   existing contract must never silently become an IP estimate.
 *
 * Android may clear its own location caches when the global Location switch is disabled. VeVak
 * therefore keeps these small local snapshots until they are replaced, explicitly cleared or the
 * app is reset. Their age is always carried to the reply, so an old point is never presented as
 * current. Nothing here creates continuous tracking or uploads a location.
 */
class RememberedLocationStore(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        // Legacy keys are kept as the "latest any" slot. In released versions they contained only
        // real points, so this remains migration-safe.
        val LATITUDE = stringPreferencesKey("latitude")
        val LONGITUDE = stringPreferencesKey("longitude")
        val ACCURACY = stringPreferencesKey("accuracy_meters")
        val CAPTURED_AT = longPreferencesKey("captured_at_epoch_ms")
        val SOURCE = stringPreferencesKey("source")

        val REAL_LATITUDE = stringPreferencesKey("real_latitude")
        val REAL_LONGITUDE = stringPreferencesKey("real_longitude")
        val REAL_ACCURACY = stringPreferencesKey("real_accuracy_meters")
        val REAL_CAPTURED_AT = longPreferencesKey("real_captured_at_epoch_ms")
        val REAL_SOURCE = stringPreferencesKey("real_source")
    }

    suspend fun remember(snapshot: VeVakLocationSnapshot, nowMillis: Long = System.currentTimeMillis()) {
        if (!RememberedLocationPolicy.canPersist(snapshot)) return

        val capturedAt = (nowMillis - snapshot.ageMillis.coerceAtLeast(0L)).coerceAtLeast(1L)
        appContext.locationMemoryDataStore.edit { prefs ->
            prefs[Keys.LATITUDE] = snapshot.latitude.toString()
            prefs[Keys.LONGITUDE] = snapshot.longitude.toString()
            snapshot.accuracyMeters?.takeIf { it.isFinite() && it > 0f }?.let {
                prefs[Keys.ACCURACY] = it.toString()
            } ?: prefs.remove(Keys.ACCURACY)
            prefs[Keys.CAPTURED_AT] = capturedAt
            prefs[Keys.SOURCE] = snapshot.source.name

            if (RememberedLocationPolicy.isRealLocal(snapshot)) {
                prefs[Keys.REAL_LATITUDE] = snapshot.latitude.toString()
                prefs[Keys.REAL_LONGITUDE] = snapshot.longitude.toString()
                snapshot.accuracyMeters?.takeIf { it.isFinite() && it > 0f }?.let {
                    prefs[Keys.REAL_ACCURACY] = it.toString()
                } ?: prefs.remove(Keys.REAL_ACCURACY)
                prefs[Keys.REAL_CAPTURED_AT] = capturedAt
                prefs[Keys.REAL_SOURCE] = snapshot.source.name
            }
        }
    }

    /** Latest remembered coordinate regardless of its legitimate source. */
    suspend fun read(nowMillis: Long = System.currentTimeMillis()): VeVakLocationSnapshot? {
        val prefs = appContext.locationMemoryDataStore.data.first()
        return snapshotFrom(
            latitudeRaw = prefs[Keys.LATITUDE],
            longitudeRaw = prefs[Keys.LONGITUDE],
            accuracyRaw = prefs[Keys.ACCURACY],
            capturedAt = prefs[Keys.CAPTURED_AT],
            sourceRaw = prefs[Keys.SOURCE],
            nowMillis = nowMillis
        )
    }

    /**
     * Latest remembered real/local point. If the dedicated slot does not exist yet, fall back to
     * the legacy slot only when it is not an IP/network estimate.
     */
    suspend fun readReal(nowMillis: Long = System.currentTimeMillis()): VeVakLocationSnapshot? {
        val prefs = appContext.locationMemoryDataStore.data.first()
        val dedicated = snapshotFrom(
            latitudeRaw = prefs[Keys.REAL_LATITUDE],
            longitudeRaw = prefs[Keys.REAL_LONGITUDE],
            accuracyRaw = prefs[Keys.REAL_ACCURACY],
            capturedAt = prefs[Keys.REAL_CAPTURED_AT],
            sourceRaw = prefs[Keys.REAL_SOURCE],
            nowMillis = nowMillis
        )
        if (dedicated != null) return dedicated

        return snapshotFrom(
            latitudeRaw = prefs[Keys.LATITUDE],
            longitudeRaw = prefs[Keys.LONGITUDE],
            accuracyRaw = prefs[Keys.ACCURACY],
            capturedAt = prefs[Keys.CAPTURED_AT],
            sourceRaw = prefs[Keys.SOURCE],
            nowMillis = nowMillis
        )?.takeIf(RememberedLocationPolicy::isRealLocal)
    }

    suspend fun clear() {
        appContext.locationMemoryDataStore.edit { it.clear() }
    }

    private fun snapshotFrom(
        latitudeRaw: String?,
        longitudeRaw: String?,
        accuracyRaw: String?,
        capturedAt: Long?,
        sourceRaw: String?,
        nowMillis: Long
    ): VeVakLocationSnapshot? {
        val latitude = latitudeRaw?.toDoubleOrNull() ?: return null
        val longitude = longitudeRaw?.toDoubleOrNull() ?: return null
        val captured = capturedAt ?: return null
        val ageMillis = RememberedLocationPolicy.ageMillis(captured, nowMillis) ?: return null
        if (!RememberedLocationPolicy.coordinatesAreValid(latitude, longitude)) return null

        val source = sourceRaw
            ?.let { raw -> LocationSource.entries.firstOrNull { it.name == raw } }
            ?.takeUnless { it == LocationSource.SafetyFallback }
            ?: LocationSource.VeVakRemembered

        return VeVakLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyRaw?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f },
            source = source,
            ageMillis = ageMillis,
            isMocked = false
        )
    }
}

object RememberedLocationPolicy {
    fun coordinatesAreValid(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun canPersist(snapshot: VeVakLocationSnapshot): Boolean =
        !snapshot.isMocked &&
            snapshot.source != LocationSource.SafetyFallback &&
            coordinatesAreValid(snapshot.latitude, snapshot.longitude)

    fun isRealLocal(snapshot: VeVakLocationSnapshot): Boolean =
        canPersist(snapshot) && !snapshot.isApproximateNetworkEstimate()

    fun ageMillis(capturedAtEpochMs: Long, nowEpochMs: Long): Long? {
        if (capturedAtEpochMs <= 0L || nowEpochMs < capturedAtEpochMs) return null
        return nowEpochMs - capturedAtEpochMs
    }
}
