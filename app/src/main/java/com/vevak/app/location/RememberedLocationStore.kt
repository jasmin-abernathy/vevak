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
 * App-private memory of the last coordinate-bearing position VeVak successfully obtained.
 *
 * Android may clear its own provider/fused caches when the global Location switch is turned off.
 * VeVak therefore keeps one local snapshot so a later authorised SMS can still return the last
 * position that was actually available. The snapshot is kept until it is replaced, explicitly
 * cleared or VeVak is reset; its age is always carried in the reply so an old point is never
 * presented as current.
 *
 * Network/IP estimates can also be remembered when the user opted into that source. Their source is
 * persisted so they remain clearly labelled as approximate after a restart. Safety/duress fallback
 * coordinates are deliberately never written here.
 */
class RememberedLocationStore(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val LATITUDE = stringPreferencesKey("latitude")
        val LONGITUDE = stringPreferencesKey("longitude")
        val ACCURACY = stringPreferencesKey("accuracy_meters")
        val CAPTURED_AT = longPreferencesKey("captured_at_epoch_ms")
        val SOURCE = stringPreferencesKey("source")
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
        }
    }

    suspend fun read(nowMillis: Long = System.currentTimeMillis()): VeVakLocationSnapshot? {
        val prefs = appContext.locationMemoryDataStore.data.first()
        val latitude = prefs[Keys.LATITUDE]?.toDoubleOrNull() ?: return null
        val longitude = prefs[Keys.LONGITUDE]?.toDoubleOrNull() ?: return null
        val capturedAt = prefs[Keys.CAPTURED_AT] ?: return null
        val ageMillis = RememberedLocationPolicy.ageMillis(capturedAt, nowMillis) ?: return null

        if (!RememberedLocationPolicy.coordinatesAreValid(latitude, longitude)) {
            clear()
            return null
        }

        val storedSource = prefs[Keys.SOURCE]
            ?.let { raw -> LocationSource.entries.firstOrNull { it.name == raw } }
            ?.takeUnless { it == LocationSource.SafetyFallback }
            ?: LocationSource.VeVakRemembered

        return VeVakLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = prefs[Keys.ACCURACY]?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f },
            source = storedSource,
            ageMillis = ageMillis,
            isMocked = false
        )
    }

    suspend fun clear() {
        appContext.locationMemoryDataStore.edit { it.clear() }
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

    fun ageMillis(capturedAtEpochMs: Long, nowEpochMs: Long): Long? {
        if (capturedAtEpochMs <= 0L || nowEpochMs < capturedAtEpochMs) return null
        return nowEpochMs - capturedAtEpochMs
    }
}
