/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.model

enum class MapProvider(val label: String) {
    CoMaps("CoMaps / application cartographique"),
    OpenStreetMap("OpenStreetMap"),
    GoogleMaps("Google Maps")
}

enum class AuthorizationDuration(val days: Int, val label: String) {
    OneDay(1, "24 heures"),
    SevenDays(7, "7 jours"),
    ThirtyDays(30, "30 jours");

    fun expiresAt(nowMillis: Long): Long = nowMillis + days * DAY_MILLIS

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}

data class VeVakSettings(
    val completedOnboarding: Boolean = false,
    val contactName: String = "",
    val contactPhone: String = "",
    val triggerPhrase: String = "Info Mari",
    val includeBattery: Boolean = true,
    val includeAccuracy: Boolean = true,
    val mapProvider: MapProvider = MapProvider.CoMaps,
    val minRequestIntervalSeconds: Int = 900,
    val maxCachedLocationAgeSeconds: Int = 120,
    val locationTimeoutSeconds: Int = 8,
    val allowStaleFallback: Boolean = true,
    val authorizationGrantedAtEpochMs: Long = 0L,
    val authorizationExpiresAtEpochMs: Long = 0L,
    val duressEnabled: Boolean = false,
    val duressPhrase: String = "",
    val fallbackLatitude: Double? = null,
    val fallbackLongitude: Double? = null,
    val fallbackAccuracyMeters: Float? = null
) {
    fun hasActiveAuthorization(nowMillis: Long = System.currentTimeMillis()): Boolean =
        authorizationGrantedAtEpochMs > 0L &&
            authorizationExpiresAtEpochMs > authorizationGrantedAtEpochMs &&
            nowMillis in authorizationGrantedAtEpochMs until authorizationExpiresAtEpochMs

    fun hasFallbackCoordinates(): Boolean =
        fallbackLatitude?.let { it in -90.0..90.0 } == true &&
            fallbackLongitude?.let { it in -180.0..180.0 } == true

    fun hasValidDuressConfiguration(): Boolean =
        !duressEnabled || (duressPhrase.isNotBlank() && hasFallbackCoordinates())

    fun isConfigured(nowMillis: Long = System.currentTimeMillis()): Boolean =
        completedOnboarding &&
            contactPhone.isNotBlank() &&
            triggerPhrase.isNotBlank() &&
            hasActiveAuthorization(nowMillis) &&
            hasValidDuressConfiguration()
}
