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

data class VeVakSettings(
    val completedOnboarding: Boolean = false,
    val contactName: String = "",
    val contactPhone: String = "",
    val triggerPhrase: String = "Info Mari",
    val includeBattery: Boolean = true,
    val includeAccuracy: Boolean = true,
    val mapProvider: MapProvider = MapProvider.CoMaps,
    val minRequestIntervalSeconds: Int = 60,
    val maxCachedLocationAgeSeconds: Int = 120,
    val locationTimeoutSeconds: Int = 8,
    val allowStaleFallback: Boolean = true
) {
    fun isConfigured(): Boolean =
        completedOnboarding && contactPhone.isNotBlank() && triggerPhrase.isNotBlank()
}
