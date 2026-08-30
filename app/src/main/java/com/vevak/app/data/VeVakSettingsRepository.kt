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
import androidx.datastore.preferences.core.longPreferencesKey
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
        val ADDITIONAL_CONTACTS = stringPreferencesKey("additional_trusted_contacts_v1")
        val INCLUDE_BATTERY = booleanPreferencesKey("include_battery")
        val INCLUDE_ACCURACY = booleanPreferencesKey("include_accuracy")
        val MAP_PROVIDER = stringPreferencesKey("map_provider")
        val MIN_INTERVAL = intPreferencesKey("min_request_interval_seconds")
        val CACHE_AGE = intPreferencesKey("max_cached_location_age_seconds")
        val TIMEOUT = intPreferencesKey("location_timeout_seconds")
        val STALE_FALLBACK = booleanPreferencesKey("allow_stale_fallback")
        val NETWORK_APPROXIMATION = booleanPreferencesKey("allow_network_approximation")
        val AUTH_GRANTED_AT = longPreferencesKey("authorization_granted_at_epoch_ms")
        val AUTH_EXPIRES_AT = longPreferencesKey("authorization_expires_at_epoch_ms")
        val DURESS_ENABLED = booleanPreferencesKey("duress_enabled")
        val DURESS_PHRASE = stringPreferencesKey("duress_phrase")
        val FALLBACK_LAT = stringPreferencesKey("fallback_latitude")
        val FALLBACK_LON = stringPreferencesKey("fallback_longitude")
        val FALLBACK_ACCURACY = stringPreferencesKey("fallback_accuracy_meters")
        val TRUSTED_WIFI_ENABLED = booleanPreferencesKey("trusted_wifi_enabled")
        val TRUSTED_WIFI_HASH = stringPreferencesKey("trusted_wifi_hash")
        val TRUSTED_PLACE_LABEL = stringPreferencesKey("trusted_place_label")
        val DISCREET_MODE_UNTIL = longPreferencesKey("discreet_mode_until_epoch_ms")
    }

    val settingsFlow: Flow<VeVakSettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun current(): VeVakSettings = settingsFlow.first()

    suspend fun save(settings: VeVakSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.COMPLETED] = settings.completedOnboarding
            prefs[Keys.CONTACT_NAME] = settings.contactName.trim()
            prefs[Keys.CONTACT_PHONE] = settings.contactPhone.trim()
            prefs[Keys.TRIGGER] = settings.triggerPhrase.trim()
            prefs[Keys.ADDITIONAL_CONTACTS] = TrustedContactStorageCodec.encode(
                settings.additionalTrustedContacts
                    .filter { it.id != VeVakSettings.PRIMARY_CONTACT_ID && it.isConfigured() }
                    .take(VeVakSettings.MAX_TRUSTED_CONTACTS - 1)
            )
            prefs[Keys.INCLUDE_BATTERY] = settings.includeBattery
            prefs[Keys.INCLUDE_ACCURACY] = settings.includeAccuracy
            prefs[Keys.MAP_PROVIDER] = settings.mapProvider.name
            prefs[Keys.MIN_INTERVAL] = settings.minRequestIntervalSeconds.coerceIn(900, 3600)
            prefs[Keys.CACHE_AGE] = settings.maxCachedLocationAgeSeconds.coerceIn(30, 3600)
            prefs[Keys.TIMEOUT] = settings.locationTimeoutSeconds.coerceIn(3, 30)
            prefs[Keys.STALE_FALLBACK] = settings.allowStaleFallback
            prefs[Keys.NETWORK_APPROXIMATION] = settings.allowNetworkApproximation
            prefs[Keys.AUTH_GRANTED_AT] = settings.authorizationGrantedAtEpochMs.coerceAtLeast(0L)
            prefs[Keys.AUTH_EXPIRES_AT] = settings.authorizationExpiresAtEpochMs.coerceAtLeast(0L)
            prefs[Keys.DURESS_ENABLED] = settings.duressEnabled
            prefs[Keys.DURESS_PHRASE] = settings.duressPhrase.trim()
            settings.fallbackLatitude?.let { prefs[Keys.FALLBACK_LAT] = it.toString() } ?: prefs.remove(Keys.FALLBACK_LAT)
            settings.fallbackLongitude?.let { prefs[Keys.FALLBACK_LON] = it.toString() } ?: prefs.remove(Keys.FALLBACK_LON)
            settings.fallbackAccuracyMeters?.let { prefs[Keys.FALLBACK_ACCURACY] = it.toString() } ?: prefs.remove(Keys.FALLBACK_ACCURACY)
            prefs[Keys.TRUSTED_WIFI_ENABLED] = settings.trustedWifiEnabled
            prefs[Keys.TRUSTED_WIFI_HASH] = settings.trustedWifiHash.trim()
            prefs[Keys.TRUSTED_PLACE_LABEL] = settings.trustedPlaceLabel.trim().ifBlank { "Maison" }
            prefs[Keys.DISCREET_MODE_UNTIL] = settings.discreetModeUntilEpochMs.coerceAtLeast(0L)
        }
    }

    suspend fun reset() {
        context.settingsDataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings(): VeVakSettings {
        val provider = runCatching {
            MapProvider.valueOf(this[Keys.MAP_PROVIDER] ?: MapProvider.CoMaps.name)
        }.getOrDefault(MapProvider.CoMaps)
        val completed = this[Keys.COMPLETED] ?: false
        val storedTrigger = this[Keys.TRIGGER].orEmpty()
        val migratedTrigger = if (!completed && storedTrigger.equals(LEGACY_TRIGGER_PLACEHOLDER, ignoreCase = true)) {
            ""
        } else {
            storedTrigger
        }

        return VeVakSettings(
            completedOnboarding = completed,
            contactName = this[Keys.CONTACT_NAME].orEmpty(),
            contactPhone = this[Keys.CONTACT_PHONE].orEmpty(),
            triggerPhrase = migratedTrigger,
            additionalTrustedContacts = TrustedContactStorageCodec.decode(this[Keys.ADDITIONAL_CONTACTS].orEmpty())
                .filter { it.id != VeVakSettings.PRIMARY_CONTACT_ID }
                .take(VeVakSettings.MAX_TRUSTED_CONTACTS - 1),
            includeBattery = this[Keys.INCLUDE_BATTERY] ?: true,
            includeAccuracy = this[Keys.INCLUDE_ACCURACY] ?: true,
            mapProvider = provider,
            minRequestIntervalSeconds = (this[Keys.MIN_INTERVAL] ?: 900).coerceIn(900, 3600),
            maxCachedLocationAgeSeconds = this[Keys.CACHE_AGE] ?: 120,
            locationTimeoutSeconds = this[Keys.TIMEOUT] ?: 8,
            allowStaleFallback = this[Keys.STALE_FALLBACK] ?: true,
            allowNetworkApproximation = this[Keys.NETWORK_APPROXIMATION] ?: false,
            authorizationGrantedAtEpochMs = this[Keys.AUTH_GRANTED_AT] ?: 0L,
            authorizationExpiresAtEpochMs = this[Keys.AUTH_EXPIRES_AT] ?: 0L,
            duressEnabled = this[Keys.DURESS_ENABLED] ?: false,
            duressPhrase = this[Keys.DURESS_PHRASE].orEmpty(),
            fallbackLatitude = this[Keys.FALLBACK_LAT]?.toDoubleOrNull(),
            fallbackLongitude = this[Keys.FALLBACK_LON]?.toDoubleOrNull(),
            fallbackAccuracyMeters = this[Keys.FALLBACK_ACCURACY]?.toFloatOrNull(),
            trustedWifiEnabled = this[Keys.TRUSTED_WIFI_ENABLED] ?: false,
            trustedWifiHash = this[Keys.TRUSTED_WIFI_HASH].orEmpty(),
            trustedPlaceLabel = this[Keys.TRUSTED_PLACE_LABEL]?.trim().takeUnless { it.isNullOrBlank() } ?: "Maison",
            discreetModeUntilEpochMs = this[Keys.DISCREET_MODE_UNTIL] ?: 0L
        )
    }

    private companion object {
        const val LEGACY_TRIGGER_PLACEHOLDER = "Info Mari"
    }
}
