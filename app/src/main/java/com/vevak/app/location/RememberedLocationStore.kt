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
 * Small app-private memory of the last real location VeVak successfully obtained.
 *
 * Android is allowed to clear its own fused/provider caches when the global Location switch is
 * turned off. Keeping one bounded copy lets VeVak return an explicitly aged last-known position
 * instead of suddenly becoming completely blind. This store is not part of configuration backup,
 * is never uploaded, and refuses mocked/invalid locations.
 */
class RememberedLocationStore(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val LATITUDE = stringPreferencesKey("latitude")
        val LONGITUDE = stringPreferencesKey("longitude")
        val ACCURACY = stringPreferencesKey("accuracy_meters")
        val CAPTURED_AT = longPreferencesKey("captured_at_epoch_ms")
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
        if (ageMillis > RememberedLocationPolicy.MAX_RETENTION_MILLIS) {
            clear()
            return null
        }

        return VeVakLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = prefs[Keys.ACCURACY]?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f },
            source = LocationSource.VeVakRemembered,
            ageMillis = ageMillis,
            isMocked = false
        )
    }

    suspend fun clear() {
        appContext.locationMemoryDataStore.edit { it.clear() }
    }
}

object RememberedLocationPolicy {
    // A very old position is more likely to mislead than to help. One day still covers the common
    // case where Location was deliberately switched off after a valid fix or trusted-place setup.
    const val MAX_RETENTION_MILLIS = 24L * 60L * 60L * 1_000L

    fun coordinatesAreValid(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    fun canPersist(snapshot: VeVakLocationSnapshot): Boolean =
        !snapshot.isMocked && coordinatesAreValid(snapshot.latitude, snapshot.longitude)

    fun ageMillis(capturedAtEpochMs: Long, nowEpochMs: Long): Long? {
        if (capturedAtEpochMs <= 0L || nowEpochMs < capturedAtEpochMs) return null
        return nowEpochMs - capturedAtEpochMs
    }
}
