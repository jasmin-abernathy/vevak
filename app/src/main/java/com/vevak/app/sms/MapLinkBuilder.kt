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
}
