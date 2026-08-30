/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import com.vevak.app.model.MapProvider
import java.util.Locale

object MapLinkBuilder {
    fun build(provider: MapProvider, latitude: Double, longitude: Double): String {
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lon = String.format(Locale.US, "%.6f", longitude)
        return when (provider) {
            MapProvider.CoMaps -> "geo:0,0?q=$lat,$lon"
            MapProvider.OpenStreetMap -> "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"
            MapProvider.GoogleMaps -> "https://maps.google.com/?q=$lat,$lon"
        }
    }

    /**
     * Network/IP estimates are areas, not device fixes. These links deliberately centre and zoom
     * the map without dropping a precise-looking marker on the returned centroid.
     */
    fun buildApproximateZone(
        provider: MapProvider,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?
    ): String {
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lon = String.format(Locale.US, "%.6f", longitude)
        val zoom = approximateZoom(accuracyMeters)
        return when (provider) {
            MapProvider.CoMaps -> "geo:$lat,$lon?z=$zoom"
            MapProvider.OpenStreetMap -> "https://www.openstreetmap.org/#map=$zoom/$lat/$lon"
            MapProvider.GoogleMaps -> "https://www.google.com/maps/@?api=1&map_action=map&center=$lat,$lon&zoom=$zoom"
        }
    }

    internal fun approximateZoom(accuracyMeters: Float?): Int {
        val radius = accuracyMeters ?: return 9
        return when {
            radius <= 250f -> 16
            radius <= 500f -> 15
            radius <= 1_000f -> 14
            radius <= 2_000f -> 13
            radius <= 5_000f -> 12
            radius <= 10_000f -> 11
            radius <= 25_000f -> 10
            radius <= 50_000f -> 9
            else -> 8
        }
    }
}
