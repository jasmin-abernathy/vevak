/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vevak.app.model.MapProvider
import com.vevak.app.model.VeVakSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "vevak_settings")

class VeVakSettingsRepository(private val context: Context) {
    private object Keys {
        val COMPLETED = booleanPreferencesKey("completed_onboarding")
        val CONTACT_NAME = stringPreferencesKey("contact_name")
        val CONTACT_PHONE = stringPreferencesKey("contact_phone")
        val TRIGGER = stringPreferencesKey("trigger_phrase")
        val INCLUDE_BATTERY = booleanPreferencesKey("include_battery")
        val INCLUDE_ACCURACY = booleanPreferencesKey("include_accuracy")
        val MAP_PROVIDER = stringPreferencesKey("map_provider")
        val MIN_INTERVAL = intPreferencesKey("min_request_interval_seconds")
        val CACHE_AGE = intPreferencesKey("max_cached_location_age_seconds")
        val TIMEOUT = intPreferencesKey("location_timeout_seconds")
        val STALE_FALLBACK = booleanPreferencesKey("allow_stale_fallback")
    }

    val settingsFlow: Flow<VeVakSettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): VeVakSettings = settingsFlow.first()

    suspend fun save(settings: VeVakSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.COMPLETED] = settings.completedOnboarding
            prefs[Keys.CONTACT_NAME] = settings.contactName.trim()
            prefs[Keys.CONTACT_PHONE] = settings.contactPhone.trim()
            prefs[Keys.TRIGGER] = settings.triggerPhrase.trim()
            prefs[Keys.INCLUDE_BATTERY] = settings.includeBattery
            prefs[Keys.INCLUDE_ACCURACY] = settings.includeAccuracy
            prefs[Keys.MAP_PROVIDER] = settings.mapProvider.name
            prefs[Keys.MIN_INTERVAL] = settings.minRequestIntervalSeconds.coerceIn(30, 3600)
            prefs[Keys.CACHE_AGE] = settings.maxCachedLocationAgeSeconds.coerceIn(30, 3600)
            prefs[Keys.TIMEOUT] = settings.locationTimeoutSeconds.coerceIn(3, 30)
            prefs[Keys.STALE_FALLBACK] = settings.allowStaleFallback
        }
    }

    suspend fun reset() {
        context.settingsDataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings(): VeVakSettings {
        val provider = runCatching {
            MapProvider.valueOf(this[Keys.MAP_PROVIDER] ?: MapProvider.CoMaps.name)
        }.getOrDefault(MapProvider.CoMaps)

        return VeVakSettings(
            completedOnboarding = this[Keys.COMPLETED] ?: false,
            contactName = this[Keys.CONTACT_NAME].orEmpty(),
            contactPhone = this[Keys.CONTACT_PHONE].orEmpty(),
            triggerPhrase = this[Keys.TRIGGER] ?: "Info Mari",
            includeBattery = this[Keys.INCLUDE_BATTERY] ?: true,
            includeAccuracy = this[Keys.INCLUDE_ACCURACY] ?: true,
            mapProvider = provider,
            minRequestIntervalSeconds = this[Keys.MIN_INTERVAL] ?: 60,
            maxCachedLocationAgeSeconds = this[Keys.CACHE_AGE] ?: 120,
            locationTimeoutSeconds = this[Keys.TIMEOUT] ?: 8,
            allowStaleFallback = this[Keys.STALE_FALLBACK] ?: true
        )
    }
}
